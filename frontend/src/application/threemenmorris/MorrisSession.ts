import type { MorrisResponse, MorrisView } from '../../model/threemenmorris/MorrisState';
import SeatGameSession, {
  type SeatGameSessionCallbacks, type SeatGameSessionConfig, type SeatGameSessionEvent, type SeatGameWebSocketPort,
} from '../SeatGameSession';

export type { HydrateResult } from '../SeatGameSession';
export type MorrisWebSocketPort = SeatGameWebSocketPort;
export type MorrisSessionEvent = SeatGameSessionEvent<MorrisView>;
export type MorrisSessionCallbacks = SeatGameSessionCallbacks<MorrisView>;

export const MORRIS_SESSION_CONFIG: SeatGameSessionConfig = {
  gameType: 'three-men-morris',
  restBase: '/api/three-men-morris/game',
  tokenKeyPrefix: 'three-men-morris:tokens:',
  startDestination: '/app/three-men-morris/start',
};

/** The seat-game session plus the commands of Three Men's Morris. The server judges both. */
export default class MorrisSession extends SeatGameSession<MorrisView, MorrisResponse> {
  constructor(webSocket: MorrisWebSocketPort, callbacks: MorrisSessionCallbacks) {
    super(MORRIS_SESSION_CONFIG, webSocket, callbacks);
  }

  /** Placement phase: put one of my pieces on a free point. */
  place(point: number): void {
    this.publishAction('/app/three-men-morris/place', { point });
  }

  /** Movement phase: slide one of my pieces to a point the server listed as a legal destination. */
  move(from: number, to: number): void {
    this.publishAction('/app/three-men-morris/move', { from, to });
  }

  /** Movement phase: propose a draw. The opponent answers, or a move by either player withdraws it. */
  offerDraw(): void {
    this.publishAction('/app/three-men-morris/offerDraw');
  }

  /** Answer to the opponent's offer: the game ends as a draw. */
  acceptDraw(): void {
    this.publishAction('/app/three-men-morris/acceptDraw');
  }

  /** Answer to the opponent's offer: the game goes on. */
  declineDraw(): void {
    this.publishAction('/app/three-men-morris/declineDraw');
  }
}
