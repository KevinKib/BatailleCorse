import type { LegTempo } from '../../animation';

/**
 * The tempo of Kilo de merde's animations, in one place, in milliseconds at the Normal speed. Purely visual: no rule
 * of the game depends on it.
 */
export const KILO_DE_MERDE_TEMPO = {
  /** A pass step: every seat's card flies to its left neighbour at once. */
  step: { holdBeforeMs: 0, flightMs: 350, holdAfterMs: 150 } satisfies LegTempo,
  /** A kilo card flying from the pile to the drawer. */
  draw: { holdBeforeMs: 100, flightMs: 600, holdAfterMs: 400 } satisfies LegTempo,
  /** The drawer's cards flying back into the pile; at most `maxFlights` of them, `staggerMs` apart. */
  flush: { holdBeforeMs: 0, flightMs: 700, holdAfterMs: 200, staggerMs: 70, maxFlights: 6 },
  /** The deal of a round: one pack per seat, leaving this far apart. */
  deal: { holdBeforeMs: 0, flightMs: 500, holdAfterMs: 150, staggerMs: 70 },
  /** How long the glow stays on the card I received. */
  receivedGlowMs: 1600,
  /** The burst bounds, as the other tables. */
  queue: { maxQueued: 4, maxLagMs: 3500 },
} as const;

/** No single movement (hold before, flight, hold after) may last longer than this. */
export const MOTION_MAX_MS = 2600;
