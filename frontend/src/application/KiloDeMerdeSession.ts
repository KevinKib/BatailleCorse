import type Card from '../model/Card';
import type { KiloDeMerdeResponse, KiloDeMerdeView } from '../model/kilodemerde/KiloDeMerdeState';
import SeatGameSession, {
  type SeatGameSessionCallbacks, type SeatGameSessionConfig, type SeatGameSessionEvent, type SeatGameWebSocketPort,
} from './SeatGameSession';

export type { HydrateResult } from './SeatGameSession';
export type KiloDeMerdeWebSocketPort = SeatGameWebSocketPort;
export type KiloDeMerdeSessionEvent = SeatGameSessionEvent<KiloDeMerdeView>;
export type KiloDeMerdeSessionCallbacks = SeatGameSessionCallbacks<KiloDeMerdeView>;

/** Kilo de merde's URLs and storage key. */
export const KILO_DE_MERDE_SESSION_CONFIG: SeatGameSessionConfig = {
  gameType: 'kilo-de-merde',
  restBase: '/api/kilo-de-merde/game',
  tokenKeyPrefix: 'kilo-de-merde:tokens:',
  startDestination: '/app/kilo-de-merde/start',
};

/**
 * The seat-game session plus the game's three commands. The state version guard and the reload after every
 * connection are the shared session's; the server judges every pass and every slap.
 */
export default class KiloDeMerdeSession extends SeatGameSession<KiloDeMerdeView, KiloDeMerdeResponse> {
  constructor(webSocket: KiloDeMerdeWebSocketPort, callbacks: KiloDeMerdeSessionCallbacks) {
    super(KILO_DE_MERDE_SESSION_CONFIG, webSocket, callbacks);
  }

  /** Chooses the card to pass to the left neighbour at this step. */
  pass(card: Card): void {
    this.publishAction('/app/kilo-de-merde/pass', { card });
  }

  /** Confirms that I am ready for the first round or the next one. */
  ready(): void {
    this.publishAction('/app/kilo-de-merde/ready');
  }

  /** Slaps the kilo pile. */
  slap(): void {
    this.publishAction('/app/kilo-de-merde/slap');
  }
}
