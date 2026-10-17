import type Card from '../Card';
import type { PresidentAction, PresidentPattern, PresidentState } from './PresidentState';
import { canonicalRank } from './ranks';

/**
 * Turns the cards the player tapped into the pattern they form, and says whether it is a legal play. There is no
 * rule here: legality is only "the server lists this pattern in `legalPlays`"; the reasons given for a refusal are
 * read from the same list and from the trick's top, so the 'Play' button hint can say what to change.
 */

/** The part of the state the matcher reads. */
export type SelectionContext = Pick<PresidentState, 'legalPlays' | 'availableActions' | 'trick'>;

/**
 * Why a selection cannot be played. `code` is a key for the locales; the other fields fill its text:
 * `counts` the numbers of cards the server accepts, `rank` and `count` the pattern to beat (`notHigher`) or, under
 * equal play, the only pattern allowed (`matchOnly`).
 */
export type SelectionRefusal =
  | { code: 'cannotPlay' }
  | { code: 'nothingSelected' }
  | { code: 'mixedRanks' }
  | { code: 'mustPass' }
  | { code: 'wrongCount'; counts: number[] }
  | { code: 'notHigher'; rank: string; count: number }
  | { code: 'matchOnly'; rank: string; count: number }
  | { code: 'notPlayable' };

export interface SelectionAnalysis {
  /** The set the cards form, null when empty or of several ranks. */
  pattern: PresidentPattern | null;
  legal: boolean;
  /** Null when legal. */
  reason: SelectionRefusal | null;
}

/** The set formed by the cards, or null for no card or several ranks. */
export function patternOf(cards: readonly Card[]): PresidentPattern | null {
  if (cards.length === 0) return null;
  const rank = cards[0].rank;
  if (cards.some(c => c.rank !== rank)) return null;
  return { kind: 'SET', rank, count: cards.length };
}

const samePattern = (a: PresidentPattern, b: PresidentPattern) =>
  a.kind === b.kind && canonicalRank(a.rank) === canonicalRank(b.rank) && a.count === b.count;

function canPlay(actions: readonly PresidentAction[]): boolean {
  return actions.includes('PLAY');
}

/** My turn with no legal play: the server lists PASS but not PLAY. */
function mustPassNow(actions: readonly PresidentAction[]): boolean {
  return !actions.includes('PLAY') && actions.includes('PASS');
}

export function analyseSelection(context: SelectionContext, selected: readonly Card[]): SelectionAnalysis {
  const pattern = patternOf(selected);
  const refuse = (reason: SelectionRefusal): SelectionAnalysis => ({ pattern, legal: false, reason });

  if (!canPlay(context.availableActions)) {
    return refuse({ code: mustPassNow(context.availableActions) ? 'mustPass' : 'cannotPlay' });
  }
  if (selected.length === 0) return refuse({ code: 'nothingSelected' });
  if (!pattern) return refuse({ code: 'mixedRanks' });
  if (context.legalPlays.some(p => samePattern(p, pattern))) return { pattern, legal: true, reason: null };
  if (context.legalPlays.length === 0) return refuse({ code: 'mustPass' });

  const trick = context.trick;
  if (trick.constraint === 'MATCH_OR_PASS' && trick.requiredRank) {
    // Equal play: the same rank and count, or nothing. "Higher" would mislead, whatever the selection got wrong.
    const count = trick.requiredCount ?? trick.top?.count ?? 1;
    return refuse({ code: 'matchOnly', rank: canonicalRank(trick.requiredRank), count });
  }

  const counts = [...new Set(context.legalPlays.map(p => p.count))].sort((a, b) => a - b);
  if (!counts.includes(pattern.count)) return refuse({ code: 'wrongCount', counts });
  const top = context.trick.top;
  if (top) return refuse({ code: 'notHigher', rank: canonicalRank(top.rank), count: top.count });
  return refuse({ code: 'notPlayable' });
}

/**
 * The cards of the hand that could join the selection to make a bigger legal play of the same rank (a pair
 * from a single, a triple from a pair): the 'add the rest' suggestion. Empty when nothing bigger is listed.
 */
export function completingCards(
  hand: readonly Card[], selected: readonly Card[], legalPlays: readonly PresidentPattern[],
): Card[] {
  const pattern = patternOf(selected);
  if (!pattern) return [];
  if (!legalPlays.some(p => canonicalRank(p.rank) === canonicalRank(pattern.rank) && p.count > pattern.count)) return [];
  const chosen = new Set(selected.map(c => c.name));
  return hand.filter(c => c.rank === pattern.rank && !chosen.has(c.name));
}
