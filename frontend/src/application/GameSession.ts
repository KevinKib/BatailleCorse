import BatailleCorse from '../model/BatailleCorse';
import type Card from '../model/Card';
import type Response from '../model/Response';
import type CreateEventData from '../model/event/CreateEventData';
import type GrabEventData from '../model/event/GrabEventData';
import type SlapEventData from '../model/event/SlapEventData';
import type { GameEvent } from './GameEvent';
import AI from '../model/ai/AI';

export interface WebSocketPort {
  publish(destination: string, body?: string): void;
  subscribeToGame(gameId: string): void;
}

export interface GameSessionCallbacks {
  onEvent(event: GameEvent): void;
  awaitAnimation(): Promise<void>;
}

export default class GameSession {
  private gameId: string | null = null;
  private state: BatailleCorse | undefined = undefined;
  private playerTokens: Record<number, string> = {};
  private pendingCreate = false;
  private autoGrabTimeoutId: ReturnType<typeof setTimeout> | null = null;
  private ai: AI;

  // Multiplayer perspective. Solo is the default so single-player behaviour is unchanged.
  private mode: 'solo' | 'multiplayer' = 'solo';
  private myPlayerIndex = 0;
  private waiting = false;

  private readonly CATCHUP_THRESHOLD = 3;
  private readonly AUTO_GRAB_DELAY = 1500;
  private readonly eventQueue: Response[] = [];
  private isProcessingQueue = false;
  private drainPromise: Promise<void> = Promise.resolve();

  private sendSeq = 0;
  private grabSeq = 0;
  private successfulSlapSeq = 0;
  private erroneousSlapSeq = 0;

  constructor(
    private readonly webSocket: WebSocketPort,
    private readonly callbacks: GameSessionCallbacks,
    private readonly aiFactory: () => AI,
  ) {
    this.ai = aiFactory();
  }

  create(gameMode: 'solo' | 'multiplayer', playerName?: string): void {
    this.pendingCreate = true;
    this.mode = gameMode;
    this.myPlayerIndex = 0;
    this.waiting = gameMode === 'multiplayer';
    // Only solo needs the puppet AI; multiplayer waits for a second human.
    if (gameMode === 'solo') {
      this.ai = this.aiFactory();
    }
    this.emitPerspective();
    // playerName stays browser-local per the spec; only the mode is sent.
    void playerName;
    const serverMode = gameMode === 'solo' ? 'SOLO' : 'MULTIPLAYER';
    this.webSocket.publish('/app/create', JSON.stringify({ mode: serverMode }));
  }

  /** Joins an existing multiplayer game as seat 1 and hydrates its state. */
  async join(id: string): Promise<void> {
    const response = await fetch(`/api/game/${id}/join`, { method: 'POST' });
    if (!response.ok) {
      throw new Error(`Join failed: ${response.status}`);
    }
    const body = await response.json() as { playerId: number; token: string };

    this.mode = 'multiplayer';
    this.myPlayerIndex = body.playerId;
    this.waiting = false;

    const tokens = { [body.playerId]: body.token };
    this.playerTokens = tokens;
    localStorage.setItem(`tokens:${id}`, JSON.stringify(tokens));

    this.gameId = id;
    this.webSocket.subscribeToGame(id);

    this.emitPerspective();
    this.callbacks.onEvent({ type: 'game-id-change', gameId: id });

    const stateResponse = await fetch(`/api/game/${id}`);
    if (stateResponse.ok) {
      const json = await stateResponse.json();
      this.state = BatailleCorse.fromJSON(json as Parameters<typeof BatailleCorse.fromJSON>[0]);
      this.callbacks.onEvent({ type: 'state-update', state: this.state });
    }
  }

  hydrate(id: string, gameState: BatailleCorse): void {
    this.gameId = id;
    this.state = gameState instanceof BatailleCorse
      ? gameState
      : BatailleCorse.fromJSON(gameState as unknown as Parameters<typeof BatailleCorse.fromJSON>[0]);
    this.callbacks.onEvent({ type: 'game-id-change', gameId: id });
    this.callbacks.onEvent({ type: 'state-update', state: this.state });
  }

  restoreTokens(tokens: Record<number, string>): void {
    this.playerTokens = tokens;
  }

  /**
   * Restores mode + perspective from persisted tokens. Solo stores both seat
   * tokens ({0,1}); multiplayer stores exactly one (the local player's seat).
   */
  restoreSession(tokens: Record<number, string>): void {
    this.playerTokens = tokens;
    const seats = Object.keys(tokens).map(Number);
    if (seats.length >= 2) {
      this.mode = 'solo';
      this.myPlayerIndex = 0;
    } else {
      this.mode = 'multiplayer';
      this.myPlayerIndex = seats[0] ?? 0;
      this.waiting = false;
    }
    this.emitPerspective();
  }

  private emitPerspective(): void {
    this.callbacks.onEvent({ type: 'mode-change', mode: this.mode });
    this.callbacks.onEvent({ type: 'my-index-change', playerIndex: this.myPlayerIndex });
    this.callbacks.onEvent({ type: 'waiting-change', waiting: this.waiting });
  }

