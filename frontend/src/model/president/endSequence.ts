import { PRESIDENT_TEMPO } from './tempo';
import type { PresidentState } from './PresidentState';

/**
 * How the game ended, for the screen that sequences it:
 * `round` the round is scored and the table waits for the ready checks; `final` the match ended with that round
 * (its sheet comes first, the final scores after it); `abandoned` the match ended without a round to show (a forfeit);
 * `none` the game goes on.
 */
export type EndKind = 'none' | 'round' | 'final' | 'abandoned';

export function endKind(game: PresidentState): EndKind {
  if (game.phase === 'ROUND_OVER') return 'round';
  if (game.phase !== 'FINISHED' && game.outcome.status !== 'FINISHED') return 'none';
  const forfeited = game.outcome.status === 'FINISHED' && game.outcome.reason === 'FORFEIT';
  return !forfeited && game.lastRound?.round === game.round ? 'final' : 'abandoned';
}

/** The waits before the end of a round shows, scaled by the speed; a still table (Instant, reduced motion) has none. */
export function endTimings(factor: number, still: boolean): { graceMs: number; settleMs: number } {
  if (still) return { graceMs: 0, settleMs: 0 };
  return { graceMs: PRESIDENT_TEMPO.roundEnd.graceMs, settleMs: Math.round(PRESIDENT_TEMPO.roundEnd.settleMs * factor) };
}
