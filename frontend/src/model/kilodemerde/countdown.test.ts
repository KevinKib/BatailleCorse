import { describe, it, expect } from 'vitest';
import { countdown } from './countdown';

describe('countdown', () => {
  it('is null when there is no timer', () => {
    expect(countdown(null, 1_000)).toBeNull();
  });

  it('counts the whole seconds left, rounding up', () => {
    expect(countdown(16_000, 1_000)).toEqual({ remainingMs: 15_000, seconds: 15, fraction: null });
    expect(countdown(16_000, 1_001)).toEqual({ remainingMs: 14_999, seconds: 15, fraction: null });
    expect(countdown(16_000, 15_001)).toEqual({ remainingMs: 999, seconds: 1, fraction: null });
  });

  it('stops at zero once the time is over', () => {
    expect(countdown(16_000, 16_000)).toEqual({ remainingMs: 0, seconds: 0, fraction: null });
    expect(countdown(16_000, 99_000)).toEqual({ remainingMs: 0, seconds: 0, fraction: null });
  });

  it('gives the share of the total left for a ring', () => {
    expect(countdown(16_000, 1_000, 15_000)?.fraction).toBe(1);
    expect(countdown(16_000, 8_500, 15_000)?.fraction).toBeCloseTo(0.5);
    expect(countdown(16_000, 20_000, 15_000)?.fraction).toBe(0);
    // A clock ahead of the server cannot overfill the ring.
    expect(countdown(16_000, 0, 15_000)?.fraction).toBe(1);
  });
});
