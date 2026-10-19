import type Card from '../Card';
import type { PresidentState } from './PresidentState';

/** One pair of the exchange, after it: who is high and low, how many cards each way, and the cards when I know them. */
export interface ExchangeRecapLine {
  high: number;
  low: number;
  count: number;
  /** The low title's best cards, given to the high title; null outside my own pair (the server never sends them). */
  best: Card[] | null;
  /** The cards the high title gave back; null outside my own pair. */
  back: Card[] | null;
  mine: boolean;
}

/**
 * What the exchange of the round came to, once every pair is done (in the exchange view, then once the play has begun; the server keeps the pairs
 * and my `given` / `received` cards all round): a line per pair, with the cards only in my own. Empty before that,
 * with no exchange, and once the round is over. Pure.
 */
export function exchangeRecap(state: PresidentState, mySeat: number): ExchangeRecapLine[] {
  if ((state.phase !== 'PLAYING' && state.phase !== 'EXCHANGE_VIEW') || state.exchange.length === 0 || !state.exchange.every(p => p.done)) return [];
  return state.exchange.map(pair => {
    const mine = pair.low === mySeat || pair.high === mySeat;
    const asLow = pair.low === mySeat;
    return {
      high: pair.high, low: pair.low, count: pair.count, mine,
      best: mine ? (asLow ? state.given : state.received) : null,
      back: mine ? (asLow ? state.received : state.given) : null,
    };
  });
}
