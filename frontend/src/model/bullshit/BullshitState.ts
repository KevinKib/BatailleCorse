import type Card from '../Card';

export interface BullshitPlayer {
  id: string;
  handCount: number;
  isCurrentPlayer: boolean;
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
