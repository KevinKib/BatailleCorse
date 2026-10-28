import type { DraughtsResponse, DraughtsView } from '../../model/draughts/DraughtsState';
import SeatGameSession, {
  type SeatGameSessionCallbacks, type SeatGameSessionConfig, type SeatGameSessionEvent, type SeatGameWebSocketPort,
} from '../SeatGameSession';

export type { HydrateResult } from '../SeatGameSession';
export type DraughtsWebSocketPort = SeatGameWebSocketPort;
export type DraughtsSessionEvent = SeatGameSessionEvent<DraughtsView>;
export type DraughtsSessionCallbacks = SeatGameSessionCallbacks<DraughtsView>;

export const DRAUGHTS_SESSION_CONFIG: SeatGameSessionConfig = {
  gameType: 'draughts',
  restBase: '/api/draughts/game',
  tokenKeyPrefix: 'draughts:tokens:',
  startDestination: '/app/draughts/start',
};

/** The seat-game session plus the commands of draughts. The server judges them all. */
export default class DraughtsSession extends SeatGameSession<DraughtsView, DraughtsResponse> {
  constructor(webSocket: DraughtsWebSocketPort, callbacks: DraughtsSessionCallbacks) {
    super(DRAUGHTS_SESSION_CONFIG, webSocket, callbacks);
  }

  /** Play a move: the client always sends the captured set, which tells apart moves with the same start and end. */
  move(from: number, to: number, captures: number[]): void {
    this.publishAction('/app/draughts/move', { from, to, captures });
  }

  /** Propose a draw. The opponent answers, or a move by either player withdraws it. */
  offerDraw(): void {
    this.publishAction('/app/draughts/offerDraw');
  }

  /** Answer to the opponent's offer: the game ends as a draw. */
  acceptDraw(): void {
    this.publishAction('/app/draughts/acceptDraw');
  }

  /** Answer to the opponent's offer: the game goes on. */
  declineDraw(): void {
    this.publishAction('/app/draughts/declineDraw');
  }
}
