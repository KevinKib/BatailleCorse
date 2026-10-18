import type Card from '../Card';
import type { LobbyView } from '../lobby/LobbyView';

/** Mirrors of the backend `KoboDto` (the viewer's projection of a started game); nothing here is derived. */

export type KoboPhase =
  | 'MEMORISING' | 'DRAW' | 'DECISION' | 'POWER' | 'KING_DECISION'
  | 'TURN_END' | 'GIFT' | 'ROUND_OVER' | 'FINISHED';

export type KoboPendingPower = 'NONE' | 'PEEK_OWN' | 'PEEK_OTHER' | 'BLIND_SWAP' | 'KING';

export type KoboAction =
  | 'READY' | 'DRAW' | 'SWAP_DRAWN' | 'DISCARD_DRAWN' | 'PEEK' | 'BLIND_SWAP' | 'KING_SWAP'
  | 'SKIP_POWER' | 'END_TURN' | 'ANNOUNCE_KOBO' | 'GIVE_TEN' | 'MATCH_DISCARD';

/** One position of a tableau. `card` is filled only while the server lets the viewer see it (a revealed round). */
export interface KoboSlot {
  slot: number;
  occupied: boolean;
  /** Changes whenever the card in this slot changes; sent back with a match to guard against a race. */
  revision: number;
  card: Card | null;
}

export interface KoboPlayer {
  seat: number;
  name: string | null;
  current: boolean;
  total: number;
  handSize: number;
  slots: KoboSlot[];
  /** Computer player; omitted by the server when false. */
  bot?: boolean;
}

/** A card the viewer was shown at the start (before "I have memorised"). */
export interface KoboStartingCard {
  slot: number;
  card: Card;
}

/** The last card the viewer peeked at, until the server drops it. */
export interface KoboReveal {
  seat: number;
  slot: number;
  revision: number;
  card: Card;
}

export interface KoboRoundPlayer {
  seat: number;
  handValue: number;
  roundPoints: number;
  totalBefore: number;
  totalAfter: number;
}

export interface KoboRound {
  players: KoboRoundPlayer[];
  announcer: number | null;
  announcerWon: boolean;
  giftTo: number | null;
}

export type KoboOutcome =
  | { status: 'ONGOING' }
  | { status: 'FINISHED'; winners: number[]; reason: string };

export interface KoboState {
  started: true;
  id: string;
  gameType: string;
  /** Increases with every change of the game; a state older than the one held is stale. */
  version: number;
  round: number;
  phase: KoboPhase;
  currentSeat: number;
  readySeats: number[];
  announcer: number | null;
  finalTurnsRemaining: number[];
  pendingPower: KoboPendingPower;
  drawPileSize: number;
  discardSize: number;
  discardTop: Card | null;
  /** Every card is face up (round end, game end). */
  revealed: boolean;
  players: KoboPlayer[];
  hasDrawn: boolean;
  /** Only the viewer's own drawn card; null for everyone else. */
  drawnCard: Card | null;
  startingCards: KoboStartingCard[];
  reveal: KoboReveal | null;
  availableActions: KoboAction[];
  lastRound: KoboRound | null;
  outcome: KoboOutcome;
}

export type KoboView = LobbyView | KoboState;
