import type Card from '../Card';
import type { PresidentView } from './PresidentState';

export type PresidentEventType =
  | 'START' | 'ROUND_START' | 'EXCHANGE' | 'PLAY' | 'COMPLETION' | 'PASS' | 'ROUND_END' | 'READY' | 'GAME_OVER'
  | 'EXCHANGE_SEEN' | 'EXCHANGE_CLOSED';

/**
 * What a command did, public to every seat. `action` is the command even when the event type is a ROUND_END or
 * GAME_OVER it caused. A give back carries a count, never its cards.
 */
export interface PresidentEventData {
  seat: number;
  action: string;
  cards: Card[] | null;
  count: number | null;
  /** The place taken when the command made the seat go out. */
  outPlace: number | null;
  /** Set when the command made the seat go out: true for a forbidden finish (no place yet). */
  outForbidden: boolean | null;
  trickWinner: number | null;
  trickEndReason: string | null;
  barrierOpened: boolean | null;
}

/** The data of an EXCHANGE_CLOSED event: the timeout closed the exchange view of `round`; `late` are the seats that had not seen it. */
export interface PresidentExchangeClosedData {
  round: number;
  late: number[];
}

export interface PresidentResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: PresidentView | null;
}
