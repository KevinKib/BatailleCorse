import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ref, nextTick } from 'vue';
import { useDisconnectCountdown } from './useDisconnectCountdown';

type Conn =
  | { status: 'disconnected'; seat: number; deadlineEpochMs: number }
  | { status: 'connected'; seat: number }
  | null;

function setup(initial: Conn) {
  const conn = ref<Conn>(initial);
  const mode = ref<'solo' | 'multiplayer'>('multiplayer');
  const over = ref(false);
  const api = useDisconnectCountdown({
    mode: () => mode.value,
    opponentConnection: () => conn.value,
    myPlayerIndex: () => 0,
    isGameOver: () => over.value,
  });
  return { conn, mode, over, ...api };
}

describe('useDisconnectCountdown', () => {
  beforeEach(() => vi.useFakeTimers({ now: 1_000_000 }));
  afterEach(() => vi.useRealTimers());

  it('givenOpponentConnected_thenNotDisconnectedAndZeroSeconds', () => {
    const { opponentDisconnected, secondsRemaining, cancel } = setup({ status: 'connected', seat: 1 });
    expect(opponentDisconnected.value).toBe(false);
    expect(secondsRemaining.value).toBe(0);
    cancel();
  });

  it('givenOpponentDisconnected_thenComputesRemainingSecondsFromDeadline', async () => {
    const deadline = Date.now() + 10_000;
    const ctx = setup({ status: 'connected', seat: 1 });
    ctx.conn.value = { status: 'disconnected', seat: 1, deadlineEpochMs: deadline };
    await nextTick();
    expect(ctx.opponentDisconnected.value).toBe(true);
    expect(ctx.secondsRemaining.value).toBe(10);

    vi.advanceTimersByTime(4_000);
    expect(ctx.secondsRemaining.value).toBe(6);
    ctx.cancel();
  });

  it('givenGameOver_thenNotConsideredDisconnected', () => {
    const deadline = Date.now() + 10_000;
    const { opponentDisconnected, over, cancel } = setup({ status: 'disconnected', seat: 1, deadlineEpochMs: deadline });
    over.value = true;
    expect(opponentDisconnected.value).toBe(false);
    cancel();
  });

  it('givenDisconnectedSeatIsMine_thenNotConsideredDisconnected', () => {
    const deadline = Date.now() + 10_000;
    const { opponentDisconnected, cancel } = setup({ status: 'disconnected', seat: 0, deadlineEpochMs: deadline });
    expect(opponentDisconnected.value).toBe(false);
    cancel();
  });

  it('givenReconnect_thenTimerStopsAndSecondsResetToZero', async () => {
    const deadline = Date.now() + 10_000;
    const ctx = setup({ status: 'connected', seat: 1 });
    ctx.conn.value = { status: 'disconnected', seat: 1, deadlineEpochMs: deadline };
    await nextTick();
    expect(vi.getTimerCount()).toBeGreaterThan(0);

    ctx.conn.value = { status: 'connected', seat: 1 };
    await nextTick();
    expect(vi.getTimerCount()).toBe(0);
    expect(ctx.secondsRemaining.value).toBe(0);
    ctx.cancel();
  });

  it('givenActiveCountdown_whenCancel_thenTimerStops', async () => {
    const deadline = Date.now() + 10_000;
    const ctx = setup({ status: 'connected', seat: 1 });
    ctx.conn.value = { status: 'disconnected', seat: 1, deadlineEpochMs: deadline };
    await nextTick();
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    ctx.cancel();
    expect(vi.getTimerCount()).toBe(0);
  });
});
