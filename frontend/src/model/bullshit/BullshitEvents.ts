import type Card from '../Card';
import type { BullshitView } from './BullshitState';

export interface DiscardEventData {
  claimantSeat: number;
  claimedTargetLabel: string;
  count: number;
}

export interface CallBullshitEventData {
  callerSeat: number;
  claimantSeat: number;
  truthful: boolean;
  pickerSeat: number;
  revealedCards: Card[];
}

export interface BullshitResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: BullshitView | null;
}
