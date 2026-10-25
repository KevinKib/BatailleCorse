import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

import webSocketService from '../service/WebSocketService';
import MangolaSession, { type MangolaSessionEvent } from '../application/mangola/MangolaSession';
import type { MangolaPhase, MangolaSide, MangolaState, MangolaView } from '../model/mangola/MangolaState';
import type { LobbyView } from '../model/lobby/LobbyView';
import { DEFAULT_MANGOLA_DIRECTION, directionOf } from '../model/mangola/direction';
import { arrowOf as arrowOfPit, sideOfSeat, viewerRows, type Arrow, type MangolaCell } from '../model/mangola/board';
import {
  addSeed, checkerboardLayout, clearLayout, draftOf, randomLayout, removeSeed, seedsLeft as seedsLeftOf, twoPerPit,
  type Draft,
} from '../model/mangola/draft';
import { lastMoveMarks, type LastMoveMarks } from '../model/mangola/trace';
import { useSeatPresence, FORFEIT_NOTICE_HOLD_MS } from '../composables/useSeatPresence';
import { useSeatRoom } from '../composables/useSeatRoom';
import type { ForfeitEventData } from '../model/SeatLifecycleEvents';

export { FORFEIT_NOTICE_HOLD_MS };

/** The editor waits this long after the last change before sending the layout, so a run of taps is one command. */
export const ARRANGE_DEBOUNCE_MS = 300;

export type MangolaPreset = 'checkerboard' | 'two-per-pit' | 'random' | 'clear';

const NO_MARKS: LastMoveMarks = { start: null, captured: [] };

/** The refusal of a confirmation that leaves the player who starts without a move. */
export const STUCK_START_KEY = 'mangola.errors.stuckStart';

