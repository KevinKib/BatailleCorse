import type { LegTempo } from '../../animation';

/**
 * The tempo of Blackjack's animations, in one place, in milliseconds at the Normal speed. Purely visual: no rule of
 * the game depends on it.
 */
export const BLACKJACK_TEMPO = {
  /** The deal: one card per leg, leaving this far apart (10 cards at 6 seats are about 2.5 s). */
  deal: { holdBeforeMs: 0, flightMs: 380, holdAfterMs: 100, staggerMs: 150 },
  /** A hit or the single card of a double. */
  hit: { holdBeforeMs: 0, flightMs: 450, holdAfterMs: 250 } satisfies LegTempo,
  /** A double reads the same as a hit. */
  double: { holdBeforeMs: 0, flightMs: 450, holdAfterMs: 300 } satisfies LegTempo,
  /** A split: the second card moves to its new hand, then the first hand is dealt a card. */
  split: { holdBeforeMs: 0, flightMs: 400, holdAfterMs: 150 } satisfies LegTempo,
  /** The hole card turning over. */
  reveal: { holdBeforeMs: 100, flightMs: 500, holdAfterMs: 300 } satisfies LegTempo,
  /** The dealer's drawn cards, after the reveal, this far apart. */
  dealerDraw: { holdBeforeMs: 200, flightMs: 450, holdAfterMs: 250, staggerMs: 650 },
  /** The burst bounds, as the other tables. */
  queue: { maxQueued: 4, maxLagMs: 3500 },
} as const;

/** No single movement (flight plus hold after) may last longer than this. */
export const MOTION_MAX_MS = 2600;
