import type Card from '../model/Card';
import type { NinetyNineResponse, NinetyNineView } from '../model/ninetynine/NinetyNineState';
import SeatGameSession, {
  type SeatGameSessionCallbacks, type SeatGameSessionConfig, type SeatGameSessionEvent, type SeatGameWebSocketPort,
} from './SeatGameSession';

export type { HydrateResult } from './SeatGameSession';
export type NinetyNineWebSocketPort = SeatGameWebSocketPort;
export type NinetyNineSessionEvent = SeatGameSessionEvent<NinetyNineView>;
export type NinetyNineSessionCallbacks = SeatGameSessionCallbacks<NinetyNineView>;

/** 99's URLs and storage key. */
export const NINETY_NINE_SESSION_CONFIG: SeatGameSessionConfig = {
  gameType: 'ninety-nine',
  restBase: '/api/ninety-nine/game',
  tokenKeyPrefix: 'ninety-nine:tokens:',
  startDestination: '/app/ninety-nine/start',
};

/**
 * The seat-game session plus 99's one command. The state version guard and the reload after every connection are
 * the shared session's; the server judges every play.
 */
export default class NinetyNineSession extends SeatGameSession<NinetyNineView, NinetyNineResponse> {
  constructor(webSocket: NinetyNineWebSocketPort, callbacks: NinetyNineSessionCallbacks) {
    super(NINETY_NINE_SESSION_CONFIG, webSocket, callbacks);
  }

  /** Plays one card; `aceValue` (1 or 11) is the Ace's chosen value and null for any other card. */
  play(card: Card, aceValue?: number | null): void {
    this.publishAction('/app/ninety-nine/play', { card, aceValue: aceValue ?? null });
  }
}
