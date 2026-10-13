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
import { HttpError } from '../model/HttpError';
import { useLobbyActions } from '../composables/useLobbyActions';

export interface WebSocketPort {
  publish(destination: string, body?: string): void;
  subscribeToGame(gameId: string): void;
  subscribeToSeat(gameId: string, seat: number, token: string, onMessage: (response: any) => void): void;
  unsubscribeFromSeat(): void;
  unsubscribeFromGame(): void;
  setPresence(body: string): void;
  clearPresence(): void;
}

export interface GameSessionCallbacks {
  onEvent(event: GameEvent): void;
  awaitAnimation(): Promise<void>;
}

/**
 * How a rehydration ended: the game is gone (404), the token no longer resolves (403), the server could not
 * answer (5xx, network: nothing is known, so nothing is forgotten), or all is well.
 */
export type HydrateResult = 'ok' | 'gone' | 'lost' | 'unavailable';

type GameJson = Parameters<typeof BatailleCorse.fromJSON>[0];

/**
 * A message of the seat channel: a lobby view, or a refused command (`success: false`) carrying the sender's
 * current state, the lobby while the room has not started and the game afterwards.
 */
interface SeatResponse {
  success: boolean;
  eventType: string;
  message: string;
  state: LobbyView | GameJson | null;
}

const GAME_TYPE = 'bataille-corse';
const tokenKey = (gameId: string) => `tokens:${gameId}`;

/**
 * This client's seat in a Bataille Corse room: one token, a lobby until the host starts, then the table.
 * Lobby views come on the seat channel (addressed by token), game states on the game topic.
 * The opponent is another human or a server bot: this client only ever acts for its own seat.
 */
export default class GameSession {
  private gameId: string | null = null;
  private myToken: string | null = null;
  private mySeat = 0;
  private state: BatailleCorse | undefined = undefined;
  // Highest lobby version applied; null when none is held (a fresh bind, or a game state came in).
  private lobbyVersion: number | null = null;
  // Set once a game state came in: a lobby view is then stale until the next bind (Play again).
  private started = false;
  // Set once the game is over: the next game on the same id belongs to whoever played again, not to me
  // until I do too (Play again rebinds).
  private over = false;
  // Set once this client has left the lobby: nothing that still arrives concerns it.
  private left = false;
  // Name typed by this player, re-sent on Play again.
  private myName: string | null = null;
  private autoGrabTimeoutId: ReturnType<typeof setTimeout> | null = null;

  private readonly CATCHUP_THRESHOLD = 3;
  private readonly AUTO_GRAB_DELAY = 1500;
  private readonly eventQueue: Response[] = [];
  private isProcessingQueue = false;
  private drainPromise: Promise<void> = Promise.resolve();

  private sendSeq = 0;
  private grabSeq = 0;
  private successfulSlapSeq = 0;
  private erroneousSlapSeq = 0;

  private readonly lobby = useLobbyActions({
    publish: (destination, body) => this.webSocket.publish(destination, body),
    identity: () => this.identity(),
  });

  constructor(
    private readonly webSocket: WebSocketPort,
    private readonly callbacks: GameSessionCallbacks,
  ) {}

