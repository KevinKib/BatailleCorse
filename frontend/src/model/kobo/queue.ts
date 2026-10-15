import { TEMPO } from './tempo';

export interface Admission {
  /** Wait this long before playing the event. */
  startInMs: number;
  /** False: the backlog is full, show the markers of the event at once and skip its flights. */
  motion: boolean;
}

/**
 * Plays the events of a burst one after the other (each starts when the previous flight is over) while keeping the
 * table from lagging behind the game: past the bounds of `TEMPO.queue`, an event is not queued for motion. Pure
 * apart from the clock it is given.
 */
export function createTempoQueue(now: () => number) {
  let busyUntil = 0;
  let starts: number[] = [];
  return {
    admit(spanMs: number): Admission {
      const t = now();
      starts = starts.filter(start => start > t);
      const startInMs = Math.max(0, busyUntil - t);
      if (starts.length >= TEMPO.queue.maxQueued || startInMs > TEMPO.queue.maxLagMs) {
        return { startInMs: 0, motion: false };
      }
      busyUntil = t + startInMs + spanMs;
      if (startInMs > 0) starts.push(t + startInMs);
      return { startInMs, motion: true };
    },
  };
}
