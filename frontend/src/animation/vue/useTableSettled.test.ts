import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useTableSettled } from './useTableSettled';

describe('useTableSettled', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  function settle(source: Partial<Parameters<typeof useTableSettled>[0]> = {}) {
    const state = { backlogMs: 0, busy: false, settleMs: 0 };
    const settled = useTableSettled({
      backlogMs: () => state.backlogMs, busy: () => state.busy, settleMs: () => state.settleMs, ...source,
    });
    let done = false;
    const wait = () => { done = false; void settled.whenSettled().then(() => { done = true; }); };
    return { state, settled, wait, isDone: () => done };
  }

  it('givenAnIdleTable_thenItIsSettledAtOnce', async () => {
    const t = settle();
    t.wait();
    await vi.advanceTimersByTimeAsync(0);
    expect(t.isDone()).toBe(true);
  });

  it('givenAQueueWithABacklog_thenItWaitsForTheBacklog', async () => {
    const t = settle();
    t.state.backlogMs = 2000;
    t.wait();
    await vi.advanceTimersByTimeAsync(1999);
    expect(t.isDone()).toBe(false);
    t.state.backlogMs = 0;
    await vi.advanceTimersByTimeAsync(1);
    expect(t.isDone()).toBe(true);
  });

  it('givenAHeldPicture_thenItIsLookedAtAgainEveryHundredMilliseconds', async () => {
    const t = settle();
    t.state.busy = true;
    t.wait();
    await vi.advanceTimersByTimeAsync(450);
    expect(t.isDone()).toBe(false);
    t.state.busy = false;
    await vi.advanceTimersByTimeAsync(100);
    expect(t.isDone()).toBe(true);
  });

  it('givenARestTime_thenTheTableMustStayQuietForThatLong', async () => {
    const t = settle();
    t.state.settleMs = 500;
    t.wait();
    await vi.advanceTimersByTimeAsync(499);
    expect(t.isDone()).toBe(false);
    await vi.advanceTimersByTimeAsync(1);
    expect(t.isDone()).toBe(true);
  });

  it('givenSomethingNewDuringTheRest_thenItWaitsForThatAfterwards', async () => {
    const t = settle();
    t.state.settleMs = 500;
    t.wait();
    await vi.advanceTimersByTimeAsync(300);
    t.state.backlogMs = 1000;
    await vi.advanceTimersByTimeAsync(200);
    expect(t.isDone()).toBe(false);
    t.state.backlogMs = 0;
    // The backlog read at the end of the first rest is waited out, then the rest starts over.
    await vi.advanceTimersByTimeAsync(1000);
    expect(t.isDone()).toBe(false);
    await vi.advanceTimersByTimeAsync(500);
    expect(t.isDone()).toBe(true);
  });

  it('givenNoBusyFunction_thenOnlyTheBacklogMatters', async () => {
    const t = settle({ busy: undefined });
    t.wait();
    await vi.advanceTimersByTimeAsync(0);
    expect(t.isDone()).toBe(true);
  });

  it('givenCancel_thenThePendingWaitNeverEnds', async () => {
    const t = settle();
    t.state.backlogMs = 1000;
    t.wait();
    t.settled.cancel();
    await vi.advanceTimersByTimeAsync(5000);
    expect(t.isDone()).toBe(false);
  });

  it('givenACallback_thenItIsToldInTheTimerThatSettlesTheTable_beforeAnyPromiseCallback', () => {
    const t = settle();
    t.state.settleMs = 500;
    const then = vi.fn();
    void t.settled.whenSettled(then);
    vi.advanceTimersByTime(499);
    expect(then).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(then).toHaveBeenCalledTimes(1);
  });

  it('givenACallbackOnAnIdleTableWithNoRest_thenItIsToldAtOnce', () => {
    const then = vi.fn();
    void settle().settled.whenSettled(then);
    expect(then).toHaveBeenCalledTimes(1);
  });

  it('givenACancelledWaitWithACallback_thenItIsNeverTold', () => {
    const t = settle();
    t.state.backlogMs = 1000;
    const then = vi.fn();
    void t.settled.whenSettled(then);
    t.settled.cancel();
    vi.advanceTimersByTime(5000);
    expect(then).not.toHaveBeenCalled();
  });

  it('givenANewWait_thenTheOldOneIsReplaced', async () => {
    const t = settle();
    t.state.backlogMs = 1000;
    let first = false;
    void t.settled.whenSettled().then(() => { first = true; });
    t.wait();
    t.state.backlogMs = 0;
    await vi.advanceTimersByTimeAsync(2000);
    expect(first).toBe(false);
    expect(t.isDone()).toBe(true);
  });
});