  send(playerIndex: number): void {
    const topCard = this.state?.pile.cards.at(0);
    this.callbacks.onEvent({ type: 'send', playerIndex, seq: ++this.sendSeq, topCard });
    this.webSocket.publish('/app/send', JSON.stringify({
      gameId: this.gameId,
      token: this.playerTokens[playerIndex],
    }));
  }

  slap(playerIndex: number): void {
    this.callbacks.onEvent({ type: 'slap' });
    this.webSocket.publish('/app/slap', JSON.stringify({
      gameId: this.gameId,
      token: this.playerTokens[playerIndex],
    }));
  }

  grab(playerIndex: number): void {
    this.webSocket.publish('/app/grab', JSON.stringify({
      gameId: this.gameId,
      token: this.playerTokens[playerIndex],
    }));
  }

  /** Starts queue processing. Returns a promise that resolves when the queue is drained. */
  onResponse(response: Response): Promise<void> {
    this.eventQueue.push(response);
    if (!this.isProcessingQueue) this.drainPromise = this.drainQueue();
    return this.drainPromise;
  }

  /** Cancels the auto-grab timer and any pending AI action. */
  cancelAll(): void {
    if (this.autoGrabTimeoutId !== null) {
      clearTimeout(this.autoGrabTimeoutId);
      this.autoGrabTimeoutId = null;
    }
    this.ai.cancel();
  }

  private async drainQueue(): Promise<void> {
    this.isProcessingQueue = true;
    while (this.eventQueue.length > 0) {
      const response = this.eventQueue.shift()!;
      await this.processEvent(response);
    }
    this.isProcessingQueue = false;
  }

  private async processEvent(response: Response): Promise<void> {
    const skipAnimation = this.eventQueue.length >= this.CATCHUP_THRESHOLD;
    let needsAnimationWait = false;

    if (response.eventType === 'CREATE') {
      if (!this.pendingCreate) return;
      this.pendingCreate = false;
      const createData = response.eventData as CreateEventData;
      this.gameId = createData.game.id;
      this.playerTokens = createData.tokens;
      localStorage.setItem(`tokens:${this.gameId}`, JSON.stringify(createData.tokens));
      this.webSocket.subscribeToGame(this.gameId);
      this.callbacks.onEvent({ type: 'game-id-change', gameId: this.gameId });
    }

    if (response.eventType === 'JOIN') {
      this.waiting = false;
      this.callbacks.onEvent({ type: 'waiting-change', waiting: false });
    }

    if (response.eventType === 'GRAB') {
      const grabData = response.eventData as GrabEventData;
      const winnerPlayerIndex = Number(grabData.player?.id);
      if (!isNaN(winnerPlayerIndex)) {
        const pileCards: Card[] = [...(this.state?.pile.cards ?? [])];
        this.callbacks.onEvent({
          type: 'grab',
          winnerPlayerIndex,
          seq: ++this.grabSeq,
          pileCards,
        });
        needsAnimationWait = true;
      }
    }

    if (response.eventType === 'SLAP') {
      const slapData = response.eventData as SlapEventData;
      const slapperIndex = Number(slapData.player?.id);
      if (slapData.isSuccessful && !isNaN(slapperIndex)) {
        const pileCards: Card[] = [...(this.state?.pile.cards ?? [])];
        this.callbacks.onEvent({
          type: 'successful-slap',
          winnerPlayerIndex: slapperIndex,
          seq: ++this.successfulSlapSeq,
          pileCards,
        });
        needsAnimationWait = true;
      } else if (!slapData.isSuccessful && !isNaN(slapperIndex)) {
        this.callbacks.onEvent({
          type: 'erroneous-slap',
          playerIndex: slapperIndex,
          seq: ++this.erroneousSlapSeq,
        });
        needsAnimationWait = true;
      }
    }

    const newState = BatailleCorse.fromJSON(response.state as unknown as Parameters<typeof BatailleCorse.fromJSON>[0]);
    this.state = newState;
    this.callbacks.onEvent({ type: 'state-update', state: newState });

    // Reset and conditionally restart the auto-grab timer
    if (this.autoGrabTimeoutId !== null) {
      clearTimeout(this.autoGrabTimeoutId);
      this.autoGrabTimeoutId = null;
    }
    // In multiplayer, only the rightful grabber's tab arms the timer so the two
    // clients don't both fire a grab. Solo is unchanged.
    const grabPlayer = newState.pile.getAutoGrabPlayer();
    const mayAutoGrab = this.mode === 'solo' || grabPlayer === this.myPlayerIndex;
    if (grabPlayer !== null && mayAutoGrab) {
      this.autoGrabTimeoutId = setTimeout(() => {
        this.autoGrabTimeoutId = null;
        this.grab(grabPlayer);
      }, this.AUTO_GRAB_DELAY);
    }

    // The puppet AI only drives player 1 in solo; multiplayer is two humans.
    if (this.mode === 'solo') {
      this.ai.play(newState, {
        send: () => this.send(1),
        slap: () => this.slap(1),
      });
    }

    if (needsAnimationWait && !skipAnimation) {
      await this.callbacks.awaitAnimation();
    }
  }
}
