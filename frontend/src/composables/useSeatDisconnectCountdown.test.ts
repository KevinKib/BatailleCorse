import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ref, nextTick } from 'vue';
import { useSeatDisconnectCountdown } from './useSeatDisconnectCountdown';

function setup(initial: Record<number, number>) {
  const map = ref<Record<number, number>>(initial);
  const over = ref(false);
  const api = useSeatDisconnectCountdown({
    disconnections: () => map.value,
    isGameOver: () => over.value,
  });
  return { map, over, ...api };
}

describe('useSeatDisconnectCountdown', () => {
  beforeEach(() => vi.useFakeTimers({ now: 1_000_000 }));
  afterEach(() => vi.useRealTimers());

  it('givenNoDisconnections_thenNoTicker', () => {
    const { cancel } = setup({});
    expect(vi.getTimerCount()).toBe(0);
    cancel();
  });

  it('givenDisconnectedSeat_thenComputesRemainingSecondsFromDeadline', async () => {
    const ctx = setup({});
    ctx.map.value = { 1: Date.now() + 10_000 };
    await nextTick();
    expect(ctx.secondsRemainingFor(ctx.map.value[1])).toBe(10);

    vi.advanceTimersByTime(4_000);
    expect(ctx.secondsRemainingFor(ctx.map.value[1])).toBe(6);
    ctx.cancel();
  });

  it('givenAllReconnected_thenTickerStops', async () => {
    const ctx = setup({ 1: Date.now() + 10_000 });
    await nextTick();
    expect(vi.getTimerCount()).toBeGreaterThan(0);

    ctx.map.value = {};
    await nextTick();
    expect(vi.getTimerCount()).toBe(0);
    ctx.cancel();
  });

  it('givenGameOver_thenTickerStops', async () => {
    const ctx = setup({ 1: Date.now() + 10_000 });
    await nextTick();
    expect(vi.getTimerCount()).toBeGreaterThan(0);

    ctx.over.value = true;
    await nextTick();
    expect(vi.getTimerCount()).toBe(0);
    ctx.cancel();
  });

  it('givenPastDeadline_thenClampsToZero', async () => {
    const ctx = setup({ 1: Date.now() - 5_000 });
    await nextTick();
    expect(ctx.secondsRemainingFor(ctx.map.value[1])).toBe(0);
    ctx.cancel();
  });

  it('givenActiveTicker_whenCancel_thenStops', async () => {
    const ctx = setup({ 1: Date.now() + 10_000 });
    await nextTick();
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    ctx.cancel();
    expect(vi.getTimerCount()).toBe(0);
  });
});
