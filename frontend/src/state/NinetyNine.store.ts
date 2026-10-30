import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

import webSocketService from '../service/WebSocketService';
import NinetyNineSession, { type NinetyNineSessionEvent } from '../application/NinetyNineSession';
import type {
  NinetyNineAction, NinetyNineEventData, NinetyNinePlayer, NinetyNineState, NinetyNineView,
} from '../model/ninetynine/NinetyNineState';
import type { LobbyView } from '../model/lobby/LobbyView';
import type Card from '../model/Card';
import { appendEntry, toLogEntry, type NinetyNineLogEntry } from '../model/ninetynine/eventLog';
import { aceValuesFor, isSafe } from '../model/ninetynine/playable';
import { useSeatPresence, FORFEIT_NOTICE_HOLD_MS } from '../composables/useSeatPresence';
import { useSeatRoom } from '../composables/useSeatRoom';
import type { ForfeitEventData } from '../model/SeatLifecycleEvents';

export { FORFEIT_NOTICE_HOLD_MS };

/** Why the last command of this client did not go through (a key; the texts live in the locales). */
export type NinetyNineNotice = 'moveImpossible' | 'connection';

/** The last thing a command did at the table, for the live region and the seats. */
export interface NinetyNineTableEvent {
  eventType: string;
  data: NinetyNineEventData | null;
}

/** Events that are not about the table: they have their own state (presence, forfeit). */
const NOT_TABLE_EVENTS = new Set(['OPPONENT_DISCONNECTED', 'OPPONENT_RECONNECTED', 'FORFEIT']);

/** The slot my played card held in the hand before the play, else null (another seat's play, or unknown). */
function slotOfPlay(data: NinetyNineEventData | null, before: NinetyNineState | null, mySeat: number): number | null {
  if (!data?.card || !before || data.seat !== mySeat) return null;
  const slot = before.hand.findIndex(c => c.name === data.card!.name);
  return slot >= 0 ? slot : null;
}

