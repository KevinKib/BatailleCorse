import { computed, readonly, ref, watch } from 'vue';

export const YOUR_TURN_LABEL = 'YOUR TURN';

/** Minimal structural view of the game model this composable needs. `BatailleCorse` satisfies it. */
export interface TurnState {
  canSend(playerIndex: number): boolean;
}

interface UseTurnIndicatorOptions {
  state: () => TurnState | undefined;
  myPlayerIndex: () => number;
  opponentIndex: () => number;
  isWaiting: () => boolean;
  showEndOverlay: () => boolean;
}

/**
 * Per-seat turn glow driven by the backend's `canSend(seat)` (the server only
 * offers SEND to the player whose turn it is and only while a card can be added),
 * plus a one-time "YOUR TURN" hint shown the first time it becomes the player's
 * turn and then dismissed for the rest of the game. Cues are suppressed while an
 * overlay owns the screen. The caller wires `cancel()` into onBeforeUnmount.
 */
export function useTurnIndicator(options: UseTurnIndicatorOptions) {
  const { state, myPlayerIndex, opponentIndex, isWaiting, showEndOverlay } = options;

  const showTurnCues = computed(() => !isWaiting() && !showEndOverlay());
  const showMyTurn = computed(() =>
    showTurnCues.value && (state()?.canSend(myPlayerIndex()) ?? false));
  const showOpponentTurn = computed(() =>
    showTurnCues.value && (state()?.canSend(opponentIndex()) ?? false));

  const showFirstTurnHint = ref(false);
  let firstTurnHintConsumed = false;

  const stop = watch(showMyTurn, (mine) => {
    if (firstTurnHintConsumed) return;
    if (mine) {
      showFirstTurnHint.value = true;
    } else if (showFirstTurnHint.value) {
      showFirstTurnHint.value = false;
      firstTurnHintConsumed = true;
    }
  }, { immediate: true });

  /** Stop the first-turn-hint watcher; wire into the view's onBeforeUnmount. */
  function cancel() {
    stop();
  }

  return { showMyTurn, showOpponentTurn, showFirstTurnHint: readonly(showFirstTurnHint), YOUR_TURN_LABEL, cancel };
}
