import BatailleCorse from '../model/BatailleCorse';
import type Card from '../model/Card';
import type Response from '../model/Response';
import type GrabEventData from '../model/event/GrabEventData';
import type SlapEventData from '../model/event/SlapEventData';
import type SendEventData from '../model/event/SendEventData';
import type { GameEvent } from './GameEvent';
import type SessionSeat from '../model/SessionSeat';
import type { LobbyView } from '../model/lobby/LobbyView';
import { SEAT_LIFECYCLE_EVENT } from '../model/SeatLifecycleEvents';
import SeatGameSession, {
  type SeatGameResponse, type SeatGameSessionConfig, type SeatGameSessionEvent, type SeatGameWebSocketPort,
} from './SeatGameSession';

export type { HydrateResult } from './SeatGameSession';

export interface WebSocketPort extends SeatGameWebSocketPort {
  subscribeToGame(gameId: string): void;
}

export interface GameSessionCallbacks {
  onEvent(event: GameEvent): void;
  awaitAnimation(): Promise<void>;
}

type GameJson = Parameters<typeof BatailleCorse.fromJSON>[0];

/**
 * The running game, as the session sees it: the server's game state carries neither `started` nor `mySeat`
 * (only a `version`), so the session wraps it in this view.
 */
export interface BatailleCorseGameView {
  started: true;
  version?: number;
  game: BatailleCorse;
}

export type BatailleCorseView = LobbyView | BatailleCorseGameView;

/** Bataille Corse's URLs and storage key: the entry `tokens:{gameId}` players already hold. */
export const BATAILLE_CORSE_SESSION_CONFIG: SeatGameSessionConfig = {
  gameType: 'bataille-corse',
  restBase: '/api/game',
  tokenKeyPrefix: 'tokens:',
  startDestination: '/app/bataillecorse/start',
};

/**
 * Turns the shared session's events into Bataille Corse's own (`lobby-update`, `seats-change`, `my-index-change`)
 * and keeps the game the table currently shows, which the game-topic animations read.
 */
class EventBridge {
  state: BatailleCorse | undefined = undefined;
  // Set once the game is over: the next game on the same id belongs to whoever played again, not to me
  // until I do too (Play again rebinds).
  over = false;

  constructor(private readonly callbacks: GameSessionCallbacks) {}

  onEvent(event: SeatGameSessionEvent<BatailleCorseView>): void {
    switch (event.type) {
      case 'state-update': {
        const view = event.state;
        if (view.started) {
          this.show(view.game);
        } else {
          this.callbacks.onEvent({
            type: 'seats-change',
            seats: view.players.map(p => ({
              id: p.seat, name: p.name, joined: p.joined, bot: p.bot ?? false, seatOptions: p.seatOptions ?? {},
            })),
          });
          this.callbacks.onEvent({ type: 'lobby-update', lobby: view });
        }
        break;
      }
      case 'seat-change':
        this.callbacks.onEvent({ type: 'my-index-change', playerIndex: event.seat });
        break;
      case 'game-id-change':
      case 'error':
      case 'removed':
        this.callbacks.onEvent(event);
        break;
      case 'event':
        // The seat channel only carries lobby views and refusals here; the game's events come on the game topic.
        break;
    }
  }

  show(state: BatailleCorse): void {
    this.state = state;
    this.over = state.isOver();
    this.callbacks.onEvent({ type: 'state-update', state });
  }

  emit(event: GameEvent): void {
    this.callbacks.onEvent(event);
  }

  awaitAnimation(): Promise<void> {
    return this.callbacks.awaitAnimation();
  }
}

/**
 * This client's seat in a Bataille Corse room: one token, a lobby until the host starts, then the table.
 * Lobby views come on the seat channel (addressed by token), game states on the game topic, where they are
 * played in order, each with its animation. The opponent is another human or a server bot: this client only
 * ever acts for its own seat.
 */
export default class BatailleCorseSession extends SeatGameSession<
  BatailleCorseView, SeatGameResponse<BatailleCorseView>
