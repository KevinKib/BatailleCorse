import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useInFlightCounts } from './useInFlightCounts';

describe('useInFlightCounts', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it('givenCardsFlyingFromASeat_thenTheSeatCountsThemUntilTheyLand', () => {
    const flights = useInFlightCounts();
    flights.add(1, 2, 800);
    expect(flights.shown(1, 5)).toBe(7);
    expect(flights.shown(2, 5)).toBe(5);
    vi.advanceTimersByTime(800);
    expect(flights.shown(1, 5)).toBe(5);
  });

  it('givenTwoPlaysInFlight_thenBothCountUntilEachLands', () => {
    const flights = useInFlightCounts();
    flights.add(1, 1, 500);
    flights.add(1, 2, 1000);
    expect(flights.shown(1, 3)).toBe(6);
    vi.advanceTimersByTime(500);
    expect(flights.shown(1, 3)).toBe(5);
    vi.advanceTimersByTime(500);
    expect(flights.shown(1, 3)).toBe(3);
  });

  it('givenAFlightQueuedFarAhead_thenTheCountCatchesUpWithTheStateAfterTheLongestLag', () => {
    const flights = useInFlightCounts({ maxLagMs: 2000 });
    flights.add(1, 2, 5000);
    expect(flights.shown(1, 0)).toBe(2);
    vi.advanceTimersByTime(2000);
    expect(flights.shown(1, 0)).toBe(0);
  });

  it('givenCancel_thenNothingIsInFlight', () => {
    const flights = useInFlightCounts();
    flights.add(1, 2, 800);
    flights.cancel();
    expect(flights.shown(1, 5)).toBe(5);
  });
  it('givenCardsFlyingToASeat_thenTheSeatShowsThemOnlyOnceTheyLand', () => {
    const flights = useInFlightCounts();
    flights.add(2, -2, 800);
    expect(flights.shown(2, 10)).toBe(8);
    flights.add(2, 0, 800);
    vi.advanceTimersByTime(800);
    expect(flights.shown(2, 10)).toBe(10);
  });

  it('givenAGiverAndAReceiver_thenEachCountFollowsItsOwnFlight', () => {
    const flights = useInFlightCounts();
    flights.add(1, 2, 800);
    flights.add(2, -2, 800);
    expect([flights.shown(1, 6), flights.shown(2, 10)]).toEqual([8, 8]);
    vi.advanceTimersByTime(800);
    expect([flights.shown(1, 6), flights.shown(2, 10)]).toEqual([6, 10]);
  });
});
