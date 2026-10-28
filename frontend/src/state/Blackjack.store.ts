import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

import webSocketService from '../service/WebSocketService';
import BlackjackSession, { type BlackjackSessionEvent } from '../application/BlackjackSession';
import type {
  BlackjackAction, BlackjackEventData, BlackjackSeat, BlackjackState, BlackjackView,
} from '../model/blackjack/BlackjackState';
import type { LobbyView } from '../model/lobby/LobbyView';
import type { BlackjackTableEvent } from '../model/blackjack/motion';
import { isBetAllowed } from '../model/blackjack/betting';
import { opponentsOf } from '../model/blackjack/seatOrder';
import { useSeatPresence, FORFEIT_NOTICE_HOLD_MS } from '../composables/useSeatPresence';
import { useSeatRoom } from '../composables/useSeatRoom';
import type { ForfeitEventData } from '../model/SeatLifecycleEvents';

export { FORFEIT_NOTICE_HOLD_MS };

/** Why the last command of this client did not go through (a key; the texts live in the locales). */
export type BlackjackNotice = 'moveImpossible' | 'connection';

/** How many table events the store keeps (newest first). */
export const MAX_EVENTS = 20;

/** Events that are not about the table: they have their own state (presence, forfeit). */
const NOT_TABLE_EVENTS = new Set(['OPPONENT_DISCONNECTED', 'OPPONENT_RECONNECTED', 'FORFEIT']);

export const useBlackjackStore = defineStore('blackjack-store', () => {
  const state = ref<BlackjackView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  // Every seat seen in this game, kept after it leaves the table (a forfeited player drops out of the seats, yet
  // "Bob forfeited" must still name him). Cleared when the room goes back to a lobby.
  const roster = ref<Record<number, BlackjackSeat>>({});
  // A command is on its way: from the send until the state that answers it, or a refusal.
  const busy = ref(false);
  const notice = ref<BlackjackNotice | null>(null);
  const lastEvent = ref<BlackjackTableEvent | null>(null);
  // The events at the table, newest first, for the pictures (flights) and the live region. Empty after a first
  // state: nothing is replayed.
  const events = ref<BlackjackTableEvent[]>([]);
  let entryId = 0;

  const game = computed<BlackjackState | null>(() =>
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
    presentSeats: () => (game.value?.players ?? []).filter(p => !p.forfeited).map(p => p.seat),
  });

  const session = new BlackjackSession(webSocketService, {
    onEvent(event: BlackjackSessionEvent) { applyEvent(event); },
  });

  // The command on its way: whether a state is its answer. A state of someone else's doing (another seat's bet, a
  // reconnection notice) is not, so the table stays locked until the command shows or the game moved on.
  let answers: ((next: BlackjackState | null) => boolean) | null = null;

  function applyEvent(event: BlackjackSessionEvent) {
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
          const entry: BlackjackTableEvent = {
            id: ++entryId,
            eventType: event.eventType,
            data: (event.eventData as BlackjackEventData | null) ?? null,
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
  const opponents = computed<BlackjackSeat[]>(() => opponentsOf(game.value?.players ?? [], mySeat.value));
  const solo = computed(() => game.value?.solo ?? false);
  const can = (action: BlackjackAction) => game.value?.availableActions.includes(action) ?? false;
  // The rules in force, for the rule chips; empty in a lobby.
  const options = computed<Record<string, string>>(() => {
    const o = game.value?.options;
    return o ? { hands: o.hands, maxBet: o.maxBet } : {};
  });
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
  function send(command: () => void, answeredBy: (next: BlackjackState | null) => boolean): boolean {
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
   * Places my bet. The server says what is possible (`availableActions`, `betLimits`); this only refuses what it
   * already said no to. Answered by a state that shows my bet, or where the hand or the phase moved on (mine was
   * the last bet awaited, so the deal followed).
   */
  function bet(amount: number): boolean {
    const current = game.value;
    if (!current || busy.value || !can('BET') || !isBetAllowed(amount, current.betLimits)) return false;
    if (me.value?.betPlaced) return false;
    const { phase, handNumber } = current;
    const seat = mySeat.value;
    return send(() => session.bet(amount), next =>
      !next || next.phase !== phase || next.handNumber !== handNumber
        || (next.players.find(p => p.seat === seat)?.betPlaced ?? false));
  }

  // A play action: only when the server offered it, answered by the next version of the game (nobody else acts
  // during my turn).
  function act(action: BlackjackAction, command: () => void): boolean {
    const current = game.value;
    if (!current || busy.value || !can(action)) return false;
    const { version } = current;
    return send(command, next => !next || next.version > version);
  }

  const hit = () => act('HIT', () => session.hit());
  const stand = () => act('STAND', () => session.stand());
  const doubleDown = () => act('DOUBLE', () => session.doubleDown());
  const split = () => act('SPLIT', () => session.split());

  // Leaves the lobby and forgets everything about it, so the next room starts from a clean store.
  const leave = () => room.leave(clear);

  const playAgain = () => room.playAgain(() => { presence.reset(); });

  return {
    state, game, lobby, roster, lobbyError, removedByHost, starting, gameId, mySeat, rematch, lastForfeit,
    disconnections: presence.disconnections,
    liveDisconnections: presence.liveDisconnections,
    forfeitNotice: presence.forfeitNotice,
    busy, notice, lastEvent, events,
    me, opponents, solo, can, options, screen,
    applyEvent, bet, hit, stand, doubleDown, split,
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
