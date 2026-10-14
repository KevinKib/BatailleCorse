import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

import webSocketService from '../service/WebSocketService';
import MorrisSession, { type MorrisSessionEvent } from '../application/threemenmorris/MorrisSession';
import type { MorrisColour, MorrisEventData, MorrisPhase, MorrisState, MorrisVariantKey, MorrisView } from '../model/threemenmorris/MorrisState';
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

export const useThreeMenMorrisStore = defineStore('three-men-morris-store', () => {
  const state = ref<MorrisView | null>(null);
  const gameId = ref<string | null>(null);
  const mySeat = ref<number>(0);
  // The piece I picked before choosing where it goes: a pure UI state, the server judges the move.
  const selected = ref<number | null>(null);
  // What the last PLACE / MOVE (or the GAME_OVER it caused) did, for the live region and the board.
  const lastMove = ref<MorrisEventData | null>(null);

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
        if (!event.state.started) lastMove.value = null;
        break;
      case 'game-id-change': gameId.value = event.gameId; break;
      case 'error':
        room.onError(state.value?.started === true, event.message);
        break;
      case 'removed':
        state.value = null;
        selected.value = null;
        lastMove.value = null;
        gameId.value = null;
        room.onRemoved();
        break;
      case 'seat-change': mySeat.value = event.seat; break;
      case 'event':
        if (isMoveData(event.eventData)) {
          lastMove.value = event.eventData;
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
  const phase = computed<'connecting' | 'lobby' | 'playing' | 'finished'>(() => {
    if (!state.value) return 'connecting';
    if (!state.value.started) return 'lobby';
    if (state.value.outcome.status === 'FINISHED') return 'finished';
    return 'playing';
  });

  const selectablePoints = computed(() => (game.value ? selectable(game.value, mySeat.value) : []));
  const targetPoints = computed(() => (game.value ? targets(game.value, selected.value) : []));

  // A reopened room lands back in the lobby; drop any presence from the finished game.
  watch(phase, (p) => { if (p === 'lobby') presence.reset(); });

  const leave = () => room.leave(() => {
    state.value = null;
    selected.value = null;
    lastMove.value = null;
    gameId.value = null;
  });

  const playAgain = () => room.playAgain(() => { presence.reset(); selected.value = null; });

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
      case 'select': selected.value = intent.point; break;
      case 'clear': selected.value = null; break;
      case 'none': break;
    }
  }

  return {
    state, game, lobby, lobbyError, removedByHost, starting, gameId, mySeat, rematch, lastForfeit,
    selected, lastMove, gamePhase,
    disconnections: presence.disconnections,
    liveDisconnections: presence.liveDisconnections,
    forfeitNotice: presence.forfeitNotice,
    isMyTurn, iWon, myColour, colourOf, variant, geometry, opponentLastMove, phase, selectablePoints, targetPoints,
    applyEvent, pointClicked, clearSelection,
    create: (name?: string) => session.create(name),
    join: (id: string, name?: string) => session.join(id, name),
    restore: (id: string, seat: number, token: string) => session.restore(id, seat, token),
    hydrate: () => session.hydrate(),
    startGame: room.startGame,
    removeSeat: (seat: number) => requestLobbyChange(() => session.removeSeat(seat)),
    rename: (name: string) => requestLobbyChange(() => session.rename(name)),
    setOption: (key: string, value: string) => requestLobbyChange(() => session.setOption(key, value)),
    leave,
    forfeit: () => session.forfeit(),
    playAgain,
  };
});
