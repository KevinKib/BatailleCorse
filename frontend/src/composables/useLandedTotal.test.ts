import { describe, it, expect, vi, afterEach } from 'vitest';
import { nextTick, ref } from 'vue';
import { useLandedTotal } from './useLandedTotal';
import type { NinetyNineLogEntry } from '../model/ninetynine/eventLog';

const play = (id: number, before: number, after: number): NinetyNineLogEntry => ({
  id, eventType: 'PLAY', seat: 1, card: { name: 'HEART_5', rank: '5', suit: 'HEART' } as never, aceValue: null,
  totalBefore: before, totalAfter: after, reversed: false, hand: null, livesLeft: null, place: null, stockSize: null,
  round: 1, pileTopBefore: null,
});

function setup(start = 10) {
  const total = ref(start);
  const events = ref<NinetyNineLogEntry[]>([]);
  const landed = useLandedTotal(() => total.value, () => events.value);
  return { total, events, ...landed };
}

describe('useLandedTotal', () => {
  afterEach(() => vi.useRealTimers());

  it('givenAPlayStartingLater_thenTheOldTotalStaysUntilItsOwnLanding', async () => {
    vi.useFakeTimers();
    const { total, events, shown, landsIn } = setup();
    total.value = 15;
    events.value = [play(1, 10, 15)];
    await nextTick();
    expect(shown.value).toBe(10);
    vi.advanceTimersByTime(5000);
    expect(shown.value).toBe(10); // the play has not been told to land: nothing moves
    landsIn(events.value[0], 950);
    vi.advanceTimersByTime(949);
    expect(shown.value).toBe(10);
    vi.advanceTimersByTime(1);
    expect(shown.value).toBe(15);
  });

  it('givenThreePlaysArrivingAtOnce_thenTheTotalFollowsTheQueueInOrder', async () => {
    vi.useFakeTimers();
    const { total, events, shown, landsIn } = setup(10);
    total.value = 40;
    events.value = [play(3, 30, 40), play(2, 20, 30), play(1, 10, 20)];
    await nextTick();
    expect(shown.value).toBe(10);
    // The queue starts them one after the other: they land at 900, 1900 and 2900 ms.
    landsIn(events.value[2], 900);
    landsIn(events.value[1], 1900);
    landsIn(events.value[0], 2900);
    vi.advanceTimersByTime(899);
    expect(shown.value).toBe(10);
    vi.advanceTimersByTime(1);
    expect(shown.value).toBe(20);
    vi.advanceTimersByTime(1000);
    expect(shown.value).toBe(30);
    vi.advanceTimersByTime(1000);
    expect(shown.value).toBe(40);
  });

  it('givenTheQueueTellsBeforeTheWatcherRuns_thenTheTotalStillWaits', async () => {
    vi.useFakeTimers();
    const { total, events, shown, landsIn } = setup();
    const entry = play(1, 10, 15);
    landsIn(entry, 500);
    total.value = 15;
    events.value = [entry];
    await nextTick();
    expect(shown.value).toBe(10);
    vi.advanceTimersByTime(500);
    expect(shown.value).toBe(15);
  });

  it('givenInstant_thenTheTotalIsShownAtOnceButStillInOrder', async () => {
    const { total, events, shown, landsIn } = setup(10);
    total.value = 30;
    events.value = [play(2, 20, 30), play(1, 10, 20)];
    await nextTick();
    landsIn(events.value[1], 0);
    expect(shown.value).toBe(20);
    landsIn(events.value[0], 0);
    expect(shown.value).toBe(30);
  });

  it('givenANewRoundWhileHolding_thenTheResetShowsAtOnce', async () => {
    vi.useFakeTimers();
    const { total, events, shown, landsIn } = setup();
    total.value = 15;
    events.value = [play(1, 10, 15)];
    await nextTick();
    landsIn(events.value[0], 950);
    total.value = 0;
    events.value = [{ ...play(2, 0, 0), eventType: 'ROUND_START', card: null, seat: null }, ...events.value];
    await nextTick();
    expect(shown.value).toBe(0);
    vi.advanceTimersByTime(2000);
    expect(shown.value).toBe(0);
  });

  it('givenAStateChangeWithoutAPlay_thenTheTotalIsShownAtOnce', async () => {
    const { total, shown } = setup();
    total.value = 33;
    await nextTick();
    expect(shown.value).toBe(33);
  });
});