  /** Creates a lobby over REST (the host token goes back to this caller only) and sits at seat 0. */
  async create(name?: string): Promise<string> {
    this.myName = name ?? null;
    const res = await fetch('/api/lobby', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ gameType: GAME_TYPE, name: name ?? null }),
    });
    if (!res.ok) throw new HttpError(`Create failed: ${res.status}`, res.status);
    const body = await res.json() as { gameId: string; token: string };
    this.seat(body.gameId, 0, body.token);
    await this.hydrate();
    return body.gameId;
  }

  /** Takes the next free seat of a lobby. */
  async join(gameId: string, name?: string): Promise<void> {
    this.myName = name ?? null;
    const res = await fetch(`/api/game/${gameId}/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name ?? null }),
    });
    if (!res.ok) throw HttpError.fromResponse(res, 'Join failed');
    const body = await res.json() as { playerId: number; token: string };
    this.seat(gameId, body.playerId, body.token);
    await this.hydrate();
  }

  /** Re-attaches to a seat held before a reload (the seat number is a hint: the server's view wins). */
  restore(gameId: string, seat: number, token: string): void {
    this.bind(gameId, seat, token);
  }

  /** Reopens the lobby after a game (the bot and its tier come back) and sits in it. */
  async playAgain(name?: string): Promise<void> {
    if (!this.gameId) return;
    if (name !== undefined) this.myName = name;
    const gameId = this.gameId;
    const res = await fetch(`/api/game/${gameId}/play-again`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: this.myName }),
    });
    if (!res.ok) throw new HttpError(`Play again failed: ${res.status}`, res.status);
    const body = await res.json() as { playerId: number; token: string };
    this.cancelAll();
    this.seat(gameId, body.playerId, body.token);
    await this.hydrate();
  }

  /** Binds to a new seat and remembers its token, alone, for a reload. */
  private seat(gameId: string, seat: number, token: string): void {
    this.bind(gameId, seat, token);
    localStorage.setItem(tokenKey(gameId), JSON.stringify({ [seat]: token }));
  }

  private bind(gameId: string, seat: number, token: string): void {
    this.gameId = gameId;
    this.myToken = token;
    this.mySeat = seat;
    this.lobbyVersion = null;
    this.started = false;
    this.over = false;
    this.left = false;
    this.webSocket.subscribeToSeat(gameId, seat, token, r => this.onSeatResponse(r));
    this.webSocket.subscribeToGame(gameId);
    // Tell the server which seat this connection occupies, so a socket drop is attributable to it
    // (disconnect grace timer, then forfeit). The WebSocketService re-asserts presence on reconnect.
    this.webSocket.setPresence(JSON.stringify({ gameId, token }));
    this.callbacks.onEvent({ type: 'game-id-change', gameId });
    this.callbacks.onEvent({ type: 'my-index-change', playerIndex: seat });
  }

  private identity(): { gameId: string; token: string } | null {
    return this.gameId && this.myToken ? { gameId: this.gameId, token: this.myToken } : null;
  }

  /**
   * Loads the lobby or the running game; `gone` when the server no longer knows the game (404), `lost` when
   * it refuses the token (403), `unavailable` for anything else (the caller keeps the token and the seat).
   */
  async hydrate(): Promise<HydrateResult> {
    if (!this.gameId || this.myToken === null) return 'ok';
    let res: globalThis.Response;
    try {
      res = await fetch(`/api/game/${this.gameId}?token=${this.myToken}`);
    } catch {
      return 'unavailable';
    }
    if (res.status === 404) return 'gone';
    if (res.status === 403) return 'lost';
    if (!res.ok) return 'unavailable';
    const body = await res.json() as LobbyView | GameJson;
    if ((body as LobbyView).started === false) {
      this.applyLobby(body as LobbyView);
    } else {
      this.hydrateGame(BatailleCorse.fromJSON(body as GameJson));
      await this.loadSeats();
    }
    return 'ok';
  }

  /** Shows a running game as it stands, without animating anything. */
  hydrateGame(state: BatailleCorse): void {
    this.started = true;
    this.lobbyVersion = null;
    this.state = state;
    this.over = state.isOver();
    this.callbacks.onEvent({ type: 'state-update', state });
  }

  /** Names, bots and bot tiers of a running game (a reload no longer has the lobby that showed them). */
  private async loadSeats(): Promise<void> {
    if (!this.gameId) return;
    try {
      const res = await fetch(`/api/game/${this.gameId}/session`);
      if (!res.ok) return;
      const view = await res.json() as { players: SessionSeat[] };
      this.callbacks.onEvent({ type: 'seats-change', seats: view.players });
    } catch {
      // The table still plays without names.
    }
  }

  /**
   * Applies a lobby view: one no newer than the one held is dropped, and the seat the server reports replaces
   * ours (it moves when a seat above is vacated), in the session and in the stored entry.
   */
  private applyLobby(view: LobbyView): void {
    if (this.left || this.started) return;
    const incoming = view.lobbyVersion;
    if (incoming !== undefined) {
      if (this.lobbyVersion !== null && incoming <= this.lobbyVersion) return;
      this.lobbyVersion = incoming;
    }
    if (typeof view.mySeat === 'number' && view.mySeat !== this.mySeat) {
      this.mySeat = view.mySeat;
      if (this.gameId && this.myToken) {
        localStorage.setItem(tokenKey(this.gameId), JSON.stringify({ [view.mySeat]: this.myToken }));
      }
      this.callbacks.onEvent({ type: 'my-index-change', playerIndex: view.mySeat });
    }
    this.callbacks.onEvent({
      type: 'seats-change',
      seats: view.players.map(p => ({
        id: p.seat, name: p.name, joined: p.joined, bot: p.bot ?? false, seatOptions: p.seatOptions ?? {},
      })),
    });
    this.callbacks.onEvent({ type: 'lobby-update', lobby: view });
  }

  /** The seat channel: lobby views, and the refusals of my own commands (lobby or game). */
  onSeatResponse(response: SeatResponse): void {
    if (this.left) return;
    if (response.eventType === 'REMOVED') {
      this.removedByHost();
      return;
    }
    const lobby = response.state && (response.state as LobbyView).started === false
      ? response.state as LobbyView : null;
    if (response.success === false) {
      // Nothing moved: the state it carries is the unchanged lobby or game, so nothing animates.
      if (lobby) this.applyLobby(lobby);
      else if (response.state) this.applyState(response.state as unknown as BatailleCorse);
      this.callbacks.onEvent({ type: 'error', eventType: response.eventType, message: response.message });
      return;
    }
    if (lobby) this.applyLobby(lobby);
  }

  startGame(): void {
    const identity = this.identity();
    if (!identity) return;
    this.webSocket.publish('/app/bataillecorse/start', JSON.stringify(identity));
  }

  /** Host only: seat a computer player. The server answers with a lobby refresh. */
  addBot(): void { this.lobby.addBot(); }

  /** Host only: free a bot seat. */
  removeBot(seat: number): void { this.lobby.removeBot(seat); }

  /** Host only: remove another human from the lobby, connected or not. */
  removeSeat(seat: number): void { this.lobby.removeSeat(seat); }

  /** Host only: change one game option (Bataille Corse declares none). */
  setOption(key: string, value: string): void { this.lobby.setOption(key, value); }

  /** Host only: change one option of a computer seat (its tier). */
  setSeatOption(seat: number, key: string, value: string): void { this.lobby.setSeatOption(seat, key, value); }

  /** Rename my own seat. */
  rename(name: string): void {
    this.myName = name;
    this.lobby.rename(name);
  }

  /**
   * Leaves the lobby. A success is silent (the leaver receives nothing), so the client tears down at once:
   * seat channel, game channel, presence and the stored token. A publish failure (socket down) throws before
   * anything is torn down: the player is still seated.
   */
  leave(): void {
    const gameId = this.gameId;
    if (!gameId || this.myToken === null) return;
    this.lobby.leave();
    this.left = true;
    this.cancelAll();
    this.webSocket.unsubscribeFromSeat();
    this.webSocket.unsubscribeFromGame();
    this.webSocket.clearPresence();
    localStorage.removeItem(tokenKey(gameId));
    this.gameId = null;
    this.myToken = null;
    this.lobbyVersion = null;
    this.started = false;
  }

  /** The host removed this seat: the server already freed it, so tear down without a Leave command. */
  private removedByHost(): void {
    const gameId = this.gameId;
    this.left = true;
    this.cancelAll();
    this.webSocket.unsubscribeFromSeat();
    this.webSocket.unsubscribeFromGame();
    this.webSocket.clearPresence();
    if (gameId) localStorage.removeItem(tokenKey(gameId));
    this.gameId = null;
    this.myToken = null;
    this.lobbyVersion = null;
    this.started = false;
    this.callbacks.onEvent({ type: 'removed' });
  }

  private act(destination: string): void {
    this.webSocket.publish(destination, JSON.stringify({ gameId: this.gameId, token: this.myToken }));
  }

  send(): void {
    const topCard = this.state?.pile.cards.at(0);
    this.callbacks.onEvent({ type: 'send', playerIndex: this.mySeat, seq: ++this.sendSeq, topCard });
    this.act('/app/send');
  }

  slap(): void {
    this.callbacks.onEvent({ type: 'slap' });
    this.act('/app/slap');
  }

  grab(): void {
    this.act('/app/grab');
  }

  /** Concede the game: the opponent wins immediately. */
  forfeit(): void {
    this.act('/app/forfeit');
  }

  /** The game topic. Starts queue processing; resolves when the queue is drained. */
  onResponse(response: Response): Promise<void> {
    if (this.left || this.over) return this.drainPromise;
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
      if (this.left || this.over) continue;
      await this.processEvent(response);
    }
    this.isProcessingQueue = false;
  }

  private async processEvent(response: Response): Promise<void> {
    const skipAnimation = this.eventQueue.length >= this.CATCHUP_THRESHOLD;
    let needsAnimationWait = false;

    if (response.eventType === 'GRAB') {
      const grabData = response.eventData as GrabEventData;
      const winnerPlayerIndex = Number(grabData.player?.id);
      if (!isNaN(winnerPlayerIndex)) {
        const pileCards: Card[] = [...(this.state?.pile.cards ?? [])];
        this.callbacks.onEvent({ type: 'grab', winnerPlayerIndex, seq: ++this.grabSeq, pileCards });
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
        this.callbacks.onEvent({ type: 'erroneous-slap', playerIndex: slapperIndex, seq: ++this.erroneousSlapSeq });
        needsAnimationWait = true;
      }
    }

    if (response.eventType === 'SEND') {
      const senderIndex = Number((response.eventData as SendEventData).player?.id);
      // My own send was already emitted optimistically when I pressed the button.
      if (!isNaN(senderIndex) && !skipAnimation && senderIndex !== this.mySeat) {
        const topCard = this.state?.pile.cards.at(0);
        this.callbacks.onEvent({ type: 'send', playerIndex: senderIndex, seq: ++this.sendSeq, topCard });
      }
    }

    // Forward the game-agnostic seat-lifecycle events verbatim; the store reduces
    // them via useSeatPresence. Do not reshape or coerce — useSeatPresence owns that.
    if (
      response.eventType === SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED ||
      response.eventType === SEAT_LIFECYCLE_EVENT.OPPONENT_RECONNECTED
    ) {
      this.callbacks.onEvent({ type: 'presence-event', eventType: response.eventType, eventData: response.eventData });
    }

    const newState = this.applyState(response.state);

    // Only the rightful grabber's tab arms the auto-grab, so two clients never both fire it; a bot
    // collects its own pile on the server.
    this.cancelAll();
    if (newState.pile.getAutoGrabPlayer() === this.mySeat) {
      this.autoGrabTimeoutId = setTimeout(() => {
        this.autoGrabTimeoutId = null;
        this.grab();
      }, this.AUTO_GRAB_DELAY);
    }

    if (needsAnimationWait && !skipAnimation) {
      await this.callbacks.awaitAnimation();
    }
  }

  private applyState(json: BatailleCorse): BatailleCorse {
    const state = BatailleCorse.fromJSON(json as unknown as GameJson);
    this.started = true;
    this.lobbyVersion = null;
    this.state = state;
    this.over = state.isOver();
    this.callbacks.onEvent({ type: 'state-update', state });
    return state;
  }
}
