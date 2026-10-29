import { describe, it, expect, vi, afterEach } from 'vitest';
import { nextTick, ref } from 'vue';
import { useLandedTotal } from './useLandedTotal';
import type { NinetyNineLogEntry } from '../model/ninetynine/eventLog';

const play = (id: number, before: number, after: number): NinetyNineLogEntry => ({
  id, eventType: 'PLAY', seat: 1, card: { name: 'HEART_5', rank: '5', suit: 'HEART' } as never, aceValue: null,
  totalBefore: before, totalAfter: after, reversed: false, hand: null, livesLeft: null, place: null, stockSize: null,
  round: 1, pileTopBefore: null,
});

describe('useLandedTotal', () => {
  afterEach(() => vi.useRealTimers());

  it('givenAPlay_thenTheOldTotalStaysUntilTheCardHasLanded', async () => {
    vi.useFakeTimers();
    const total = ref(10);
    const events = ref<NinetyNineLogEntry[]>([]);
    const shown = useLandedTotal(() => total.value, () => events.value, () => 950);
    total.value = 15;
    events.value = [play(1, 10, 15)];
    await nextTick();
    expect(shown.value).toBe(10);
    vi.advanceTimersByTime(949);
    expect(shown.value).toBe(10);
    vi.advanceTimersByTime(1);
    expect(shown.value).toBe(15);
  });

  it('givenNoDelay_thenTheTotalIsShownAtOnce', async () => {
    const total = ref(10);
    const events = ref<NinetyNineLogEntry[]>([]);
    const shown = useLandedTotal(() => total.value, () => events.value, () => 0);
    total.value = 15;
    events.value = [play(1, 10, 15)];
    await nextTick();
    expect(shown.value).toBe(15);
  });

  it('givenANewRoundWhileHolding_thenTheResetShowsAtOnce', async () => {
    vi.useFakeTimers();
    const total = ref(10);
    const events = ref<NinetyNineLogEntry[]>([]);
    const shown = useLandedTotal(() => total.value, () => events.value, () => 950);
    total.value = 15;
    events.value = [play(1, 10, 15)];
    await nextTick();
    total.value = 0;
    await nextTick();
    expect(shown.value).toBe(0);
    vi.advanceTimersByTime(2000);
    expect(shown.value).toBe(0);
  });
});
