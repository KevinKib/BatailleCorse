import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

import webSocketService from '../service/WebSocketService';
import KoboSession, { type KoboSessionEvent } from '../application/KoboSession';
import type { KoboAction, KoboPlayer, KoboState, KoboView } from '../model/kobo/KoboState';
import type { KoboEventData, KoboLogEntry } from '../model/kobo/KoboEvents';
import type { LobbyView } from '../model/lobby/LobbyView';
import { koboInteraction, NOTHING, type KoboPending, type KoboSlotCommand } from '../model/kobo/interaction';
import { reconcilePending } from '../model/kobo/pending';
import { displayedOf, liveHold, mergeHold, NO_HOLD, type DisplayedHold } from '../model/kobo/displayed';
import { revealToken, visibleFaces } from '../model/kobo/faces';
import { useSeatPresence, FORFEIT_NOTICE_HOLD_MS } from '../composables/useSeatPresence';
import { useSeatRoom } from '../composables/useSeatRoom';
import type { ForfeitEventData } from '../model/SeatLifecycleEvents';

export { FORFEIT_NOTICE_HOLD_MS };

/** How many events the live log keeps, newest first. */
export const KOBO_LOG_SIZE = 20;

/** Why the last command of this client did not go through (a key; the texts live in the locales). */
export type KoboNotice = 'matchTooLate' | 'moveImpossible' | 'connection';

/** Events that are not about the table: they have their own state (presence, forfeit) and never enter the log. */
const NOT_TABLE_EVENTS = new Set(['OPPONENT_DISCONNECTED', 'OPPONENT_RECONNECTED', 'FORFEIT']);
const NO_PENDING: KoboPending = { kind: 'none' };
/** After a command of mine, a click on a card is not a one-click match for this long (a double tap, a late tap). */
export const KOBO_SETTLE_MS = 500;

