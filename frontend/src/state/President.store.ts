import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

import webSocketService from '../service/WebSocketService';
import PresidentSession, { type PresidentSessionEvent } from '../application/PresidentSession';
import type {
  PresidentAction, PresidentPattern, PresidentPlayer, PresidentState, PresidentView,
} from '../model/president/PresidentState';
import type { PresidentEventData } from '../model/president/PresidentEvents';
import type { LobbyView } from '../model/lobby/LobbyView';
import type Card from '../model/Card';
import {
  analyseSelection, completingCards, type SelectionAnalysis, type SelectionRefusal,
} from '../model/president/selection';
import { useSeatPresence, FORFEIT_NOTICE_HOLD_MS } from '../composables/useSeatPresence';
import { useSeatRoom } from '../composables/useSeatRoom';
import type { ForfeitEventData } from '../model/SeatLifecycleEvents';

export { FORFEIT_NOTICE_HOLD_MS };

/** Why the last command of this client did not go through (a key; the texts live in the locales). */
export type PresidentNotice = 'moveImpossible' | 'connection';

/** The last thing a command did at the table, for the live region and the seats. */
export interface PresidentTableEvent {
  eventType: string;
  data: PresidentEventData | null;
}

/** Events that are not about the table: they have their own state (presence, forfeit). */
const NOT_TABLE_EVENTS = new Set(['OPPONENT_DISCONNECTED', 'OPPONENT_RECONNECTED', 'FORFEIT']);

/**
 * Identifies the trick a selection was made in: the round, the phase and who opened it with which cards. Plays
 * added to the same trick keep it; a new trick, a new round or a new phase change it.
 */
function trickKey(game: PresidentState | null): string {
  if (!game) return '';
  const first = game.trick.plays[0];
  return [game.round, game.phase, game.trick.leader ?? '', first?.seat ?? '', first?.cards.map(c => c.name).join('+') ?? ''].join('|');
}

