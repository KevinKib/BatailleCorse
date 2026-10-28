import type { LegTempo } from '../../animation';

/**
 * The tempo of 99's animations, in one place. A play reads as: hold -> flight to the pile -> hold; the replacement
 * card follows from the stock. Everything is in milliseconds, at the Normal speed. Purely visual: no rule of the
 * game depends on it.
 */
export const NINETY_NINE_TEMPO = {
  /** Another seat's play: the hard one to follow. */
  opponent: { holdBeforeMs: 200, flightMs: 750, holdAfterMs: 350 } satisfies LegTempo,
  /** My own play. */
  own: { holdBeforeMs: 100, flightMs: 500, holdAfterMs: 250 } satisfies LegTempo,
  /** The replacement card leaves the stock this long after the play started. */
  drawFollowMs: 350,
  /** The pile going back to the stock. */
  reshuffle: { holdBeforeMs: 100, flightMs: 700, holdAfterMs: 200 } satisfies LegTempo,
  /** The ring on a seat that is stuck. */
  bust: { holdBeforeMs: 0, flightMs: 900, holdAfterMs: 0 } satisfies LegTempo,
  /** The bust overlay waits this long after the card that busted was played, so the table shows it first. */
  bustRevealMs: 1500,
  /** The deal of a round: one pack per live seat, leaving this far apart. */
  deal: { holdBeforeMs: 0, flightMs: 500, holdAfterMs: 150, staggerMs: 70 },
  /** How long the rings stay on the places a move touched. */
  markMs: 1600,
  /** The same rings when nothing moves (reduced motion, Instant). */
  markReducedMs: 1500,
  /** The burst bounds, as the other tables. */
  queue: { maxQueued: 4, maxLagMs: 3500 },
} as const;

/** No single movement (hold before, flight, hold after) may last longer than this. */
export const MOTION_MAX_MS = 2600;
