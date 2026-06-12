import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ref, nextTick } from 'vue';
import { useGameDuration, formatDuration } from './useGameDuration';

describe('formatDuration', () => {
  it('givenUnderOneMinute_thenMmSs', () => {
    expect(formatDuration(5_000)).toBe('00:05');
  });

  it('givenMinutesAndSeconds_thenMmSs', () => {
    expect(formatDuration(65_000)).toBe('01:05');
  });

  it('givenOverOneHour_thenHMmSs', () => {
    expect(formatDuration(3_725_000)).toBe('1:02:05'); // 1h 2m 5s
  });

  it('givenZero_thenZeroZero', () => {
    expect(formatDuration(0)).toBe('00:00');
  });
});

describe('useGameDuration', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('givenInactive_thenStaysAtZero', async () => {
    const active = ref(false);
    const over = ref(false);
    const { formattedDuration } = useGameDuration(() => active.value, () => over.value);

    await nextTick();
    vi.advanceTimersByTime(10_000);

    expect(formattedDuration.value).toBe('00:00');
  });

  it('givenActive_thenCountsUp', async () => {
    const active = ref(true);
    const over = ref(false);
    const { formattedDuration } = useGameDuration(() => active.value, () => over.value);

    await nextTick();
    vi.advanceTimersByTime(65_000);

    expect(formattedDuration.value).toBe('01:05');
  });

  it('givenGameOver_thenFreezesAtFinalDuration', async () => {
    const active = ref(true);
    const over = ref(false);
    const { formattedDuration } = useGameDuration(() => active.value, () => over.value);

    await nextTick();
    vi.advanceTimersByTime(30_000);

    over.value = true;
    active.value = false; // active = !over in the real view
    await nextTick();

    vi.advanceTimersByTime(60_000); // time keeps passing...
    expect(formattedDuration.value).toBe('00:30'); // ...but the value is frozen
  });

  it('givenGameOver_thenStopsTheInterval', async () => {
    const active = ref(true);
    const over = ref(false);
    useGameDuration(() => active.value, () => over.value);

    await nextTick();
    expect(vi.getTimerCount()).toBe(1);

    over.value = true;
    active.value = false;
    await nextTick();

    expect(vi.getTimerCount()).toBe(0);
  });

  it('givenCancel_thenStopsIntervalAndWatch', async () => {
    const active = ref(true);
    const over = ref(false);
    const { cancel } = useGameDuration(() => active.value, () => over.value);

    await nextTick();
    cancel();

    expect(vi.getTimerCount()).toBe(0);
  });
});