export const useKoboStore = defineStore('kobo-store', () => {
  const state = ref<KoboView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  // Every seat seen in this game, kept after it leaves the table (a forfeited player drops out of `players`,
  // yet "Bob forfeited" must still name him). Cleared when the room goes back to a lobby.
  const roster = ref<Record<number, KoboPlayer>>({});
  // The local selection of a multi-click action (see `koboInteraction`).
  const pending = ref<KoboPending>(NO_PENDING);
  // A turn command is on its way: from the send until the next state or refusal. A match is exempt (a race).
  const busy = ref(false);
  const events = ref<KoboLogEntry[]>([]);
  const notice = ref<KoboNotice | null>(null);
  // The peek the player chose to turn face down ("Hide"): a display choice only, forgotten with the peek.
  const hiddenPeek = ref<string | null>(null);
  // The state version whose own event must keep the "too late" notice raised by that same state.
  let staleNoticeVersion: number | null = null;

  const game = computed<KoboState | null>(() =>
    state.value && state.value.started ? state.value : null);
  const lobby = computed<LobbyView | null>(() =>
    state.value && !state.value.started ? state.value : null);

  // Lobby error, Start, rematch, removed-by-host and last forfeit are the shared part of every seat game.
  const room = useSeatRoom({
    startGame: () => session.startGame(),
    leave: () => session.leave(),
    playAgain: (name) => session.playAgain(name),
  });
  const { lobbyError, starting, rematch, removedByHost, lastForfeit, requestLobbyChange } = room;

  const presence = useSeatPresence({
    presentSeats: () => (game.value?.players ?? []).map(p => p.seat),
  });

  const session = new KoboSession(webSocketService, {
    onEvent(event: KoboSessionEvent) { applyEvent(event); },
  });

  function applyEvent(event: KoboSessionEvent) {
    switch (event.type) {
      case 'state-update': {
        const next = event.state;
        const nextGame = next.started ? next : null;
        const reconciled = reconcilePending(pending.value, game.value, nextGame, mySeat.value);
        // A card picked for a match changed under my hand (somebody moved first): say so, the prompt just vanished.
        if (pending.value.kind === 'match-give' && reconciled.kind === 'none' && game.value && nextGame
          && nextGame.round === game.value.round && nextGame.availableActions.includes('MATCH_DISCARD')) {
          notice.value = 'matchTooLate';
          staleNoticeVersion = nextGame.version;
        }
        pending.value = reconciled;
        if (nextGame && game.value && nextGame.round !== game.value.round) events.value = [];
        state.value = next;
        if (!nextGame?.reveal) hiddenPeek.value = null;
        busy.value = false;
        room.onStateUpdate(next.started);
        if (nextGame) {
          for (const p of nextGame.players) roster.value = { ...roster.value, [p.seat]: p };
        } else {
          roster.value = {};
          events.value = [];
          notice.value = null;
        }
        break;
      }
      case 'game-id-change':
        if (gameId.value !== event.gameId) roster.value = {};
        gameId.value = event.gameId;
        break;
      case 'error':
        busy.value = false;
        room.onError(state.value?.started === true, event.message);
        if (state.value?.started === true) notice.value = event.eventType === 'MATCH' ? 'matchTooLate' : 'moveImpossible';
        break;
      case 'removed':
        clear();
        room.onRemoved();
        break;
      case 'seat-change': mySeat.value = event.seat; break;
      case 'event':
        if (event.eventType === 'FORFEIT') room.onForfeit(event.eventData as ForfeitEventData);
        if (NOT_TABLE_EVENTS.has(event.eventType)) {
          presence.applyPresenceEvent(event.eventType, event.eventData);
        } else {
          // The event that comes with the state that dropped my selection must not wipe the notice about it.
          if (!(notice.value === 'matchTooLate' && game.value?.version === staleNoticeVersion)) notice.value = null;
          logEvent(event.eventType, event.eventData as KoboEventData | null);
        }
        break;
    }
  }

  function logEvent(eventType: string, data: KoboEventData | null) {
    if (eventType === 'ROUND_START' || eventType === 'START') {
      events.value = [];
      return;
    }
    events.value = [{ eventType, data: data ?? null }, ...events.value].slice(0, KOBO_LOG_SIZE);
  }

  function clear() {
    state.value = null;
    pending.value = NO_PENDING;
    busy.value = false;
    clearTimeout(settleTimer);
    settling.value = false;
    clearTimeout(holdTimer);
    held.value = NO_HOLD;
    events.value = [];
    notice.value = null;
    hiddenPeek.value = null;
    gameId.value = null;
    roster.value = {};
  }

  const me = computed(() => game.value?.players.find(p => p.seat === mySeat.value) ?? null);
  const isMyTurn = computed(() => game.value?.currentSeat === mySeat.value && me.value !== null);
  const can = (action: KoboAction) => game.value?.availableActions.includes(action) ?? false;
  // True for a moment after each command I send: the one-click match is held back so that the second tap of a
  // double tap, landing on the table the first one just changed, cannot cost a penalty card.
  const settling = ref(false);
  let settleTimer: ReturnType<typeof setTimeout> | undefined;
  function settle() {
    settling.value = true;
    clearTimeout(settleTimer);
    settleTimer = setTimeout(() => { settling.value = false; }, KOBO_SETTLE_MS);
  }
  // What the table still shows while the flights of an event play (see `DisplayedHold`): a match is built on it.
  const held = ref<DisplayedHold>(NO_HOLD);
  let holdTimer: ReturnType<typeof setTimeout> | undefined;
  /** Keeps `hold` on screen for the match commands until its end time (a `Date.now()` stamp), however the flights end. */
  function holdDisplayed(hold: DisplayedHold) {
    held.value = mergeHold(liveHold(held.value, Date.now()), hold);
    scheduleRelease();
  }
  // Wakes up at the next end time to drop what has landed (the interaction must recompute without any other change).
  function scheduleRelease() {
    clearTimeout(holdTimer);
    const next = Math.min(...Object.values(held.value.slots).map(h => h.until), held.value.discard?.until ?? Infinity);
    if (!Number.isFinite(next)) return;
    holdTimer = setTimeout(() => {
      held.value = liveHold(held.value, Date.now());
      scheduleRelease();
    }, Math.max(0, next - Date.now()));
  }
  const interaction = computed(() =>
    game.value
      ? koboInteraction(game.value, mySeat.value, pending.value, {
        directMatch: !settling.value, displayed: displayedOf(held.value),
      })
      : NOTHING);
  // Every card the viewer may see, by `slotKey` (see `visibleFaces`).
  const faces = computed(() =>
    game.value ? visibleFaces(game.value, mySeat.value, hiddenPeek.value) : {});
  const canHidePeek = computed(() =>
    !!game.value?.reveal && revealToken(game.value) !== hiddenPeek.value);
  const screen = computed<'connecting' | 'lobby' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (!state.value.started) return 'lobby';
    if (state.value.outcome.status === 'FINISHED') return 'finished';
    return 'playing';
  });

  // A reopened room lands back in the lobby; drop any presence from the finished game.
  watch(screen, (s) => { if (s === 'lobby') presence.reset(); });

  // Runs a turn command: ignored while another is in flight (unless `race`), busy until the answer, and a
  // socket that is down is reported rather than left pending.
  function send(command: () => void, race = false): boolean {
    if (busy.value && !race) return false;
    notice.value = null;
    try {
      command();
    } catch {
      notice.value = 'connection';
      return false;
    }
    if (!race) busy.value = true;
    settle();
    return true;
  }

  function sendSlotCommand(command: KoboSlotCommand): boolean {
    switch (command.type) {
      case 'swapDrawn': return send(() => session.swapDrawn(command.slot));
      case 'peek': return send(() => session.peek(command.seat, command.slot));
      case 'kingSwap': return send(() => session.kingSwap(command.slot));
      case 'blindSwap': return send(() => session.blindSwap(command.ownSlot, command.seat, command.slot));
      case 'match': {
        const { type: _type, ...request } = command;
        return send(() => session.match(request), true);
      }
    }
  }

  /** The one entry point of a click on a card: sends the command it means, or moves the selection forward. */
  function pickSlot(seat: number, slot: number) {
    const intent = interaction.value.intents[`${seat}:${slot}`];
    if (!intent) return;
    if (intent.kind === 'pend') {
      pending.value = intent.pending;
      return;
    }
    if (intent.command.type !== 'match' && busy.value) return;
    if (sendSlotCommand(intent.command)) pending.value = NO_PENDING;
  }

  /** A click on a pile: the draw pile draws, the discard pile discards the card I hold (the bar buttons stay). */
  function pickPile(pile: 'draw' | 'discard') {
    if (!interaction.value.piles[pile]) return;
    send(pile === 'draw' ? () => session.draw() : () => session.discardDrawn());
  }

  /** Arms (or disarms) the matching discard; only while the server lists it. */
  function armMatch() {
    if (pending.value.kind !== 'none') { pending.value = NO_PENDING; return; }
    if (can('MATCH_DISCARD')) pending.value = { kind: 'match-armed' };
  }

  function cancelPending() { pending.value = NO_PENDING; }

  /** Turns the peeked card face down at once, without waiting for the server to drop it. */
  function hidePeek() {
    if (game.value) hiddenPeek.value = revealToken(game.value);
  }

  // Leaves the lobby and forgets everything about it, so the next room starts from a clean store.
  const leave = () => room.leave(clear);

  const playAgain = () => room.playAgain(() => { presence.reset(); pending.value = NO_PENDING; });

  return {
    state, game, lobby, roster, lobbyError, removedByHost, starting, gameId, mySeat, rematch, lastForfeit,
    disconnections: presence.disconnections,
    liveDisconnections: presence.liveDisconnections,
    forfeitNotice: presence.forfeitNotice,
    pending, busy, events, notice,
    me, isMyTurn, can, interaction, screen, faces, canHidePeek,
    applyEvent, holdDisplayed, armMatch, cancelPending, pickSlot, pickPile, hidePeek,
    create: (name?: string, privateGame = false) => session.create(name, privateGame),
    join: (id: string, name?: string) => session.join(id, name),
    restore: (id: string, seat: number, token: string) => session.restore(id, seat, token),
    hydrate: () => session.hydrate(),
    startGame: room.startGame,
    addBot: () => requestLobbyChange(() => session.addBot()),
    removeBot: (seat: number) => requestLobbyChange(() => session.removeBot(seat)),
    setSeatOption: (seat: number, key: string, value: string) => requestLobbyChange(() => session.setSeatOption(seat, key, value)),
    removeSeat: (seat: number) => requestLobbyChange(() => session.removeSeat(seat)),
    rename: (name: string) => requestLobbyChange(() => session.rename(name)),
    leave,
    ready: () => send(() => session.ready()),
    draw: () => send(() => session.draw()),
    swapDrawn: (slot: number) => send(() => session.swapDrawn(slot)),
    discardDrawn: () => send(() => session.discardDrawn()),
    skipPower: () => send(() => session.skipPower()),
    peek: (seat: number, slot: number) => send(() => session.peek(seat, slot)),
    blindSwap: (ownSlot: number, seat: number, slot: number) => send(() => session.blindSwap(ownSlot, seat, slot)),
    kingSwap: (slot: number) => send(() => session.kingSwap(slot)),
    endTurn: () => send(() => session.endTurn()),
    announce: () => send(() => session.announce()),
    giveTen: (seat: number) => send(() => session.giveTen(seat)),
    forfeit: () => session.forfeit(),
    playAgain,
  };
});
