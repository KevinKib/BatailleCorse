import { computed, ref, watch } from 'vue';

type OpponentConnection =
  | { status: 'disconnected'; seat: number; deadlineEpochMs: number }
  | { status: 'connected'; seat: number }
  | null;

interface UseDisconnectCountdownOptions {
  mode: () => 'solo' | 'multiplayer';
  opponentConnection: () => OpponentConnection;
  myPlayerIndex: () => number;
  isGameOver: () => boolean;
}

/**
 * Opponent-disconnect countdown. The deadline is server-provided (absolute epoch
 * ms); the local clock only renders the remaining seconds. The 250ms ticker runs
 * only while the opponent is disconnected, and is cleared on reconnect/game-end.
 * The caller wires `cancel()` into onBeforeUnmount (same pattern as useEndScreen).
 */
export function useDisconnectCountdown(options: UseDisconnectCountdownOptions) {
  const { mode, opponentConnection, myPlayerIndex, isGameOver } = options;

  const now = ref(Date.now());
  let countdownTimer: ReturnType<typeof setInterval> | null = null;

  const opponentDisconnected = computed(() => {
    const oc = opponentConnection();
    return mode() === 'multiplayer'
      && oc?.status === 'disconnected'
      && oc.seat !== myPlayerIndex()
      && !isGameOver();
  });

  const secondsRemaining = computed(() => {
    const oc = opponentConnection();
    if (oc?.status !== 'disconnected') return 0;
    return Math.max(0, Math.ceil((oc.deadlineEpochMs - now.value) / 1000));
  });

  const stop = watch(opponentDisconnected, (active) => {
    if (active && countdownTimer === null) {
      now.value = Date.now();
      countdownTimer = setInterval(() => { now.value = Date.now(); }, 250);
    } else if (!active && countdownTimer !== null) {
      clearInterval(countdownTimer);
      countdownTimer = null;
    }
  });

  /** Stop the interval and the watcher; wire into the view's onBeforeUnmount. */
  function cancel() {
    stop();
    if (countdownTimer !== null) { clearInterval(countdownTimer); countdownTimer = null; }
  }

  return { opponentDisconnected, secondsRemaining, cancel };
}
