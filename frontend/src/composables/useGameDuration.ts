import { computed, ref, watch } from 'vue';

/** Format an elapsed millisecond count as mm:ss, rolling to h:mm:ss past an hour. */
export function formatDuration(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000);
  const seconds = totalSeconds % 60;
  const minutes = Math.floor(totalSeconds / 60) % 60;
  const hours = Math.floor(totalSeconds / 3600);
  const pad = (n: number) => String(n).padStart(2, '0');
  return hours > 0
    ? `${hours}:${pad(minutes)}:${pad(seconds)}`
    : `${pad(minutes)}:${pad(seconds)}`;
}

const TICK_MS = 1000;

/**
 * Cosmetic, frontend-only game-duration timer. Starts counting the first time the
 * game becomes active (state loaded, not waiting, not over), ticks once a second,
 * and freezes the elapsed value at game over. It is NOT authoritative — it resets
 * on refresh and is not shared between players.
 *
 * `isActive` / `isOver` are getters into reactive state (read at call time), the
 * same pattern as `useEndScreen`. The caller wires `cancel()` into onBeforeUnmount.
 */
export function useGameDuration(
  isActive: () => boolean,
  isOver: () => boolean,
) {
  const startEpochMs = ref<number | null>(null);
  const frozenMs = ref<number | null>(null);
  const now = ref(Date.now());
  let intervalId: ReturnType<typeof setInterval> | null = null;

  function startTicking() {
    if (intervalId !== null) return;
    now.value = Date.now();
    intervalId = setInterval(() => { now.value = Date.now(); }, TICK_MS);
  }

  function stopTicking() {
    if (intervalId !== null) {
      clearInterval(intervalId);
      intervalId = null;
    }
  }

  const stopWatch = watch(
    [() => isActive(), () => isOver()],
    ([active, over]) => {
      if (over) {
        if (frozenMs.value === null && startEpochMs.value !== null) {
          frozenMs.value = Date.now() - startEpochMs.value;
        }
        stopTicking();
        return;
      }
      if (active) {
        if (startEpochMs.value === null) startEpochMs.value = Date.now();
        startTicking();
      } else {
        stopTicking();
      }
    },
    { immediate: true },
  );

  const elapsedMs = computed(() => {
    if (frozenMs.value !== null) return frozenMs.value;
    if (startEpochMs.value === null) return 0;
    return Math.max(0, now.value - startEpochMs.value);
  });

  const formattedDuration = computed(() => formatDuration(elapsedMs.value));

  /** Stop the interval and the watcher; wire into the view's onBeforeUnmount. */
  function cancel() {
    stopTicking();
    stopWatch();
  }

  return { formattedDuration, cancel };
}
