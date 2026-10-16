import type Card from '../Card';
import type { PresidentView } from './PresidentState';

export type PresidentEventType =
  | 'START' | 'ROUND_START' | 'EXCHANGE' | 'PLAY' | 'COMPLETION' | 'PASS' | 'ROUND_END' | 'READY' | 'GAME_OVER';

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
  trickWinner: number | null;
  trickEndReason: string | null;
  barrierOpened: boolean | null;
}

export interface PresidentResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: PresidentView | null;
}
