import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

import webSocketService from '../service/WebSocketService';
import DraughtsSession, { type DraughtsSessionEvent } from '../application/draughts/DraughtsSession';
import type {
  DraughtsColour, DraughtsLegalMove, DraughtsMoveEventData, DraughtsState, DraughtsView,
} from '../model/draughts/DraughtsState';
import type { LobbyView } from '../model/lobby/LobbyView';
import {
  onCandidatePiece, onSquare, orientationOf, selectable, separatingSquares, targets, type DraughtsOrientation,
  type SquareIntent,
} from '../model/draughts/board';
import { useSeatPresence, FORFEIT_NOTICE_HOLD_MS } from '../composables/useSeatPresence';
import { useSeatRoom } from '../composables/useSeatRoom';
import { botLevelsOf } from '../model/lobby/botLevel';
import type { ForfeitEventData } from '../model/SeatLifecycleEvents';

export { FORFEIT_NOTICE_HOLD_MS };

export type { DraughtsColour };

/** A move event carries the piece's start, its landing squares and what it took; other events lack them. */
function isMoveData(data: unknown): data is DraughtsMoveEventData {
  const move = data as Partial<DraughtsMoveEventData> | null;
  return typeof move?.from === 'number' && typeof move.to === 'number' && Array.isArray(move.path);
}

/** What the live region says about a draw: an offer, or the refusal of one. */
export interface DraughtsDrawNotice {
  kind: 'offered' | 'declined';
  seat: number | null;
}

const seatOf = (data: unknown): number | null => {
  const seat = (data as { seat?: unknown } | null)?.seat;
  return typeof seat === 'number' ? seat : null;
};