> {
  private readonly CATCHUP_THRESHOLD = 3;
  private readonly AUTO_GRAB_DELAY = 1500;
  private readonly eventQueue: Response[] = [];
  private isProcessingQueue = false;
  private drainPromise: Promise<void> = Promise.resolve();
  private autoGrabTimeoutId: ReturnType<typeof setTimeout> | null = null;

  private sendSeq = 0;
  private grabSeq = 0;
  private successfulSlapSeq = 0;
  private erroneousSlapSeq = 0;

  private readonly gameTopic: WebSocketPort;

  constructor(
    webSocket: WebSocketPort,
    callbacks: GameSessionCallbacks,
    private readonly bridge: EventBridge = new EventBridge(callbacks),
  ) {
    super(BATAILLE_CORSE_SESSION_CONFIG, webSocket, { onEvent: e => bridge.onEvent(e) });
    this.gameTopic = webSocket;
  }

  // --- Shared-session extension points ---------------------------------------------------------------------

  protected override parseView(raw: unknown): BatailleCorseView {
    if ((raw as { started?: boolean }).started === false) return raw as LobbyView;
    return { started: true, version: (raw as { version?: number }).version, game: BatailleCorse.fromJSON(raw as GameJson) };
  }

  protected override onBind(gameId: string): void {
    this.cancelAll();
    this.bridge.over = false;
    this.gameTopic.subscribeToGame(gameId);
  }

  protected override onTearDown(): void {
    this.cancelAll();
  }

  /** A running game is not reloaded over events still animating (that would replace the state under them). */
  protected override shouldHydrateOnConnect(): boolean {
    return !(this.hasStarted && (this.isProcessingQueue || this.eventQueue.length > 0));
  }

  /** Names, bots and bot tiers of a running game (a reload no longer has the lobby that showed them). */
  protected override async onViewHydrated(view: BatailleCorseView): Promise<void> {
    if (!view.started) return;
    const gameId = this.currentGameId;
    if (!gameId) return;
    try {
      const res = await fetch(`/api/game/${gameId}/session`);
      if (!res.ok) return;
      const body = await res.json() as { players: SessionSeat[] };
      this.bridge.emit({ type: 'seats-change', seats: body.players });
    } catch {
      // The table still plays without names.
    }
  }

  // --- Commands --------------------------------------------------------------------------------------------

  override startGame(): void {
    if (!this.currentGameId) return;
    super.startGame();
  }

  /** Shows a running game as it stands, without animating anything. */
  hydrateGame(state: BatailleCorse): void {
    this.applyView({ started: true, game: state });
  }

  send(): void {
    const topCard = this.bridge.state?.pile.cards.at(0);
    this.bridge.emit({ type: 'send', playerIndex: this.currentSeat ?? 0, seq: ++this.sendSeq, topCard });
    this.publishAction('/app/send');
  }

  slap(): void {
    this.bridge.emit({ type: 'slap' });
    this.publishAction('/app/slap');
  }

  grab(): void {
    this.publishAction('/app/grab');
  }

  // --- The game topic --------------------------------------------------------------------------------------

  /** The game topic. Starts queue processing; resolves when the queue is drained. */
  onGameResponse(response: Response): Promise<void> {
    if (this.isLeft || this.bridge.over) return this.drainPromise;
    this.eventQueue.push(response);
    if (!this.isProcessingQueue) this.drainPromise = this.drainQueue();
    return this.drainPromise;
  }

  /** Cancels the auto-grab timer. */
  cancelAll(): void {
    if (this.autoGrabTimeoutId !== null) {
      clearTimeout(this.autoGrabTimeoutId);
      this.autoGrabTimeoutId = null;
    }
  }

  private async drainQueue(): Promise<void> {
    this.isProcessingQueue = true;
    while (this.eventQueue.length > 0) {
      const response = this.eventQueue.shift()!;
      if (this.isLeft || this.bridge.over) continue;
      // A frame older than the state held: its state, its animation and its auto-grab are all out of date.
      if (this.isStaleVersion((response.state as { version?: number } | undefined)?.version)) continue;
      await this.processEvent(response);
    }
    this.isProcessingQueue = false;
  }

  private async processEvent(response: Response): Promise<void> {
    const skipAnimation = this.eventQueue.length >= this.CATCHUP_THRESHOLD;
    const mySeat = this.currentSeat;
    const state = this.bridge.state;
    let needsAnimationWait = false;

    if (response.eventType === 'GRAB') {
      const grabData = response.eventData as GrabEventData;
      const winnerPlayerIndex = Number(grabData.player?.id);
      if (!isNaN(winnerPlayerIndex)) {
        const pileCards: Card[] = [...(state?.pile.cards ?? [])];
        this.bridge.emit({ type: 'grab', winnerPlayerIndex, seq: ++this.grabSeq, pileCards });
        needsAnimationWait = true;
      }
    }

    if (response.eventType === 'SLAP') {
      const slapData = response.eventData as SlapEventData;
      const slapperIndex = Number(slapData.player?.id);
      if (slapData.isSuccessful && !isNaN(slapperIndex)) {
        const pileCards: Card[] = [...(state?.pile.cards ?? [])];
        this.bridge.emit({
          type: 'successful-slap',
          winnerPlayerIndex: slapperIndex,
          seq: ++this.successfulSlapSeq,
          pileCards,
        });
        needsAnimationWait = true;
      } else if (!slapData.isSuccessful && !isNaN(slapperIndex)) {
        this.bridge.emit({ type: 'erroneous-slap', playerIndex: slapperIndex, seq: ++this.erroneousSlapSeq });
        needsAnimationWait = true;
      }
    }

    if (response.eventType === 'SEND') {
      const senderIndex = Number((response.eventData as SendEventData).player?.id);
      // My own send was already emitted optimistically when I pressed the button.
      if (!isNaN(senderIndex) && !skipAnimation && senderIndex !== mySeat) {
        const topCard = state?.pile.cards.at(0);
        this.bridge.emit({ type: 'send', playerIndex: senderIndex, seq: ++this.sendSeq, topCard });
      }
    }

    // Forward the game-agnostic seat-lifecycle events verbatim; the store reduces
    // them via useSeatPresence. Do not reshape or coerce — useSeatPresence owns that.
    if (
      response.eventType === SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED ||
      response.eventType === SEAT_LIFECYCLE_EVENT.OPPONENT_RECONNECTED
    ) {
      this.bridge.emit({ type: 'presence-event', eventType: response.eventType, eventData: response.eventData });
    }

    const view = this.parseView(response.state) as BatailleCorseGameView;
    this.applyView(view);
    const newState = view.game;

    // Only the rightful grabber's tab arms the auto-grab, so two clients never both fire it; a bot
    // collects its own pile on the server.
    this.cancelAll();
    if (mySeat !== null && newState.pile.getAutoGrabPlayer() === mySeat) {
      this.autoGrabTimeoutId = setTimeout(() => {
        this.autoGrabTimeoutId = null;
        this.grab();
      }, this.AUTO_GRAB_DELAY);
    }

    if (needsAnimationWait && !skipAnimation) {
      await this.bridge.awaitAnimation();
    }
  }
}
