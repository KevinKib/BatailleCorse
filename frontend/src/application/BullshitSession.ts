import type Card from '../model/Card';
import type { BullshitView } from '../model/bullshit/BullshitState';
import type { BullshitResponse } from '../model/bullshit/BullshitEvents';
import SeatGameSession, {
  type SeatGameSessionCallbacks, type SeatGameSessionConfig, type SeatGameSessionEvent, type SeatGameWebSocketPort,
} from './SeatGameSession';

export type { HydrateResult } from './SeatGameSession';
export type BullshitWebSocketPort = SeatGameWebSocketPort;
export type BullshitSessionEvent = SeatGameSessionEvent<BullshitView>;
export type BullshitSessionCallbacks = SeatGameSessionCallbacks<BullshitView>;

/** Bullshit's URLs and storage key; the token key is the one players already hold, so no seat is lost. */
export const BULLSHIT_SESSION_CONFIG: SeatGameSessionConfig = {
  gameType: 'bullshit',
  restBase: '/api/bullshit/game',
  tokenKeyPrefix: 'bullshit:tokens:',
  startDestination: '/app/bullshit/start',
};

/** The seat-game session plus Bullshit's two commands. */
export default class BullshitSession extends SeatGameSession<BullshitView, BullshitResponse> {
  constructor(webSocket: BullshitWebSocketPort, callbacks: BullshitSessionCallbacks) {
    super(BULLSHIT_SESSION_CONFIG, webSocket, callbacks);
  }

  discard(cards: Card[]): void {
    this.publishAction('/app/discard', { cards });
  }

  callBullshit(): void {
    this.publishAction('/app/callBullshit');
  }
}
