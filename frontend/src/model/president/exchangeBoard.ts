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
 * The board "The exchange" from the latest state and the legs still in flight; null when there is nothing to show.
 * Declared by the shared contract: the board chunk implements it.
 */
export function exchangeBoard(
  _state: PresidentState, _mySeat: number, _flyingLegs: ReadonlySet<string>,
): ExchangeBoard | null {
  return null;
}
