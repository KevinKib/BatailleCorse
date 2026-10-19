import type Card from '../model/Card';
import type { PresidentView } from '../model/president/PresidentState';
import type { PresidentResponse } from '../model/president/PresidentEvents';
import SeatGameSession, {
  type SeatGameSessionCallbacks, type SeatGameSessionConfig, type SeatGameSessionEvent, type SeatGameWebSocketPort,
} from './SeatGameSession';

export type { HydrateResult } from './SeatGameSession';
export type PresidentWebSocketPort = SeatGameWebSocketPort;
export type PresidentSessionEvent = SeatGameSessionEvent<PresidentView>;
export type PresidentSessionCallbacks = SeatGameSessionCallbacks<PresidentView>;

/** President's URLs and storage key. */
export const PRESIDENT_SESSION_CONFIG: SeatGameSessionConfig = {
  gameType: 'president',
  restBase: '/api/president/game',
  tokenKeyPrefix: 'president:tokens:',
  startDestination: '/app/president/start',
};

/**
 * The seat-game session plus President's commands, one per `/app/president/...` endpoint. The state version
 * guard and the reload after every connection are the shared session's; the server judges every command.
 */
export default class PresidentSession extends SeatGameSession<PresidentView, PresidentResponse> {
  constructor(webSocket: PresidentWebSocketPort, callbacks: PresidentSessionCallbacks) {
    super(PRESIDENT_SESSION_CONFIG, webSocket, callbacks);
  }

  /** Plays the cards (one rank, any count): leading, or beating the trick. */
  play(cards: Card[]): void { this.publishAction('/app/president/play', { cards }); }

  pass(): void { this.publishAction('/app/president/pass'); }

  /** Exchange step of a high rank: the cards given back to the title pair. */
  giveBack(cards: Card[]): void { this.publishAction('/app/president/giveBack', { cards }); }

  /** Round-over barrier: ready for the next round. */
  ready(): void { this.publishAction('/app/president/ready'); }

  /** Exchange view: this screen has shown the exchange of `round`. A late or repeated one is ignored silently. */
  seeExchange(round: number): void { this.publishAction('/app/president/seeExchange', { round }); }
}
