import type { PresidentTrick } from './PresidentState';

/** The four cards of a rank: the trick closes when all four have been played in a row. */
const SUIT_COUNT = 4;

/**
 * The "N or nothing" state of equal play: the rank every next play is bound to, and how many of its four cards the chain
 * has used. Null when the trick is not bound. Pure; the server's trick (`constraint`, `requiredRank`, `chain`) is the
 * only source.
 */
export interface Binding {
  rank: string;
  /** Cards of the rank played in a row (at most four). */
  played: number;
  total: 4;
  /** Cards that close the trick: the fourth one, played in turn or completed out of turn, ends it. */
  left: number;
}

export function bindingOf(trick: PresidentTrick): Binding | null {
  if (trick.constraint !== 'MATCH_OR_PASS' || !trick.requiredRank) return null;
  const used = trick.chain ? trick.chain.cards.length : (trick.requiredCount ?? 1);
  const played = Math.min(SUIT_COUNT, used);
  return { rank: trick.requiredRank, played, total: SUIT_COUNT, left: SUIT_COUNT - played };
}
