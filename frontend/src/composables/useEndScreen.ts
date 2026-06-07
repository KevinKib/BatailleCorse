import { ref } from 'vue';

/** Short beat after the final card lands before the end overlay fades in. */
export const END_SCREEN_DELAY_MS = 600;

/**
 * Owns the visibility/timing of the end-of-game overlay, kept out of the
 * component so it is unit-testable. `isOver` is a getter into reactive game
 * state (read at call time, not captured).
 */
export function useEndScreen(isOver: () => boolean, delayMs = END_SCREEN_DELAY_MS) {
  const showEndOverlay = ref(false);
  let timeoutId: ReturnType<typeof setTimeout> | null = null;

  // Live win: the caller invokes this once the final card animation resolves.
  function revealAfterAnimation() {
    if (!isOver()) return;
    if (timeoutId !== null) clearTimeout(timeoutId);
    timeoutId = setTimeout(() => {
      timeoutId = null;
      showEndOverlay.value = true;
    }, delayMs);
  }

  // Reload into a finished game: no animation in flight, reveal at once.
  function revealImmediatelyIfOver() {
    if (isOver()) showEndOverlay.value = true;
  }

  // Cancel a pending reveal; the caller wires this into onBeforeUnmount.
  function cancel() {
    if (timeoutId !== null) {
      clearTimeout(timeoutId);
      timeoutId = null;
    }
  }

  return { showEndOverlay, revealAfterAnimation, revealImmediatelyIfOver, cancel };
}
