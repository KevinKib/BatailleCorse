import type { LegTempo } from '../kobo/tempo';

/**
 * The tempo of President's animations (option B of the animations canvas, spec §11 decision 7), in one place. Shorter
 * than Kobo's: a trick often holds 5 to 10 moves and the bots play every 0.7 to 1.3 s. At the Normal speed; Fast
 * halves every duration. Purely visual: no rule of the game depends on it.
 */
export const PRESIDENT_TEMPO = {
  /** Another seat's play. */
  opponent: { holdBeforeMs: 180, flightMs: 700, holdAfterMs: 250 } satisfies LegTempo,
  /** My own play. */
  own: { holdBeforeMs: 100, flightMs: 480, holdAfterMs: 250 } satisfies LegTempo,
  /** The cards of one play leave this far apart, so a pair reads as two cards. */
  cardStaggerMs: 60,
  /** How long the rings stay on the places a move touched. */
  markMs: 1600,
  /** The same rings when nothing moves (reduced motion, Instant): still outlines. */
  markReducedMs: 1500,
  /** How long a seat bubble ("Pass", "Out on a 2", "Four completed") stays up. */
  bubbleMs: 1400,
  /** Bubbles that come in a burst pop this far apart (they are not queued with the flights). */
  bubbleGapMs: 200,
  /** A seat's card count waits for its flights to land, but never longer than this behind the state. */
  countLagMaxMs: 2000,
  /** The sweep of a closed trick toward its winner (chunk 3). */
  sweep: {
    /** The closing badge sits on the pile this long, after the closing play has landed, before the cards leave. */
    badgeHoldMs: 800,
    /** One card's flight to the winner's seat. */
    flightMs: 620,
    /** A copy stays this long on the seat it reached, so that its landing is seen. */
    landHoldMs: 40,
    /** The cards leave this far apart. */
    staggerMs: 90,
    /** At most this many cards sweep (the latest ones): the layer stays light on a very long trick. */
    maxCards: 8,
  },
  /** The burst bounds, as Kobo's. */
  queue: { maxQueued: 4, maxLagMs: 3500 },
} as const;
