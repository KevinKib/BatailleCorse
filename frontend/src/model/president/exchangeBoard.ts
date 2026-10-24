import type Card from '../Card';
import type { PresidentState } from './PresidentState';

/** The two legs of a pair: the low title's best cards going up, the high title's cards coming back. */
export type ExchangeLeg = 'best' | 'back';

/** Where a pair stands: its best cards flying, awaiting the return, the return flying, over. */
export type ExchangeRowState = 'giving' | 'choosing' | 'returning' | 'done';

/** One pair of the board "The exchange", top pair first. */
export interface ExchangeBoardRow {
  key: string;
  high: number;
  low: number;
  count: number;
  mine: boolean;
  state: ExchangeRowState;
  /** The low title's best cards; the faces only in my own pair, null elsewhere. */
  best: Card[] | null;
  /** The cards given back; the faces only in my own pair, null elsewhere. */
  back: Card[] | null;
}

export interface ExchangeBoard {
  rows: ExchangeBoardRow[];
  /** I am in no pair. */
  neutral: boolean;
}

/** The key of a leg, shared by the screen (legs in flight) and the board: "{high}-{low}:{leg}". Pure. */
export function exchangeLegKey(pair: { high: number; low: number }, leg: ExchangeLeg): string {
  return `${pair.high}-${pair.low}:${leg}`;
}

/**
 * The board "The exchange" from the latest state and the legs still in flight; null outside the exchange (and its view)
 * or with no pair. A row says "done" only once its legs have landed; the faces are known in my own pair only. Pure.
 */
export function exchangeBoard(
  state: PresidentState, mySeat: number, flyingLegs: ReadonlySet<string>,
): ExchangeBoard | null {
  if ((state.phase !== 'EXCHANGE' && state.phase !== 'EXCHANGE_VIEW') || state.exchange.length === 0) return null;
  const rows = state.exchange.map((pair): ExchangeBoardRow => {
    const asLow = pair.low === mySeat;
    const mine = asLow || pair.high === mySeat;
    const faces = (cards: Card[]): Card[] | null => mine && cards.length > 0 ? cards : null;
    let rowState: ExchangeRowState;
    if (flyingLegs.has(exchangeLegKey(pair, 'best'))) rowState = 'giving';
    else if (flyingLegs.has(exchangeLegKey(pair, 'back'))) rowState = 'returning';
    else rowState = pair.done ? 'done' : 'choosing';
    return {
      key: `${pair.high}-${pair.low}`, high: pair.high, low: pair.low, count: pair.count, mine, state: rowState,
      best: faces(asLow ? state.given : state.received),
      back: faces(asLow ? state.received : state.given),
    };
  });
  return { rows, neutral: !rows.some(r => r.mine) };
}