export const useMangolaStore = defineStore('mangola-store', () => {
  const state = ref<MangolaView | null>(null);
  const gameId = ref<string | null>(null);
  // The computer seats, read from the lobby: the game view does not say which seat is one, so a player who
  // reloads in the middle of a game has none (the screen then just says it is waiting).
  const botSeats = ref<number[]>([]);
  const mySeat = ref<number>(0);

  // The layout being edited: 16 counts in the order of my loop. It follows the server's draft (so a reload or a
  // reconnection restores it) until I change it; from then on it is mine until it is sent (`dirty`).
  const draft = ref<Draft>(clearLayout());
  const dirty = ref(false);
  let arrangeTimer: ReturnType<typeof setTimeout> | null = null;
  // The key of the last refused confirmation, until I change the layout, confirm again or the phase moves on.
  const placementError = ref<string | null>(null);

  const game = computed<MangolaState | null>(() =>
    state.value && state.value.started ? state.value : null);
  const lobby = computed<LobbyView | null>(() =>
    state.value && !state.value.started ? state.value : null);

  const room = useSeatRoom({
    startGame: () => session.startGame(),
    leave: () => session.leave(),
    playAgain: (name) => session.playAgain(name),
  });
  const { lobbyError, starting, rematch, removedByHost, lastForfeit, requestLobbyChange } = room;

  const presence = useSeatPresence({
    presentSeats: () => (game.value?.players ?? []).map(p => p.seat),
  });

  const session = new MangolaSession(webSocketService, {
    onEvent(event: MangolaSessionEvent) { applyEvent(event); },
  });

  function cancelArrange() {
    if (arrangeTimer !== null) clearTimeout(arrangeTimer);
    arrangeTimer = null;
  }

  function forgetDraft() {
    cancelArrange();
    dirty.value = false;
    draft.value = clearLayout();
    placementError.value = null;
  }

  function applyEvent(event: MangolaSessionEvent) {
    switch (event.type) {
      case 'state-update':
        state.value = event.state;
        if (!event.state.started) botSeats.value = event.state.players.filter(p => p.bot === true).map(p => p.seat);
        room.onStateUpdate(event.state.started);
        if (event.state.started && event.state.phase === 'PLACEMENT') {
          // My unsent edit stands over what the server says; otherwise the server's draft is the truth.
          if (!dirty.value) draft.value = draftOf(event.state, mySeat.value);
        } else {
          forgetDraft();
        }
        break;
      case 'game-id-change': gameId.value = event.gameId; break;
      case 'error':
        room.onError(state.value?.started === true, event.message);
        // A refused confirmation stays in the editor, which says why (the key is mapped to a text by the screen).
        if (event.message === STUCK_START_KEY && phase.value === 'placement') placementError.value = event.message;
        break;
      case 'removed':
        state.value = null;
        botSeats.value = [];
        forgetDraft();
        gameId.value = null;
        room.onRemoved();
        break;
      case 'seat-change': mySeat.value = event.seat; break;
      case 'event':
        if (event.eventType === 'FORFEIT') room.onForfeit(event.eventData as ForfeitEventData);
        presence.applyPresenceEvent(event.eventType, event.eventData);
        break;
    }
  }

  const gamePhase = computed<MangolaPhase | null>(() => game.value?.phase ?? null);
  const isOver = computed(() => game.value?.outcome.status === 'FINISHED');
  const mySide = computed<MangolaSide>(() => (game.value ? sideOfSeat(game.value, mySeat.value) : 'A'));
  const isMyTurn = computed(() =>
    game.value !== null && game.value.phase === 'PLAY' && !isOver.value && game.value.currentSeat === mySeat.value);
  // A finished game with no winner: the same position three times, or an endless sowing allowed as a draw.
  const isDraw = computed(() => {
    const outcome = game.value?.outcome;
    return outcome?.status === 'FINISHED' && outcome.winner === null;
  });
  const iWon = computed(() => {
    const outcome = game.value?.outcome;
    return outcome?.status === 'FINISHED' && outcome.winner === mySeat.value;
  });
  // The server writes the count only from 2: a position seen twice is one move away from a draw.
  const repeatedTwice = computed(() => !isOver.value && game.value?.repetitions === 2);
  const phase = computed<'connecting' | 'lobby' | 'placement' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (!state.value.started) return 'lobby';
    if (state.value.outcome.status === 'FINISHED') return 'finished';
    return state.value.phase === 'PLACEMENT' ? 'placement' : 'playing';
  });

  // The board as I see it: my side at the bottom, columns reversed for the second side.
  const rows = computed<MangolaCell[][]>(() => (game.value ? viewerRows(game.value, mySeat.value) : []));
  const arrowOf = (pit: number): Arrow => arrowOfPit(pit, mySide.value, game.value ? directionOf(game.value) : DEFAULT_MANGOLA_DIRECTION);
  const canSow = (pit: number) => isMyTurn.value && game.value!.legalPits.includes(pit);
  // The opponent's last move, by pit marks only: the pit played and the pits captured, until my move replaces it.
  const opponentLastMarks = computed<LastMoveMarks>(() => {
    const last = game.value?.lastMove;
    return last && last.seat !== mySeat.value ? lastMoveMarks(last) : NO_MARKS;
  });

  const me = computed(() => game.value?.players.find(p => p.seat === mySeat.value) ?? null);
  const opponent = computed(() => game.value?.players.find(p => p.seat !== mySeat.value) ?? null);
  const myReady = computed(() => me.value?.ready === true);
  const opponentReady = computed(() => opponent.value?.ready === true);
  const draftOnlyEndless = computed(() => game.value?.draftOnlyEndless === true);

  const seedsLeft = computed(() => seedsLeftOf(draft.value));
  const editable = computed(() => phase.value === 'placement' && !myReady.value);
  const canConfirm = computed(() => editable.value && seedsLeft.value === 0);

  // A layout is sent whole and only when it holds the 32 seeds: the server refuses anything else.
  // Returns false when it could not be sent (socket down): the edit stays mine and the player can retry.
  function sendDraft(): boolean {
    cancelArrange();
    try {
      session.arrange([...draft.value]);
      dirty.value = false;
      return true;
    } catch {
      return false;
    }
  }

  function edit(change: (current: Draft) => Draft) {
    if (!editable.value) return;
    draft.value = change(draft.value);
    dirty.value = true;
    placementError.value = null;
    cancelArrange();
    if (seedsLeft.value === 0) arrangeTimer = setTimeout(sendDraft, ARRANGE_DEBOUNCE_MS);
  }

  const addDraftSeed = (index: number) => edit(current => addSeed(current, index));
  const removeDraftSeed = (index: number) => edit(current => removeSeed(current, index));

  function applyPreset(preset: MangolaPreset) {
    edit(() => {
      switch (preset) {
        case 'checkerboard': return checkerboardLayout(mySide.value);
        case 'two-per-pit': return twoPerPit();
        case 'random': return randomLayout();
        case 'clear': return clearLayout();
      }
    });
  }

  /** Confirm the layout: the pending edit goes first, so the server confirms what the player sees. */
  function confirmLayout() {
    if (!canConfirm.value) return;
    placementError.value = null;
    if (dirty.value && !sendDraft()) return;
    try { session.confirmLayout(); } catch { /* socket down: the screen shows it, the button stays */ }
  }

  function editLayout() {
    try { session.editLayout(); } catch { /* socket down */ }
  }

  /** A click on a pit: sow from it when it is one of my legal pits. Nothing is applied before the server answers. */
  function sow(pit: number) {
    if (!game.value || !canSow(pit)) return;
    try { session.sow(pit); } catch { /* socket down: the board stays as it is */ }
  }

  // A reopened room lands back in the lobby; drop any presence from the finished game.
  watch(phase, (p) => { if (p === 'lobby') presence.reset(); });

  const leave = () => room.leave(() => {
    state.value = null;
    botSeats.value = [];
    forgetDraft();
    gameId.value = null;
  });

  const playAgain = () => room.playAgain(() => { presence.reset(); forgetDraft(); });

  return {
    state, game, lobby, botSeats, lobbyError, removedByHost, starting, gameId, mySeat, rematch, lastForfeit, gamePhase,
    disconnections: presence.disconnections,
    liveDisconnections: presence.liveDisconnections,
    forfeitNotice: presence.forfeitNotice,
    isMyTurn, isDraw, iWon, repeatedTwice, phase, mySide, rows, arrowOf, canSow, opponentLastMarks,
    me, opponent, myReady, opponentReady, draftOnlyEndless,
    draft, seedsLeft, editable, canConfirm, placementError,
    addSeed: addDraftSeed, removeSeed: removeDraftSeed, applyPreset, confirmLayout, editLayout, sow,
    applyEvent,
    create: (name?: string) => session.create(name),
    join: (id: string, name?: string) => session.join(id, name),
    restore: (id: string, seat: number, token: string) => session.restore(id, seat, token),
    hydrate: () => session.hydrate(),
    startGame: room.startGame,
    addBot: () => requestLobbyChange(() => session.addBot()),
    removeBot: (seat: number) => requestLobbyChange(() => session.removeBot(seat)),
    setSeatOption: (seat: number, key: string, value: string) => requestLobbyChange(() => session.setSeatOption(seat, key, value)),
    removeSeat: (seat: number) => requestLobbyChange(() => session.removeSeat(seat)),
    rename: (name: string) => requestLobbyChange(() => session.rename(name)),
    setOption: (key: string, value: string) => requestLobbyChange(() => session.setOption(key, value)),
    leave,
    forfeit: () => session.forfeit(),
    playAgain,
  };
});
