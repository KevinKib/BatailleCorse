import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { useDisplayedState } from './useDisplayedState';

describe('useDisplayedState', () => {
  beforeEach(() => { vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] }); });
  afterEach(() => { vi.useRealTimers(); });

  it('showsAMoveOnlyWhenItsPictureIsOver', () => {
    const display = useDisplayedState<string, number>({ maxLagMs: 10_000 });
    display.show('s0');
    const id = display.reserve('s1', 1);
    display.revealIn(id, 800);
    vi.advanceTimersByTime(799);
    expect(display.displayed.value?.state).toBe('s0');
    vi.advanceTimersByTime(1);
    expect(display.displayed.value).toEqual({ state: 's1', entry: 1 });
  });

  it('revealsAtOnceForNoDelay', () => {
    const display = useDisplayedState<string, number>({ maxLagMs: 10_000 });
    const id = display.reserve('s1', 1);
    display.revealIn(id, 0);
    expect(display.displayed.value?.state).toBe('s1');
  });

  it('tellsEachMoveWhenItIsShown_inOrder', () => {
    const revealed: number[] = [];
    const display = useDisplayedState<string, number>({ maxLagMs: 10_000, onReveal: entry => revealed.push(entry) });
    const a = display.reserve('a', 1);
    const b = display.reserve('b', 2);
    display.revealIn(b, 100);
    display.revealIn(a, 500);
    display.show('c');
    vi.advanceTimersByTime(100);
    expect(revealed).toEqual([]);
    vi.advanceTimersByTime(400);
    expect(revealed).toEqual([1, 2]);
    expect(display.displayed.value).toEqual({ state: 'c', entry: 2 });
  });

  it('neverFallsFurtherBehindThanItsBound_evenIfAMoveIsNeverTimed', () => {
    const display = useDisplayedState<string, number>({ maxLagMs: 3000 });
    display.show('s0');
    display.reserve('s1', 1);
    vi.advanceTimersByTime(2999);
    expect(display.displayed.value?.state).toBe('s0');
    vi.advanceTimersByTime(1);
    expect(display.displayed.value?.state).toBe('s1');
  });

  it('flushAndResetCancelWhatWaits', () => {
    const display = useDisplayedState<string, number>({ maxLagMs: 3000 });
    display.show('s0');
    display.revealIn(display.reserve('s1', 1), 1000);
    display.flush();
    expect(display.displayed.value?.state).toBe('s1');
    display.revealIn(display.reserve('s2', 2), 1000);
    display.reset(null);
    vi.advanceTimersByTime(5000);
    expect(display.displayed.value).toBeNull();
  });
});
