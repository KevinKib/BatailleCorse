import { ref, computed, watch } from 'vue';

export interface UseSeatDisconnectCountdownOptions {
  disconnections: () => Record<number, number>;
  isGameOver: () => boolean;
}

/**
 * Multi-seat opponent-disconnect countdown. Deadlines are server-provided (absolute
 * epoch ms); the local clock only renders remaining seconds. One 250ms ticker runs
 * while >=1 seat is disconnected and the game isn't over. Generalizes BatailleCorse's
 * useDisconnectCountdown from a single connection to a map of seats. The caller wires
 * cancel() into onBeforeUnmount.
 */
export function useSeatDisconnectCountdown(options: UseSeatDisconnectCountdownOptions) {
  const { disconnections, isGameOver } = options;

  const now = ref(Date.now());
  let timer: ReturnType<typeof setInterval> | null = null;

  const active = computed(
    () => Object.keys(disconnections()).length > 0 && !isGameOver(),
  );

  const stop = watch(
    active,
    (on) => {
      if (on && timer === null) {
        now.value = Date.now();
        timer = setInterval(() => { now.value = Date.now(); }, 250);
      } else if (!on && timer !== null) {
        clearInterval(timer);
        timer = null;
      }
    },
    { immediate: true },
  );

  function secondsRemainingFor(deadlineEpochMs: number): number {
    return Math.max(0, Math.ceil((deadlineEpochMs - now.value) / 1000));
  }

  /** Stop the interval and the watcher; wire into the view's onBeforeUnmount. */
  function cancel(): void {
    stop();
    if (timer !== null) {
      clearInterval(timer);
      timer = null;
    }
  }

  return { secondsRemainingFor, cancel };
}
