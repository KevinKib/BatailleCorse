import type { BlackjackResponse, BlackjackView } from '../model/blackjack/BlackjackState';
import SeatGameSession, {
  type SeatGameSessionCallbacks, type SeatGameSessionConfig, type SeatGameSessionEvent, type SeatGameWebSocketPort,
} from './SeatGameSession';

export type { HydrateResult } from './SeatGameSession';
export type BlackjackWebSocketPort = SeatGameWebSocketPort;
export type BlackjackSessionEvent = SeatGameSessionEvent<BlackjackView>;
export type BlackjackSessionCallbacks = SeatGameSessionCallbacks<BlackjackView>;

/** Blackjack's URLs and storage key. */
export const BLACKJACK_SESSION_CONFIG: SeatGameSessionConfig = {
  gameType: 'blackjack',
  restBase: '/api/blackjack/game',
  tokenKeyPrefix: 'blackjack:tokens:',
  startDestination: '/app/blackjack/start',
};

/**
 * The seat-game session plus the game's five commands. The state version guard and the reload after every
 * connection are the shared session's; the server judges every bet and every action.
 */
export default class BlackjackSession extends SeatGameSession<BlackjackView, BlackjackResponse> {
  constructor(webSocket: BlackjackWebSocketPort, callbacks: BlackjackSessionCallbacks) {
    super(BLACKJACK_SESSION_CONFIG, webSocket, callbacks);
  }

  /** Places the bet of the hand (whole points; sealed until the deal). */
  bet(amount: number): void {
    this.publishAction('/app/blackjack/bet', { amount });
  }

  hit(): void {
    this.publishAction('/app/blackjack/hit');
  }

  stand(): void {
    this.publishAction('/app/blackjack/stand');
  }

  doubleDown(): void {
    this.publishAction('/app/blackjack/double');
  }

  split(): void {
    this.publishAction('/app/blackjack/split');
  }
}
