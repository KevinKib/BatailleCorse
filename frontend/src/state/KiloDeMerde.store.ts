import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

import webSocketService from '../service/WebSocketService';
import KiloDeMerdeSession, { type KiloDeMerdeSessionEvent } from '../application/KiloDeMerdeSession';
import type {
  KiloDeMerdeAction, KiloDeMerdeEventData, KiloDeMerdePlayer, KiloDeMerdeState, KiloDeMerdeView,
} from '../model/kilodemerde/KiloDeMerdeState';
import type { LobbyView } from '../model/lobby/LobbyView';
import type Card from '../model/Card';
import type { KiloDeMerdeTableEvent } from '../model/kilodemerde/motion';
import { useSeatPresence, FORFEIT_NOTICE_HOLD_MS } from '../composables/useSeatPresence';
import { useSeatRoom } from '../composables/useSeatRoom';
import type { ForfeitEventData } from '../model/SeatLifecycleEvents';

export { FORFEIT_NOTICE_HOLD_MS };

/** Why the last command of this client did not go through (a key; the texts live in the locales). */
export type KiloDeMerdeNotice = 'moveImpossible' | 'connection';

/** How many table events the store keeps (newest first). */
export const MAX_EVENTS = 20;

/** Events that are not about the table: they have their own state (presence, forfeit). */
const NOT_TABLE_EVENTS = new Set(['OPPONENT_DISCONNECTED', 'OPPONENT_RECONNECTED', 'FORFEIT']);

export const useKiloDeMerdeStore = defineStore('kilo-de-merde-store', () => {
  const state = ref<KiloDeMerdeView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  // Every seat seen in this game, kept after it leaves the table (a forfeited player drops out of the active
  // seats, yet "Bob forfeited" must still name him). Cleared when the room goes back to a lobby.
  const roster = ref<Record<number, KiloDeMerdePlayer>>({});
  // A command is on its way: from the send until the state that answers it, or a refusal.
  const busy = ref(false);
  const notice = ref<KiloDeMerdeNotice | null>(null);
  const lastEvent = ref<KiloDeMerdeTableEvent | null>(null);
  // The events at the table, newest first, for the pictures (flights) and the live regions. Each carries the
  // table it happened at. Empty after a first state: nothing is replayed.
  const events = ref<KiloDeMerdeTableEvent[]>([]);
  let entryId = 0;

  const game = computed<KiloDeMerdeState | null>(() =>
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
    presentSeats: () => (game.value?.players ?? []).filter(p => p.active).map(p => p.seat),
  });

  const session = new KiloDeMerdeSession(webSocketService, {
    onEvent(event: KiloDeMerdeSessionEvent) { applyEvent(event); },
  });

  // The command on its way: whether a state is its answer. A state of someone else's doing (another seat's choice or
  // slap, a reconnection notice) is not, so the table stays locked until the command shows or the game moved on.
  let answers: ((next: KiloDeMerdeState | null) => boolean) | null = null;

  function applyEvent(event: KiloDeMerdeSessionEvent) {
    switch (event.type) {
      case 'state-update': {
        const next = event.state;
        const nextGame = next.started ? next : null;
        state.value = next;
        if (busy.value && (!answers || answers(nextGame))) { busy.value = false; answers = null; }
        room.onStateUpdate(next.started);
        if (nextGame) {
          for (const p of nextGame.players) roster.value = { ...roster.value, [p.seat]: p };
        } else {
          roster.value = {};
          lastEvent.value = null;
          notice.value = null;
          events.value = [];
        }
        break;
      }
      case 'game-id-change':
        if (gameId.value !== event.gameId) roster.value = {};
        gameId.value = event.gameId;
        break;
      case 'error':
        busy.value = false;
        answers = null;
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
          const entry: KiloDeMerdeTableEvent = {
            id: ++entryId,
            eventType: event.eventType,
            data: (event.eventData as KiloDeMerdeEventData | null) ?? null,
            // The state of the event already arrived (it comes first): the table around the event is that state.
            activeSeats: (game.value?.players ?? []).filter(p => p.active).map(p => p.seat),
            received: game.value?.lastReceived ?? null,
          };
          notice.value = null;
          lastEvent.value = entry;
          events.value = [entry, ...events.value].slice(0, MAX_EVENTS);
        }
        break;
    }
  }

  function clear() {
    state.value = null;
    busy.value = false;
    answers = null;
    notice.value = null;
    lastEvent.value = null;
    events.value = [];
    gameId.value = null;
    roster.value = {};
  }

  const me = computed(() => game.value?.players.find(p => p.seat === mySeat.value) ?? null);
  const can = (action: KiloDeMerdeAction) => game.value?.availableActions.includes(action) ?? false;
  // The rules in force, for the rule chips; empty in a lobby.
  const options = computed<Record<string, string>>(() => game.value?.options ?? {});
  const hand = computed<Card[]>(() => game.value?.hand ?? []);
  const screen = computed<'connecting' | 'lobby' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (!state.value.started) return 'lobby';
    if (state.value.phase === 'FINISHED') return 'finished';
    return 'playing';
  });

  // A reopened room lands back in the lobby; drop any presence from the finished game.
  watch(screen, (s) => { if (s === 'lobby') presence.reset(); });

  // Runs a command: ignored while another is in flight, busy until the answer, and a socket that is down is
  // reported rather than left pending.
  function send(command: () => void, answeredBy: (next: KiloDeMerdeState | null) => boolean): boolean {
    if (busy.value) return false;
    notice.value = null;
    try {
      command();
    } catch {
      notice.value = 'connection';
      return false;
    }
    answers = answeredBy;
    busy.value = true;
    return true;
  }

  /**
   * Chooses the card to pass. The server says what is possible (`availableActions`, my hand, my choice); this only
   * refuses what it already said no to, so no round trip is wasted. The command is answered by a state that shows
   * my choice, or by one where the step, the round or the phase moved on.
   */
  function pass(card: Card): boolean {
    const current = game.value;
    if (!current || busy.value || !can('PASS') || current.myChoice !== null) return false;
    if (!current.hand.some(c => c.name === card.name)) return false;
    const { step, round, phase } = current;
    return send(() => session.pass(card), next =>
      !next || next.myChoice !== null || next.step !== step || next.round !== round || next.phase !== phase);
  }

  /**
   * Slaps the pile. Answered by a state that shows my slap rank, or where the phase or the round moved on (the slap
   * opened a race, or a false slap ended the round).
   */
  function slap(): boolean {
    const current = game.value;
    if (!current || busy.value || !can('SLAP')) return false;
    const { round, phase } = current;
    const seat = mySeat.value;
    return send(() => session.slap(), next =>
      !next || next.phase !== phase || next.round !== round
        || (next.players.find(p => p.seat === seat)?.slapRank ?? null) !== null);
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
    me, can, options, hand, screen,
    applyEvent, pass, slap,
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
