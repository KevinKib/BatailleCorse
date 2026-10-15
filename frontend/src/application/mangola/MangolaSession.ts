import type { MangolaResponse, MangolaView } from '../../model/mangola/MangolaState';
import SeatGameSession, {
  type SeatGameSessionCallbacks, type SeatGameSessionConfig, type SeatGameSessionEvent, type SeatGameWebSocketPort,
} from '../SeatGameSession';

export type { HydrateResult } from '../SeatGameSession';
export type MangolaWebSocketPort = SeatGameWebSocketPort;
export type MangolaSessionEvent = SeatGameSessionEvent<MangolaView>;
export type MangolaSessionCallbacks = SeatGameSessionCallbacks<MangolaView>;

export const MANGOLA_SESSION_CONFIG: SeatGameSessionConfig = {
  gameType: 'mangola',
  restBase: '/api/mangola/game',
  tokenKeyPrefix: 'mangola:tokens:',
  startDestination: '/app/mangola/start',
};

/**
 * The seat-game session plus the commands of Mangola. The server judges them all. The shared session already
 * reloads the state after every connection of the seat channel and drops a state older than the one it holds.
 */
export default class MangolaSession extends SeatGameSession<MangolaView, MangolaResponse> {
  constructor(webSocket: MangolaWebSocketPort, callbacks: MangolaSessionCallbacks) {
    super(MANGOLA_SESSION_CONFIG, webSocket, callbacks);
  }

  /** Placement phase: my whole layout, 16 counts in the order of my loop, 32 seeds in all. */
  arrange(counts: number[]): void {
    this.publishAction('/app/mangola/arrange', { counts });
  }

  /** Placement phase: I am ready. The layouts are revealed once both players are. */
  confirmLayout(): void {
    this.publishAction('/app/mangola/confirmLayout');
  }

  /** Placement phase: take my confirmation back, until the other player has confirmed too. */
  editLayout(): void {
    this.publishAction('/app/mangola/editLayout');
  }

  /** Play phase: sow from one of my pits the server listed as legal. */
  sow(pit: number): void {
    this.publishAction('/app/mangola/sow', { pit });
  }
}