export const useNinetyNineStore = defineStore('ninety-nine-store', () => {
  const state = ref<NinetyNineView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  // Every seat seen in this game, kept after it leaves the table (a forfeited player drops out of `players`, yet
  // "Bob forfeited" must still name him). Cleared when the room goes back to a lobby.
  const roster = ref<Record<number, NinetyNinePlayer>>({});
  // A command is on its way: from the send until the next state or refusal.
  const busy = ref(false);
  const notice = ref<NinetyNineNotice | null>(null);
  const lastEvent = ref<NinetyNineTableEvent | null>(null);
  // The moves at the table, newest first, for the pictures (flights, rings, the log line). Each is the server's
  // event plus the state that came before it. Empty after a first state: nothing is replayed.
  const events = ref<NinetyNineLogEntry[]>([]);
  let previousGame: NinetyNineState | null = null;
  let entryId = 0;

  const game = computed<NinetyNineState | null>(() =>
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

  const session = new NinetyNineSession(webSocketService, {
    onEvent(event: NinetyNineSessionEvent) { applyEvent(event); },
  });

  // The play on its way: the version it was sent from and the card. Only the answer to it frees the table.
  let pending: { version: number; cardName: string } | null = null;

  // Whether a state is the answer to the pending play: newer than the one it was sent from, and showing my card
  // played or the turn gone elsewhere. A state of someone else's doing (a forfeit, a reconnection notice) is not.
  function answersPending(next: NinetyNineState | null): boolean {
    if (!pending || !next) return true; // nothing pending, or back to a lobby
    if (next.version <= pending.version) return false;
    const mine = next.lastPlay?.seat === mySeat.value && next.lastPlay.card.name === pending.cardName;
    return mine || next.currentSeat !== mySeat.value || next.phase !== 'PLAYING';
  }

  function applyEvent(event: NinetyNineSessionEvent) {
    switch (event.type) {
      case 'state-update': {
        const next = event.state;
        const nextGame = next.started ? next : null;
        // One command sends several events, each with the state after the whole command (same version): the state
        // before the command is kept until the version moves on.
        if (!game.value || !nextGame || nextGame.version !== game.value.version) previousGame = game.value;
        state.value = next;
        if (busy.value && answersPending(nextGame)) busy.value = false;
        room.onStateUpdate(next.started);
        if (nextGame) {
          for (const p of nextGame.players) roster.value = { ...roster.value, [p.seat]: p };
        } else {
          roster.value = {};
          lastEvent.value = null;
          notice.value = null;
          events.value = [];
          previousGame = null;
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
        if (state.value?.started === true) notice.value = 'moveImpossible';
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
        } else if (event.eventType !== 'START') {
          const data = (event.eventData as NinetyNineEventData | null) ?? null;
          notice.value = null;
          lastEvent.value = event.eventType === 'ROUND_START' ? null : { eventType: event.eventType, data };
          // The state of the event already arrived (it comes first): the one before it is what the entry needs.
          const entry = toLogEntry(++entryId, event.eventType, data, previousGame);
          if (entry && event.eventType === 'PLAY') entry.handSlot = slotOfPlay(data, previousGame, mySeat.value);
          events.value = appendEntry(events.value, entry);
        }
        break;
    }
  }

  function clear() {
    state.value = null;
    busy.value = false;
    notice.value = null;
    lastEvent.value = null;
    events.value = [];
    previousGame = null;
    gameId.value = null;
    roster.value = {};
  }

  const me = computed(() => game.value?.players.find(p => p.seat === mySeat.value) ?? null);
  const isMyTurn = computed(() => game.value?.currentSeat === mySeat.value && me.value !== null);
  const can = (action: NinetyNineAction) => game.value?.availableActions.includes(action) ?? false;
  // The rules in force, for the rule chips; empty in a lobby.
  const options = computed<Record<string, string>>(() => game.value?.options ?? {});
  const hand = computed<Card[]>(() => game.value?.hand ?? []);
  const screen = computed<'connecting' | 'lobby' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (!state.value.started) return 'lobby';
    if (state.value.outcome.status === 'FINISHED') return 'finished';
    return 'playing';
  });

  // A reopened room lands back in the lobby; drop any presence from the finished game.
  watch(screen, (s) => { if (s === 'lobby') presence.reset(); });

  // Runs a command: ignored while another is in flight, busy until the answer, and a socket that is down is
  // reported rather than left pending.
  function send(command: () => void): boolean {
    if (busy.value) return false;
    notice.value = null;
    try {
      command();
    } catch {
      notice.value = 'connection';
      return false;
    }
    busy.value = true;
    return true;
  }

  /**
   * Plays a card of my hand. The server says what is playable and which Ace values are allowed; this only refuses
   * what it already said no to (so no round trip is wasted), and fills in an Ace that has a single value. An Ace
   * with two allowed values needs the player's choice.
   */
  function play(card: Card, aceValue?: number): boolean {
    const current = game.value;
    if (!current || !can('PLAY') || busy.value || !isSafe(current, card)) return false;
    const allowed = aceValuesFor(current, card);
    let value: number | null = null;
    if (allowed.length > 0) {
      value = aceValue ?? (allowed.length === 1 ? allowed[0] : null);
      if (value === null || !allowed.includes(value)) return false;
    }
    const sentFrom = { version: current.version, cardName: card.name };
    const sentOk = send(() => session.play(card, value));
    if (sentOk) pending = sentFrom;
    return sentOk;
  }

  // Leaves the lobby and forgets everything about it, so the next room starts from a clean store.
  const leave = () => room.leave(clear);

  const playAgain = () => room.playAgain(() => { presence.reset(); });

  return {
    state, game, lobby, roster, lobbyError, removedByHost, starting, gameId, mySeat, rematch, lastForfeit,
    disconnections: presence.disconnections,
    liveDisconnections: presence.liveDisconnections,
    forfeitNotice: presence.forfeitNotice,
    busy, notice, lastEvent, events,
    me, isMyTurn, can, options, hand, screen,
    applyEvent, play,
    create: (name?: string, privateGame = false) => session.create(name, privateGame),
    join: (id: string, name?: string) => session.join(id, name),
    restore: (id: string, seat: number, token: string) => session.restore(id, seat, token),
    hydrate: () => session.hydrate(),
    startGame: room.startGame,
    addBot: () => requestLobbyChange(() => session.addBot()),
    removeBot: (seat: number) => requestLobbyChange(() => session.removeBot(seat)),
    setOption: (key: string, value: string) => requestLobbyChange(() => session.setOption(key, value)),
    setSeatOption: (seat: number, key: string, value: string) => requestLobbyChange(() => session.setSeatOption(seat, key, value)),
    removeSeat: (seat: number) => requestLobbyChange(() => session.removeSeat(seat)),
    rename: (name: string) => requestLobbyChange(() => session.rename(name)),
    setPrivate: (privateGame: boolean) => requestLobbyChange(() => session.setPrivate(privateGame)),
    leave,
    forfeit: () => session.forfeit(),
    playAgain,
  };
});
