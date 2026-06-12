import { ref, watch } from 'vue';

/** Short beat after the board settles before the end overlay fades in. */
export const END_SCREEN_DELAY_MS = 600;

/**
 * Owns the visibility/timing of the end-of-game overlay, kept out of the
 * component so it is unit-testable and the view holds no end-game logic.
 *
 * The winner is a property of game STATE that ANY move can set — including a
 * SEND that empties the sender's hand (e.g. the computer playing its last card).
 * So the reveal is driven by `isOver` (state), not by a specific action's
 * watcher. It waits for the pile animation to settle (`isAnimating` false) so the
 * final card movement lands first, then reveals after a short delay. This covers
 * every ending uniformly: send, grab, slap, erroneous-slap.
 *
 * `isOver` / `isAnimating` are getters into reactive state (read at call time).
 */
export function useEndScreen(
  isOver: () => boolean,
  isAnimating: () => boolean,
  delayMs = END_SCREEN_DELAY_MS,
) {
  const showEndOverlay = ref(false);
  let timeoutId: ReturnType<typeof setTimeout> | null = null;

  function scheduleReveal() {
    if (timeoutId !== null) clearTimeout(timeoutId);
    timeoutId = setTimeout(() => {
      timeoutId = null;
      showEndOverlay.value = true;
    }, delayMs);
  }

  // Reveal once the game is over AND the board has settled (no pile animation).
  // When the game is no longer over (e.g. a rematch dealt a fresh board), cancel
  // any pending reveal and hide the overlay.
  const stopWatch = watch(
    [() => isOver(), () => isAnimating()],
    ([over, animating]) => {
      if (over && !animating) {
        scheduleReveal();
      } else if (!over) {
        if (timeoutId !== null) { clearTimeout(timeoutId); timeoutId = null; }
        showEndOverlay.value = false;
      }
    },
    { immediate: true },
  );

  // Reload into an already-finished game: no animation in flight, reveal at once.
  function revealImmediatelyIfOver() {
    if (isOver()) showEndOverlay.value = true;
  }

  // Cancel a pending reveal and stop watching; caller wires into onBeforeUnmount.
  function cancel() {
    if (timeoutId !== null) {
      clearTimeout(timeoutId);
      timeoutId = null;
    }
    stopWatch();
  }

  return { showEndOverlay, revealImmediatelyIfOver, cancel };
}
