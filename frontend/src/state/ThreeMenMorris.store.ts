import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

import webSocketService from '../service/WebSocketService';
import MorrisSession, { type MorrisSessionEvent } from '../application/threemenmorris/MorrisSession';
import type {
  MorrisColour, MorrisDrawEventData, MorrisEventData, MorrisPhase, MorrisRemovalEventData, MorrisState, MorrisVariantKey,
  MorrisView,
} from '../model/threemenmorris/MorrisState';
import type { LobbyView } from '../model/lobby/LobbyView';
import { colourOf as colourOfSeat, onPoint, selectable, targets } from '../model/threemenmorris/board';
import { geometryOf, variantKeyOf, type MorrisGeometry } from '../model/threemenmorris/geometries';
import { useSeatPresence, FORFEIT_NOTICE_HOLD_MS } from '../composables/useSeatPresence';
import { useSeatRoom } from '../composables/useSeatRoom';
import type { ForfeitEventData } from '../model/SeatLifecycleEvents';

export { FORFEIT_NOTICE_HOLD_MS };

export type { MorrisColour };

function isMoveData(data: unknown): data is MorrisEventData {
  const action = (data as { action?: unknown } | null)?.action;
  return action === 'PLACE' || action === 'MOVE';
}

function isRemovalData(data: unknown): data is MorrisRemovalEventData {
  return (data as { action?: unknown } | null)?.action === 'REMOVE';
}

function isDrawData(data: unknown): data is MorrisDrawEventData {
  const action = (data as { action?: unknown } | null)?.action;
  return action === 'OFFER_DRAW' || action === 'DECLINE_DRAW';
}

/** What the live region says about a draw: an offer, or the refusal of one. */
export interface MorrisDrawNotice {
  kind: 'offered' | 'declined';
  seat: number;
}

