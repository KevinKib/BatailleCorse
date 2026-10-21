import { describe, it, expect } from 'vitest';
import { createDisplayQueue } from './displayQueue';

describe('createDisplayQueue', () => {
  it('showsAStateAtOnceWhenNothingWaits', () => {
    const queue = createDisplayQueue<string, number>();
    expect(queue.current()).toBeNull();
    const shown = queue.show('s1');
    expect(shown).toEqual([{ state: 's1', entry: null }]);
    expect(queue.current()).toEqual({ state: 's1', entry: null });
  });

  it('keepsAReservedStateBackUntilItIsReady', () => {
    const queue = createDisplayQueue<string, number>({ state: 's0', entry: null });
    const id = queue.reserve('s1', 1);
    expect(queue.current()).toEqual({ state: 's0', entry: null });
    expect(queue.ready(id)).toEqual([{ state: 's1', entry: 1 }]);
    expect(queue.current()).toEqual({ state: 's1', entry: 1 });
  });

  it('neverLetsALaterStateOvertakeAnEarlierOne', () => {
    const queue = createDisplayQueue<string, number>({ state: 's0', entry: null });
    const seven = queue.reserve('after7', 1);
    const nine = queue.reserve('after9', 2);
    const nineAgain = queue.reserve('after9again', 3);
    // The last play is ready first (its timer was shorter): it still waits for the two before it.
    expect(queue.ready(nineAgain)).toEqual([]);
    expect(queue.ready(nine)).toEqual([]);
    expect(queue.current()?.state).toBe('s0');
    expect(queue.ready(seven).map(d => d.state)).toEqual(['after7', 'after9', 'after9again']);
    expect(queue.current()?.state).toBe('after9again');
  });

  it('queuesAStateWithoutAMoveBehindTheMovesStillWaiting', () => {
    const queue = createDisplayQueue<string, number>({ state: 's0', entry: null });
    const play = queue.reserve('afterPlay', 1);
    expect(queue.show('afterReady')).toEqual([]);
    expect(queue.current()?.state).toBe('s0');
    expect(queue.ready(play).map(d => d.state)).toEqual(['afterPlay', 'afterReady']);
  });

  it('keepsTheLatestMoveWhenAStateWithoutAMoveIsShown', () => {
    const queue = createDisplayQueue<string, number>({ state: 's0', entry: null });
    queue.ready(queue.reserve('afterPlay', 7));
    queue.show('afterReady');
    expect(queue.current()).toEqual({ state: 'afterReady', entry: 7 });
  });

  it('readyIsIdempotentAndIgnoresUnknownSlots', () => {
    const queue = createDisplayQueue<string, number>();
    const id = queue.reserve('s1', 1);
    queue.ready(id);
    expect(queue.ready(id)).toEqual([]);
    expect(queue.ready(999)).toEqual([]);
    expect(queue.current()?.state).toBe('s1');
  });

  it('flushShowsEverythingWaiting', () => {
    const queue = createDisplayQueue<string, number>({ state: 's0', entry: null });
    queue.reserve('s1', 1);
    queue.reserve('s2', 2);
    expect(queue.flush().map(d => d.state)).toEqual(['s1', 's2']);
    expect(queue.current()).toEqual({ state: 's2', entry: 2 });
    expect(queue.waiting()).toBe(0);
  });

  it('resetDropsWhatWaitsAndForgetsTheMoves', () => {
    const queue = createDisplayQueue<string, number>({ state: 's0', entry: 3 });
    const id = queue.reserve('s1', 1);
    queue.reset(null);
    expect(queue.current()).toBeNull();
    expect(queue.waiting()).toBe(0);
    expect(queue.ready(id)).toEqual([]);
    queue.show('s2');
    expect(queue.current()).toEqual({ state: 's2', entry: null });
  });
});
