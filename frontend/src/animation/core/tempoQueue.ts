import type { QueueBounds } from './tempo';

export interface Admission {
  /** Wait this long before playing the event. */
  startInMs: number;
  /** False: the backlog is full, show the markers of the event at once and skip its flights. */
  motion: boolean;
}

/**
 * Plays the events of a burst one after the other (each starts when the previous flight is over) while keeping the
 * table from lagging behind the game: past the bounds, an event is not queued for motion. Shared by every table that
 * animates. Pure apart from the clock it is given.
 */
export function createTempoQueue(now: () => number, bounds: QueueBounds) {
  let busyUntil = 0;
  let starts: number[] = [];
  return {
    admit(spanMs: number): Admission {
      const t = now();
      starts = starts.filter(start => start > t);
      const startInMs = Math.max(0, busyUntil - t);
      if (starts.length >= bounds.maxQueued || startInMs > bounds.maxLagMs) {
        return { startInMs: 0, motion: false };
      }
      busyUntil = t + startInMs + spanMs;
      if (startInMs > 0) starts.push(t + startInMs);
      return { startInMs, motion: true };
    },
    /** How long until the events admitted so far are all over (0 when the table is idle). */
    backlogMs(): number {
      return Math.max(0, busyUntil - now());
    },
  };
}