export const useThreeMenMorrisStore = defineStore('three-men-morris-store', () => {
  const state = ref<MorrisView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  // The piece I picked before choosing where it goes: a pure UI state, the server judges the move.
  const selected = ref<number | null>(null);
  // What the last PLACE / MOVE (or the GAME_OVER it caused) did, for the live region and the board.
  const lastMove = ref<MorrisEventData | null>(null);
  // The last REMOVE (or the GAME_OVER it caused), until the next placement or move: the point is marked as captured.
  const lastRemoval = ref<MorrisRemovalEventData | null>(null);
  // The last draw offer or refusal, until the next move (which ends any offer) or a new game.
  const drawNotice = ref<MorrisDrawNotice | null>(null);

  const game = computed<MorrisState | null>(() =>
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

  const session = new MorrisSession(webSocketService, {
    onEvent(event: MorrisSessionEvent) { applyEvent(event); },
  });

  function applyEvent(event: MorrisSessionEvent) {
    switch (event.type) {
      case 'state-update':
        // A selection belongs to the state it was made on: whatever the server says next replaces it.
        selected.value = null;
        state.value = event.state;
        room.onStateUpdate(event.state.started);
        if (!event.state.started) { lastMove.value = null; lastRemoval.value = null; drawNotice.value = null; }
        break;
      case 'game-id-change': gameId.value = event.gameId; break;
      case 'error':
        room.onError(state.value?.started === true, event.message);
        break;
      case 'removed':
        state.value = null;
        selected.value = null;
        lastMove.value = null;
        lastRemoval.value = null;
        drawNotice.value = null;
        gameId.value = null;
        room.onRemoved();
        break;
      case 'seat-change': mySeat.value = event.seat; break;
      case 'event':
        if (isMoveData(event.eventData)) {
          lastMove.value = event.eventData;
          lastRemoval.value = null;
          drawNotice.value = null;
        } else if (isRemovalData(event.eventData)) {
          // The mill-closing move stays the last move (its ring stays on the board); the removal is announced and marked.
          lastRemoval.value = event.eventData;
        } else if (isDrawData(event.eventData)) {
          drawNotice.value = {
            kind: event.eventData.action === 'OFFER_DRAW' ? 'offered' : 'declined',
            seat: event.eventData.seat,
          };
        } else {
          if (event.eventType === 'FORFEIT') room.onForfeit(event.eventData as ForfeitEventData);
          presence.applyPresenceEvent(event.eventType, event.eventData);
        }
        break;
    }
  }

  const gamePhase = computed<MorrisPhase | null>(() => game.value?.phase ?? null);
  const isOver = computed(() => game.value?.outcome.status === 'FINISHED');
  const isMyTurn = computed(() =>
    game.value !== null && !isOver.value && game.value.currentSeat === mySeat.value);
  // A finished game with no winner: agreed, or the same position three times.
  const isDraw = computed(() => {
    const outcome = game.value?.outcome;
    return outcome?.status === 'FINISHED' && outcome.winner === null;
  });
  const iWon = computed(() => {
    const outcome = game.value?.outcome;
    return outcome?.status === 'FINISHED' && outcome.winner === mySeat.value;
  });
  // The colour of each seat comes from the state: the host may play Black. White always starts.
  const colourOf = (seat: number): MorrisColour =>
    game.value ? colourOfSeat(game.value, seat) : seat === 0 ? 'WHITE' : 'BLACK';
  const myColour = computed<MorrisColour>(() => colourOf(mySeat.value));
  const variant = computed<MorrisVariantKey>(() => variantKeyOf(game.value));
  const geometry = computed<MorrisGeometry>(() => geometryOf(game.value));
  // The points of the opponent's last move, until my own move or the next game replaces it.
  const opponentLastMove = computed<number[]>(() => {
    const move = lastMove.value;
    if (!move || move.seat === mySeat.value) return [];
    if (move.action === 'PLACE') return move.point !== null ? [move.point] : [];
    return move.from !== null && move.to !== null ? [move.from, move.to] : [];
  });
  // The draw commands I may send. Absent means OFFER_DRAW in the movement phase (the server writes only the rest).
  const drawActions = computed<string[]>(() => {
    const current = game.value;
    if (!current || isOver.value) return [];
    return current.drawActions ?? (current.phase === 'MOVEMENT' ? ['OFFER_DRAW'] : []);
  });
  const canOfferDraw = computed(() => drawActions.value.includes('OFFER_DRAW'));
  const canAnswerDraw = computed(() => drawActions.value.includes('ACCEPT_DRAW'));
  const drawOfferedBy = computed<number | null>(() => game.value?.drawOffer?.seat ?? null);
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

  // A mill was closed and its owner has yet to take a piece (Six Men's Morris); the turn has not passed.
  const removalPending = computed(() => game.value?.removal !== undefined && !isOver.value);
  const iMustRemove = computed(() => removalPending.value && game.value?.removal?.seat === mySeat.value);
  const capturedPoint = computed<number | null>(() => lastRemoval.value?.point ?? null);

  const selectablePoints = computed(() => (game.value ? selectable(game.value, mySeat.value) : []));
  const targetPoints = computed(() => (game.value ? targets(game.value, selected.value) : []));

  // A reopened room lands back in the lobby; drop any presence from the finished game.
  watch(phase, (p) => { if (p === 'lobby') presence.reset(); });

  const leave = () => room.leave(() => {
    state.value = null;
    selected.value = null;
    lastMove.value = null;
    lastRemoval.value = null;
    drawNotice.value = null;
    gameId.value = null;
  });

  const playAgain = () => room.playAgain(() => { presence.reset(); selected.value = null; });

  // A draw command the socket cannot carry is dropped: the screen shows the lost connection, and the buttons stay.
  const sendDraw = (send: () => void) => () => { try { send(); } catch { /* socket down */ } };
  const offerDraw = sendDraw(() => session.offerDraw());
  const acceptDraw = sendDraw(() => session.acceptDraw());
  const declineDraw = sendDraw(() => session.declineDraw());

  function clearSelection() { selected.value = null; }

  /** A click on a point: place, move, or just change the selection. Nothing is applied before the server answers. */
  function pointClicked(point: number) {
    if (!game.value) return;
    const intent = onPoint(game.value, mySeat.value, selected.value, point);
    switch (intent.kind) {
      case 'place':
        try { session.place(intent.point); } catch { /* socket down: the screen shows it, the board stays as it is */ }
        break;
      case 'move':
        try {
          session.move(intent.from, intent.to);
          selected.value = null;
        } catch { /* socket down: keep the selection so that the player can retry */ }
        break;
      case 'remove':
        try { session.remove(intent.point); } catch { /* socket down: the screen shows it, the board stays as it is */ }
        break;
      case 'select': selected.value = intent.point; break;
      case 'clear': selected.value = null; break;
      case 'none': break;
    }
  }

  return {
    state, game, lobby, lobbyError, removedByHost, starting, gameId, mySeat, rematch, lastForfeit,
    selected, lastMove, lastRemoval, capturedPoint, removalPending, iMustRemove, drawNotice, gamePhase,
    disconnections: presence.disconnections,
    liveDisconnections: presence.liveDisconnections,
    forfeitNotice: presence.forfeitNotice,
    isDraw, drawActions, canOfferDraw, canAnswerDraw, drawOfferedBy, myDrawOfferPending, incomingDrawOffer, repeatedTwice,
    offerDraw, acceptDraw, declineDraw,
    isMyTurn, iWon, myColour, colourOf, variant, geometry, opponentLastMove, phase, selectablePoints, targetPoints,
    applyEvent, pointClicked, clearSelection,
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
