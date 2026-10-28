import type Card from '../Card';
import { kiloValue } from './kilo';
import type { KiloDeMerdeRoundOutcome } from './KiloDeMerdeState';

/** Which of the round lines of the table a draw is: the texts are chosen by this code. */
export type RoundLineCode = 'LAST_DRAWS' | 'FALSE_SLAP' | 'FLUSH' | 'NOTHING' | 'VOIDED';

/** One line of the round banner: the code and the parameters its text fills in (`{name}`, `{card}`, `{kg}`). */
export interface RoundLine {
  code: RoundLineCode;
  /** The drawer; null for a line about nobody. */
  seat: number | null;
  /** The drawn card; null for a line about nobody. */
  card: Card | null;
  /** The kilos that card weighs (0 for a flush). */
  kg: number;
}

const NOTHING: RoundLine = { code: 'NOTHING', seat: null, card: null, kg: 0 };

/**
 * What the banner says once a round is over, from the outcome alone: one line per draw (a race that times out has
 * several drawers); a drawn 7 is a flush; a void round (a forfeit) says so; a round with no card taken says that
 * nobody took kilos. Empty without an outcome.
 */
export function roundSummary(outcome: KiloDeMerdeRoundOutcome | null): RoundLine[] {
  if (!outcome) return [];
  if (outcome.kind === 'VOIDED') return [{ ...NOTHING, code: 'VOIDED' }];
  const lines = outcome.draws.flatMap((draw): RoundLine[] => {
    if (draw.card === null) return [];
    if (draw.flushed) return [{ code: 'FLUSH', seat: draw.seat, card: draw.card, kg: 0 }];
    return [{
      code: outcome.kind === 'FALSE_SLAP' ? 'FALSE_SLAP' : 'LAST_DRAWS',
      seat: draw.seat, card: draw.card, kg: kiloValue(draw.card),
    }];
  });
  return lines.length > 0 ? lines : [NOTHING];
}
