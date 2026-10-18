import { describe, expect, it } from 'vitest';
import { createTempoQueue } from './queue';

describe('createTempoQueue (shared)', () => {
  const at = (t: { now: number }) => () => t.now;
  const bounds = { maxQueued: 2, maxLagMs: 1000 };

  it('givenAnIdleTable_thenAnEventStartsAtOnce', () => {
    const queue = createTempoQueue(at({ now: 50 }), bounds);
    expect(queue.admit(400)).toEqual({ startInMs: 0, motion: true });
  });

  it('givenAnEventStillFlying_thenTheNextOneWaitsForItsFlight', () => {
    const t = { now: 0 };
    const queue = createTempoQueue(at(t), bounds);
    queue.admit(600);
    t.now = 100;
    expect(queue.admit(300)).toEqual({ startInMs: 500, motion: true });
  });

  it('givenTheBoundsItIsGiven_thenTheBacklogIsCappedByThem', () => {
    const t = { now: 0 };
    const queue = createTempoQueue(at(t), bounds);
    queue.admit(300);
    queue.admit(300);
    queue.admit(300);
    expect(queue.admit(300)).toEqual({ startInMs: 0, motion: false });
  });

  it('givenAnEventThatWouldStartLaterThanTheLagBound_thenItOnlyGetsItsMarkers', () => {
    const queue = createTempoQueue(at({ now: 0 }), bounds);
    queue.admit(1500);
    expect(queue.admit(300)).toEqual({ startInMs: 0, motion: false });
  });
});
