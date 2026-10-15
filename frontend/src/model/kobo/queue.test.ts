import { describe, expect, it } from 'vitest';
import { createTempoQueue } from './queue';
import { TEMPO } from './tempo';

describe('createTempoQueue', () => {
  const at = (t: { now: number }) => () => t.now;

  it('givenAnIdleTable_thenAnEventStartsAtOnce', () => {
    const t = { now: 1000 };
    const queue = createTempoQueue(at(t));
    expect(queue.admit(1200)).toEqual({ startInMs: 0, motion: true });
  });

  it('givenAnEventStillFlying_thenTheNextOneStartsWhenItsFlightIsOver', () => {
    const t = { now: 0 };
    const queue = createTempoQueue(at(t));
    queue.admit(1200);
    t.now = 300;
    expect(queue.admit(1000)).toEqual({ startInMs: 900, motion: true });
    t.now = 400;
    expect(queue.admit(500).startInMs).toBe(1800);
  });

  it('givenTheTableHasBeenQuietLongEnough_thenTheQueueIsEmptyAgain', () => {
    const t = { now: 0 };
    const queue = createTempoQueue(at(t));
    queue.admit(1200);
    t.now = 5000;
    expect(queue.admit(1200)).toEqual({ startInMs: 0, motion: true });
  });

  it('givenABurstLongerThanTheCap_thenTheExtraEventsOnlyGetTheirMarkersAtOnce', () => {
    const t = { now: 0 };
    const queue = createTempoQueue(at(t));
    const results = Array.from({ length: TEMPO.queue.maxQueued + 4 }, () => queue.admit(300));
    const withMotion = results.filter(r => r.motion);
    expect(withMotion.length).toBeLessThanOrEqual(TEMPO.queue.maxQueued + 1);
    expect(results.at(-1)).toEqual({ startInMs: 0, motion: false });
    for (const r of withMotion) expect(r.startInMs).toBeLessThanOrEqual(TEMPO.queue.maxLagMs);
  });

  it('givenAnEventThatWouldStartTooLate_thenItIsNotQueuedForMotion', () => {
    const t = { now: 0 };
    const queue = createTempoQueue(at(t));
    queue.admit(TEMPO.queue.maxLagMs + 500);
    expect(queue.admit(300)).toEqual({ startInMs: 0, motion: false });
  });

  it('neverLagsBehindTheGameByMoreThanTheCap', () => {
    const t = { now: 0 };
    const queue = createTempoQueue(at(t));
    for (let i = 0; i < 50; i++) {
      const r = queue.admit(1200);
      expect(r.startInMs).toBeLessThanOrEqual(TEMPO.queue.maxLagMs);
      t.now += 100;
    }
  });
});