export const useDraughtsStore = defineStore('draughts-store', () => {
  const state = ref<DraughtsView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  // The level of each computer seat: the game view's `level`, else the one remembered from the lobby.
  const botLevels = ref<Record<number, string>>({});
  // The piece I picked before choosing where it goes: a pure UI state, the server judges the move.
  const selected = ref<number | null>(null);
  // The moves that share one start and end but take different pieces, while the player picks among them.
  const choice = ref<DraughtsLegalMove[] | null>(null);
  // What the last MOVE (or the GAME_OVER it caused) did, for the glide, the live region and the board.
  const lastMove = ref<DraughtsMoveEventData | null>(null);
  // The last draw offer or refusal, until the next move (which ends any offer) or a new game.
  const drawNotice = ref<DraughtsDrawNotice | null>(null);

  const game = computed<DraughtsState | null>(() =>
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

  const session = new DraughtsSession(webSocketService, {
    onEvent(event: DraughtsSessionEvent) { applyEvent(event); },
  });

  function clearTransient() {
    selected.value = null;
    choice.value = null;
    lastMove.value = null;
    drawNotice.value = null;
  }

  function applyEvent(event: DraughtsSessionEvent) {
    switch (event.type) {
      case 'state-update':
        // A selection belongs to the state it was made on: a new version replaces it, while an update of the
        // same version (a presence change) leaves it alone.
        const before = state.value?.started ? state.value.version : null;
        if (!event.state.started || before !== event.state.version) {
          selected.value = null;
          choice.value = null;
        }
        state.value = event.state;
        room.onStateUpdate(event.state.started);
        // The lobby records the levels; the game view carries them too (a reload mid-game has no lobby) and, when
        // it does, what it says stands over the lobby's, which stays the fallback for a server that says nothing.
        botLevels.value = event.state.started
          ? { ...botLevels.value, ...botLevelsOf(event.state.players) }
          : botLevelsOf(event.state.players);
        if (!event.state.started) { lastMove.value = null; drawNotice.value = null; }
        break;
      case 'game-id-change': gameId.value = event.gameId; break;
      case 'error':
        room.onError(state.value?.started === true, event.message);
        break;
      case 'removed':
        state.value = null;
        botLevels.value = {};
        clearTransient();
        gameId.value = null;
        room.onRemoved();
        break;
      case 'seat-change': mySeat.value = event.seat; break;
      case 'event':
        if ((event.eventType === 'MOVE' || event.eventType === 'GAME_OVER') && isMoveData(event.eventData)) {
          lastMove.value = event.eventData;
          drawNotice.value = null;
        } else if (event.eventType === 'DRAW_OFFERED' || event.eventType === 'DRAW_DECLINED') {
          drawNotice.value = {
            kind: event.eventType === 'DRAW_OFFERED' ? 'offered' : 'declined',
            seat: seatOf(event.eventData),
          };
        } else {
          if (event.eventType === 'FORFEIT') room.onForfeit(event.eventData as ForfeitEventData);
          presence.applyPresenceEvent(event.eventType, event.eventData);
        }
        break;
    }
  }

  const isOver = computed(() => game.value?.outcome.status === 'FINISHED');
  const isMyTurn = computed(() =>
    game.value !== null && !isOver.value && game.value.currentSeat === mySeat.value);
  // A finished game with no winner: agreed, repetition, or one of the move-count rules.
  const isDraw = computed(() => {
    const outcome = game.value?.outcome;
    return outcome?.status === 'FINISHED' && outcome.winner === null;
  });
  const iWon = computed(() => {
    const outcome = game.value?.outcome;
    return outcome?.status === 'FINISHED' && outcome.winner === mySeat.value;
  });
  // The colour of each seat comes from the state: the host may play Black. White always starts.
  const colourOf = (seat: number): DraughtsColour => {
    const current = game.value;
    if (!current) return seat === 0 ? 'WHITE' : 'BLACK';
    return current.players.find(p => p.seat === seat)?.colour ?? (current.whiteSeat === seat ? 'WHITE' : 'BLACK');
  };
  const myColour = computed<DraughtsColour>(() => colourOf(mySeat.value));
  // Each player sees their own men at the bottom.
  const orientation = computed<DraughtsOrientation>(() =>
    game.value ? orientationOf(game.value, mySeat.value) : 'WHITE');
  // The last move, as long as it is the opponent's: my own is already on the board by the time it comes back.
  const opponentLastMove = computed<DraughtsMoveEventData | null>(() =>
    lastMove.value && lastMove.value.seat !== mySeat.value ? lastMove.value : null);
  // The count to show in the hint "you must capture", while the capture is mine to make.
  const captureHint = computed<number | null>(() =>
    isMyTurn.value && game.value?.captureRequired ? game.value.maxCaptures : null);

  const canOfferDraw = computed(() => !isOver.value && (game.value?.availableActions.includes('OFFER_DRAW') ?? false));
  const canAnswerDraw = computed(() => !isOver.value && (game.value?.availableActions.includes('ACCEPT_DRAW') ?? false));
  const drawOfferedBy = computed<number | null>(() => game.value?.drawOffer?.by ?? null);
  const myDrawOfferPending = computed(() => drawOfferedBy.value !== null && drawOfferedBy.value === mySeat.value);
  const incomingDrawOffer = computed(() => drawOfferedBy.value !== null && drawOfferedBy.value !== mySeat.value && !isOver.value);
  // The server writes the count only from 2: a position seen twice is one move away from a draw.
  const repeatedTwice = computed(() => !isOver.value && game.value?.repetitions === 2);
  const phase = computed<'connecting' | 'lobby' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (!state.value.started) return 'lobby';
    if (state.value.outcome.status === 'FINISHED') return 'finished';
    return 'playing';
  });

  const selectableSquares = computed(() => (game.value ? selectable(game.value, mySeat.value) : []));
  const targetSquares = computed(() => (game.value ? targets(game.value, selected.value, mySeat.value) : []));
  // The pieces that tell the candidates apart (square to candidate index) while a chooser is open.
  const choiceSquares = computed(() => separatingSquares(choice.value ?? []));

  // A reopened room lands back in the lobby; drop any presence from the finished game.
  watch(phase, (p) => { if (p === 'lobby') presence.reset(); });

  const leave = () => room.leave(() => {
    state.value = null;
    botLevels.value = {};
    clearTransient();
    gameId.value = null;
  });

  const playAgain = () => room.playAgain(() => { presence.reset(); selected.value = null; choice.value = null; });

  // A draw command the socket cannot carry is dropped: the screen shows the lost connection, and the buttons stay.
  const sendDraw = (send: () => void) => () => { try { send(); } catch { /* socket down */ } };
  const offerDraw = sendDraw(() => session.offerDraw());
  const acceptDraw = sendDraw(() => session.acceptDraw());
  const declineDraw = sendDraw(() => session.declineDraw());

  function clearSelection() { selected.value = null; choice.value = null; }

  function play(intent: Extract<SquareIntent, { kind: 'play' }>) {
    try {
      session.move(intent.from, intent.to, intent.captures);
      selected.value = null;
      choice.value = null;
    } catch { /* socket down: keep the selection so that the player can retry */ }
  }

  function apply(intent: SquareIntent) {
    switch (intent.kind) {
      case 'play': play(intent); break;
      case 'choose': choice.value = intent.candidates; break;
      case 'select': selected.value = intent.square; choice.value = null; break;
      case 'deselect': clearSelection(); break;
      case 'none': break;
    }
  }

  /** A tap on a square: select, play, open the chooser or pick in it. Nothing is applied before the server answers. */
  function squareClicked(square: number) {
    if (!game.value) return;
    if (choice.value) {
      const picked = onCandidatePiece(choice.value, square);
      if (picked.kind === 'play') { apply(picked); return; }
      choice.value = null;
    }
    apply(onSquare(game.value, mySeat.value, selected.value, square));
  }

  /** The chooser list: pick the candidate at `index`. */
  function chooseCandidate(index: number) {
    const candidate = choice.value?.[index];
    if (!candidate) return;
    play({ kind: 'play', from: candidate.from, to: candidate.to, captures: [...candidate.captures] });
  }

  return {
    state, game, lobby, botLevels, lobbyError, removedByHost, starting, gameId, mySeat, rematch, lastForfeit,
    selected, choice, choiceSquares, lastMove, opponentLastMove, drawNotice, captureHint,
    disconnections: presence.disconnections,
    liveDisconnections: presence.liveDisconnections,
    forfeitNotice: presence.forfeitNotice,
    isOver, isDraw, canOfferDraw, canAnswerDraw, drawOfferedBy, myDrawOfferPending, incomingDrawOffer, repeatedTwice,
    offerDraw, acceptDraw, declineDraw,
    isMyTurn, iWon, myColour, colourOf, orientation, phase, selectableSquares, targetSquares,
    applyEvent, squareClicked, chooseCandidate, clearSelection,
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
    setPrivate: (privateGame: boolean) => requestLobbyChange(() => session.setPrivate(privateGame)),
    setOption: (key: string, value: string) => requestLobbyChange(() => session.setOption(key, value)),
    leave,
    forfeit: () => session.forfeit(),
    playAgain,
  };
});
