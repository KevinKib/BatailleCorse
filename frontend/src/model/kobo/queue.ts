import { TEMPO } from './tempo';
import { createTempoQueue as createSharedTempoQueue } from '../../animation';

export type { Admission } from '../../animation';

/**
 * Kobo's queue of events: the shared table queue (`animation/core/tempoQueue.ts`) within the bounds of `TEMPO.queue`. Plays
 * the events of a burst one after the other while keeping the table from lagging behind the game. Pure apart from
 * the clock it is given.
 */
export function createTempoQueue(now: () => number) {
  return createSharedTempoQueue(now, TEMPO.queue);
}
