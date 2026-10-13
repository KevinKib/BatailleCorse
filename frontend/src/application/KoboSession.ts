import type { KoboView, KoboState } from '../model/kobo/KoboState';
import type { KoboResponse } from '../model/kobo/KoboEvents';
import SeatGameSession, {
  type SeatGameSessionCallbacks, type SeatGameSessionConfig, type SeatGameSessionEvent, type SeatGameWebSocketPort,
} from './SeatGameSession';

export type { HydrateResult } from './SeatGameSession';
export type KoboWebSocketPort = SeatGameWebSocketPort;
export type KoboSessionEvent = SeatGameSessionEvent<KoboView>;
export type KoboSessionCallbacks = SeatGameSessionCallbacks<KoboView>;

/** Kobo's URLs and storage key. */
export const KOBO_SESSION_CONFIG: SeatGameSessionConfig = {
  gameType: 'kobo',
  restBase: '/api/kobo/game',
  tokenKeyPrefix: 'kobo:tokens:',
  startDestination: '/app/kobo/start',
};

/** The fields of a matching discard: both expected values are required by the server (a race guard). */
export interface KoboMatchRequest {
  seat: number;
  slot: number;
  /** Only for a card of another player: which of my cards I hand over. */
  giveSlot?: number;
  expectedTopRank: string;
  expectedRevision: number;
}

/**
 * Drops a started state older than the one held (a late hydration GET racing the seat topic). The held
 * version restarts from zero on every bind (each bind announces `game-id-change`, and Play again restarts the
 * aggregate at version 0) and on a lobby view. An equal version is applied: a refused command answers with
 * the unchanged state.
 */
function withVersionGuard(callbacks: KoboSessionCallbacks): KoboSessionCallbacks {
  let held: number | null = null;
  return {
    onEvent(event) {
      if (event.type === 'game-id-change') {
        held = null;
      } else if (event.type === 'state-update') {
        if (!event.state.started) {
          held = null;
        } else {
          const version = (event.state as KoboState).version;
          if (held !== null && version < held) return;
          held = version;
        }
      }
      callbacks.onEvent(event);
    },
  };
}

/** The seat-game session plus Kobo's commands, one per `/app/kobo/...` endpoint. */
export default class KoboSession extends SeatGameSession<KoboView, KoboResponse> {
  constructor(webSocket: KoboWebSocketPort, callbacks: KoboSessionCallbacks) {
    super(KOBO_SESSION_CONFIG, webSocket, withVersionGuard(callbacks));
  }

  ready(): void { this.publishAction('/app/kobo/ready'); }
  draw(): void { this.publishAction('/app/kobo/draw'); }
  swapDrawn(slot: number): void { this.publishAction('/app/kobo/swapDrawn', { slot }); }
  discardDrawn(): void { this.publishAction('/app/kobo/discardDrawn'); }
  skipPower(): void { this.publishAction('/app/kobo/skipPower'); }
  peek(seat: number, slot: number): void { this.publishAction('/app/kobo/peek', { seat, slot }); }
  blindSwap(ownSlot: number, seat: number, slot: number): void {
    this.publishAction('/app/kobo/blindSwap', { ownSlot, seat, slot });
  }
  kingSwap(slot: number): void { this.publishAction('/app/kobo/kingSwap', { slot }); }
  endTurn(): void { this.publishAction('/app/kobo/endTurn'); }
  announce(): void { this.publishAction('/app/kobo/announce'); }
  match(request: KoboMatchRequest): void {
    const { giveSlot, ...rest } = request;
    this.publishAction('/app/kobo/match', giveSlot === undefined ? rest : { ...rest, giveSlot });
  }
  giveTen(seat: number): void { this.publishAction('/app/kobo/giveTen', { seat }); }
}
