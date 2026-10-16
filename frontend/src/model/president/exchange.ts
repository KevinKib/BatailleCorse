import type Card from '../Card';
import type { PresidentExchangePair } from './PresidentState';
import { canonicalRank } from './ranks';

/**
 * Suggest: the lowest cards of the hand that break no set. A convenience only; the server accepts any cards of
 * the hand. `hand` is in the order the server sends it, weakest first, so the order is the strength in force.
 * Whole sets that fit in what is left to give go first, lowest first; if that is not enough, every rank left is
 * in a bigger set and the smallest, lowest set is broken. The result is in hand order.
 */
export function suggestGiveBack(hand: readonly Card[], count: number): Card[] {
  const groups: Card[][] = [];
  for (const card of hand) {
    const last = groups[groups.length - 1];
    if (last && canonicalRank(last[0].rank) === canonicalRank(card.rank)) last.push(card);
    else groups.push([card]);
  }
  const picked = new Set<Card>();
  const taken = new Set<Card[]>();
  let remaining = Math.max(count, 0);
  for (const group of groups) {
    if (group.length > remaining) continue;
    group.forEach(c => picked.add(c));
    taken.add(group);
    remaining -= group.length;
  }
  const rest = groups
    .map((group, order) => ({ group, order }))
    .filter(({ group }) => !taken.has(group))
    .sort((a, b) => a.group.length - b.group.length || a.order - b.order);
  for (const { group } of rest) {
    for (const card of group) {
      if (remaining === 0) break;
      picked.add(card);
      remaining -= 1;
    }
  }
  return hand.filter(c => picked.has(c));
}

/**
 * What the exchange is for one seat: `giving` a high rank who still owes cards, `gave` one who has given them
 * back, `taken` a low rank whose best cards moved at the deal, `idle` a seat outside every pair (a Neutral).
 */
export type ExchangeRole = 'giving' | 'gave' | 'taken' | 'idle';

export interface ExchangeView {
  role: ExchangeRole;
  /** The other seat of my pair, null when idle. */
  partner: number | null;
  /** The cards that pair swaps, 0 when idle. */
  count: number;
}

export function exchangeView(pairs: readonly PresidentExchangePair[], seat: number): ExchangeView {
  const high = pairs.find(p => p.high === seat);
  if (high) return { role: high.done ? 'gave' : 'giving', partner: high.low, count: high.count };
  const low = pairs.find(p => p.low === seat);
  if (low) return { role: 'taken', partner: low.high, count: low.count };
  return { role: 'idle', partner: null, count: 0 };
}
