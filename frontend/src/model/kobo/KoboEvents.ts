import type Card from '../Card';
import type { KoboView } from './KoboState';

export type KoboEventType =
  | 'START' | 'ROUND_START' | 'READY' | 'DRAW' | 'SWAP' | 'DISCARD_DRAWN' | 'PEEK' | 'BLIND_SWAP'
  | 'KING_SWAP' | 'SKIP_POWER' | 'END_TURN' | 'KOBO' | 'MATCH' | 'GIVE_TEN' | 'ROUND_END' | 'GAME_OVER';

/** What a command did, public to every seat: a face-down card never appears here. */
export interface KoboEventData {
  seat: number;
  action: string;
  slot: number | null;
  targetSeat: number | null;
  targetSlot: number | null;
  card: Card | null;
  givenSlot: number | null;
  success: boolean | null;
  barrierOpened: boolean | null;
}

/** One line of the live log. */
export interface KoboLogEntry {
  eventType: string;
  data: KoboEventData | null;
}

export interface KoboResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: KoboView | null;
}