export const usePresidentStore = defineStore('president-store', () => {
  const state = ref<PresidentView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  // Every seat seen in this game, kept after it leaves the table (a forfeited player drops out of `players`, yet
  // "Bob forfeited" must still name him). Cleared when the room goes back to a lobby.
  const roster = ref<Record<number, PresidentPlayer>>({});
  // The names of the cards the player tapped. A pure UI state: the server judges the play.
  const selectedNames = ref<string[]>([]);
  // A command is on its way: from the send until the next state or refusal.
  const busy = ref(false);
  const notice = ref<PresidentNotice | null>(null);
  const lastEvent = ref<PresidentTableEvent | null>(null);

  const game = computed<PresidentState | null>(() =>
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

  const session = new PresidentSession(webSocketService, {
    onEvent(event: PresidentSessionEvent) { applyEvent(event); },
  });

  function applyEvent(event: PresidentSessionEvent) {
    switch (event.type) {
      case 'state-update': {
        const next = event.state;
        const nextGame = next.started ? next : null;
        if (!nextGame || trickKey(nextGame) !== trickKey(game.value)) {
          selectedNames.value = [];
        } else {
          // The cards that left the hand (played, given) leave the selection with it.
          const held = new Set(nextGame.hand.map(c => c.name));
          selectedNames.value = selectedNames.value.filter(n => held.has(n));
        }
        state.value = next;
        busy.value = false;
        room.onStateUpdate(next.started);
        if (nextGame) {
          for (const p of nextGame.players) roster.value = { ...roster.value, [p.seat]: p };
        } else {
          roster.value = {};
          lastEvent.value = null;
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
        if (state.value?.started === true) {
          // A refused play: the selection that led to it is stale, the state it came with is the truth.
          selectedNames.value = [];
          notice.value = 'moveImpossible';
        }
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
          notice.value = null;
          lastEvent.value = event.eventType === 'ROUND_START' || event.eventType === 'START'
            ? null
            : { eventType: event.eventType, data: (event.eventData as PresidentEventData | null) ?? null };
        }
        break;
    }
  }

  function clear() {
    state.value = null;
    selectedNames.value = [];
    busy.value = false;
    notice.value = null;
    lastEvent.value = null;
    gameId.value = null;
    roster.value = {};
  }

  const me = computed(() => game.value?.players.find(p => p.seat === mySeat.value) ?? null);
  const isMyTurn = computed(() => game.value?.currentSeat === mySeat.value && me.value !== null);
  const can = (action: PresidentAction) => game.value?.availableActions.includes(action) ?? false;
  // The rules in force, for the rule chips; empty in a lobby.
  const options = computed<Record<string, string>>(() => game.value?.options ?? {});
  const hand = computed<Card[]>(() => game.value?.hand ?? []);
  const screen = computed<'connecting' | 'lobby' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (!state.value.started) return 'lobby';
    if (state.value.outcome.status === 'FINISHED') return 'finished';
    return 'playing';
  });

  // The selection, in hand order.
  const selectedCards = computed<Card[]>(() => hand.value.filter(c => selectedNames.value.includes(c.name)));
  const analysis = computed<SelectionAnalysis>(() =>
    game.value
      ? analyseSelection(game.value, selectedCards.value)
      : { pattern: null, legal: false, reason: { code: 'cannotPlay' } });
  const selectedPattern = computed<PresidentPattern | null>(() => analysis.value.pattern);
  const canPlay = computed(() => analysis.value.legal && !busy.value);
  // The hint line under the Play button: why the selection cannot be played, null when it can.
  const playHint = computed<SelectionRefusal | null>(() => analysis.value.reason);
  const completions = computed<Card[]>(() =>
    game.value ? completingCards(hand.value, selectedCards.value, game.value.legalPlays) : []);
  // The exchange step: exactly the cards owed.
  const canGiveBack = computed(() =>
    can('GIVE_BACK') && !busy.value && (game.value?.toGiveBack ?? 0) > 0
    && selectedCards.value.length === game.value?.toGiveBack);

  // A reopened room lands back in the lobby; drop any presence from the finished game.
  watch(screen, (s) => { if (s === 'lobby') presence.reset(); });

  // Runs a command: ignored while another is in flight, busy until the answer, and a socket that is down is
  // reported rather than left pending (the selection is kept to retry).
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

  /** A tap on a card of my hand. Only while the server lists a command that takes cards. */
  function toggleCard(name: string) {
    if (!can('PLAY') && !can('GIVE_BACK')) return;
    if (!hand.value.some(c => c.name === name)) return;
    selectedNames.value = selectedNames.value.includes(name)
      ? selectedNames.value.filter(n => n !== name)
      : [...selectedNames.value, name];
  }

  function clearSelection() { selectedNames.value = []; }

  /** Plays the selection when it is a legal combination; the selection goes with the next state. */
  function play(): boolean {
    if (!canPlay.value) return false;
    const cards = selectedCards.value;
    return send(() => session.play(cards));
  }

  function pass(): boolean {
    return can('PASS') ? send(() => session.pass()) : false;
  }

  function giveBack(): boolean {
    if (!canGiveBack.value) return false;
    const cards = selectedCards.value;
    return send(() => session.giveBack(cards));
  }

  function ready(): boolean {
    return can('READY') ? send(() => session.ready()) : false;
  }

  // Leaves the lobby and forgets everything about it, so the next room starts from a clean store.
  const leave = () => room.leave(clear);

  const playAgain = () => room.playAgain(() => { presence.reset(); selectedNames.value = []; });

  return {
    state, game, lobby, roster, lobbyError, removedByHost, starting, gameId, mySeat, rematch, lastForfeit,
    disconnections: presence.disconnections,
    liveDisconnections: presence.liveDisconnections,
    forfeitNotice: presence.forfeitNotice,
    busy, notice, lastEvent,
    me, isMyTurn, can, options, hand, screen,
    selectedCards, selectedPattern, canPlay, playHint, completions, canGiveBack,
    applyEvent, toggleCard, clearSelection, play, pass, giveBack, ready,
    create: (name?: string) => session.create(name),
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
    leave,
    forfeit: () => session.forfeit(),
    playAgain,
  };
});
