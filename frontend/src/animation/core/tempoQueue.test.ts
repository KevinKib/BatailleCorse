import { describe, expect, it } from 'vitest';
import { createTempoQueue } from './tempoQueue';

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

  it('givenAnEventStillFlying_thenTheBacklogIsWhatIsLeftOfIt_andZeroOnceItIsOver', () => {
    const t = { now: 0 };
    const queue = createTempoQueue(at(t), bounds);
    expect(queue.backlogMs()).toBe(0);
    queue.admit(600);
    t.now = 200;
    expect(queue.backlogMs()).toBe(400);
    queue.admit(300);
    expect(queue.backlogMs()).toBe(700);
    t.now = 5000;
    expect(queue.backlogMs()).toBe(0);
  });

  // The cases of Kobo's own queue test (its queue module is gone), with Kobo's bounds written out.
  describe('with the bounds of a four-event, 3.5 second table', () => {
    const wide = { maxQueued: 4, maxLagMs: 3500 };

    it('givenAnEventStillFlying_thenTheNextOneStartsWhenItsFlightIsOver', () => {
      const t = { now: 0 };
      const queue = createTempoQueue(at(t), wide);
      queue.admit(1200);
      t.now = 300;
      expect(queue.admit(1000)).toEqual({ startInMs: 900, motion: true });
      t.now = 400;
      expect(queue.admit(500).startInMs).toBe(1800);
    });

    it('givenTheTableHasBeenQuietLongEnough_thenTheQueueIsEmptyAgain', () => {
      const t = { now: 0 };
      const queue = createTempoQueue(at(t), wide);
      queue.admit(1200);
      t.now = 5000;
      expect(queue.admit(1200)).toEqual({ startInMs: 0, motion: true });
    });

    it('givenABurstLongerThanTheCap_thenTheExtraEventsOnlyGetTheirMarkersAtOnce', () => {
      const queue = createTempoQueue(at({ now: 0 }), wide);
      const results = Array.from({ length: wide.maxQueued + 4 }, () => queue.admit(300));
      const withMotion = results.filter(r => r.motion);
      expect(withMotion.length).toBeLessThanOrEqual(wide.maxQueued + 1);
      expect(results.at(-1)).toEqual({ startInMs: 0, motion: false });
      for (const r of withMotion) expect(r.startInMs).toBeLessThanOrEqual(wide.maxLagMs);
    });

    it('givenAnEventThatWouldStartTooLate_thenItIsNotQueuedForMotion', () => {
      const queue = createTempoQueue(at({ now: 0 }), wide);
      queue.admit(wide.maxLagMs + 500);
      expect(queue.admit(300)).toEqual({ startInMs: 0, motion: false });
    });

    it('neverLagsBehindTheGameByMoreThanTheCap', () => {
      const t = { now: 0 };
      const queue = createTempoQueue(at(t), wide);
      for (let i = 0; i < 50; i++) {
        expect(queue.admit(1200).startInMs).toBeLessThanOrEqual(wide.maxLagMs);
        t.now += 100;
      }
    });
  });
});
