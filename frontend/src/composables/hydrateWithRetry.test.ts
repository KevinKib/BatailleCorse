import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { HydrateResult } from '../application/SeatGameSession';
import { HYDRATE_RETRY_DELAYS_MS, hydrateWithRetry } from './hydrateWithRetry';

describe('hydrateWithRetry', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  const answering = (...results: HydrateResult[]) => {
    const queue = [...results];
    return vi.fn(async (): Promise<HydrateResult> => queue.shift() ?? 'unavailable');
  };

  it('asks once when the first answer is not unavailable', async () => {
    for (const result of ['ok', 'gone', 'lost'] as const) {
      const hydrate = answering(result);
      expect(await hydrateWithRetry(hydrate)).toBe(result);
      expect(hydrate).toHaveBeenCalledTimes(1);
    }
  });

  it('retries after 1 s, 2 s then 4 s, and stops at the first answer', async () => {
    expect(HYDRATE_RETRY_DELAYS_MS).toEqual([1000, 2000, 4000]);
    const hydrate = answering('unavailable', 'unavailable', 'ok');
    const done = hydrateWithRetry(hydrate);
    await vi.advanceTimersByTimeAsync(0);
    expect(hydrate).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(999);
    expect(hydrate).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(hydrate).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1999);
    expect(hydrate).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(1);
    expect(hydrate).toHaveBeenCalledTimes(3);
    expect(await done).toBe('ok');
    await vi.advanceTimersByTimeAsync(10_000);
    expect(hydrate).toHaveBeenCalledTimes(3);
  });

  it('gives up after four attempts and returns unavailable', async () => {
    const hydrate = answering();
    const done = hydrateWithRetry(hydrate);
    await vi.advanceTimersByTimeAsync(7000);
    expect(await done).toBe('unavailable');
    expect(hydrate).toHaveBeenCalledTimes(4);
    await vi.advanceTimersByTimeAsync(60_000);
    expect(hydrate).toHaveBeenCalledTimes(4);
  });

  it('stops retrying once cancelled', async () => {
    let gone = false;
    const hydrate = answering();
    const done = hydrateWithRetry(hydrate, () => gone);
    await vi.advanceTimersByTimeAsync(0);
    gone = true;
    await vi.advanceTimersByTimeAsync(10_000);
    expect(await done).toBe('unavailable');
    expect(hydrate).toHaveBeenCalledTimes(1);
  });
});
