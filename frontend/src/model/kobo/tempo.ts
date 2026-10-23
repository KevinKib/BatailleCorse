/**
 * The tempo of Kobo's animations, in one place: every duration and hold time of the travelling cards, the markers
 * and the queue is read from here. Tune the feel of the table by editing these numbers only.
 *
 * A move reads as: hold (the copy sits on its source) -> flight -> hold after landing (the copy stays on its
 * destination). Everything is in milliseconds. Purely visual: no rule of the game depends on it.
 */
import type { LegTempo } from '../../animation';

export const TEMPO = {
  /** The moves of other seats: the hard ones to follow, hence the slowest. */
  opponent: { holdBeforeMs: 250, flightMs: 1000, holdAfterMs: 450 } satisfies LegTempo,
  /** My own moves: I know what I just did. */
  own: { holdBeforeMs: 150, flightMs: 700, holdAfterMs: 300 } satisfies LegTempo,
  /** A look at a card (out and back) by another seat. */
  glance: { holdBeforeMs: 250, flightMs: 1200, holdAfterMs: 300 } satisfies LegTempo,
  /** Every card of the table turns over at the end of a round. */
  reveal: { holdBeforeMs: 0, flightMs: 700, holdAfterMs: 0 } satisfies LegTempo,
  /** The gift of a match leaves this long after the matched card, so the two moves read in order. */
  followMs: 450,
  /** How long the ring stays on every card a move touched (longer when the move itself lasts longer). */
  markMs: 2000,
  /** The same ring under reduced motion: a plain outline, nothing moves. */
  markReducedMs: 1500,
  /** Events that arrive in a burst are played one after the other, within these bounds. */
  queue: {
    /** More events than this waiting to start: the extra ones only get their markers, at once. */
    maxQueued: 4,
    /** An event that would start later than this after it arrived only gets its markers, at once. */
    maxLagMs: 3500,
  },
} as const;

/** No single movement (hold before, flight, hold after) may last longer than this. */
export const MOTION_MAX_MS = 2600;
