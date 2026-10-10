import type Card from '../Card';
import type { LobbyView } from './LobbyView';

export interface BullshitPlayer {
  id: string;
  handCount: number;
  isCurrentPlayer: boolean;
  /** Name the player typed; absent or empty when none (the UI then shows "Player N"). */
  name?: string | null;
  /** Computer player: the UI labels it "Bot N" whatever `name` says. */
  bot?: boolean;
}

export type TableView =
  | { state: 'NO_CLAIM' }
  | { state: 'CLAIM'; claimantId: string; claimedTargetLabel: string; count: number };

export type PendingWinnerView =
  | { state: 'NONE' }
  | { state: 'PENDING'; playerId: string };

export type OutcomeView =
  | { status: 'ONGOING' }
  | { status: 'FINISHED'; winnerId: string };

export interface BullshitState {
  started: true;
  id: string;
  gameType: string;
  myHand: Card[];
  availableActions: string[];
  players: BullshitPlayer[];
  currentTarget: { label: string };
  discardPileSize: number;
  table: TableView;
  pendingWinner: PendingWinnerView;
  outcome: OutcomeView;
}

export type BullshitView = LobbyView | BullshitState;
