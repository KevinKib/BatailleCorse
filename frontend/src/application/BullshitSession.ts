import type Card from '../model/Card';
import type { BullshitView } from '../model/bullshit/BullshitState';
import type { BullshitResponse } from '../model/bullshit/BullshitEvents';
import { HttpError } from '../model/HttpError';
import { useLobbyActions } from '../composables/useLobbyActions';

export interface BullshitWebSocketPort {
  publish(destination: string, body?: string): void;
  subscribeToSeat(gameId: string, seat: number, token: string, onMessage: (response: any) => void): void;
  setPresence(body: string): void;
  unsubscribeFromSeat(): void;
  unsubscribeFromGame(): void;
  clearPresence(): void;
}

export type BullshitSessionEvent =
  | { type: 'state-update'; state: BullshitView }
  | { type: 'game-id-change'; gameId: string }
  | { type: 'seat-change'; seat: number }
  | { type: 'error'; eventType: string; message: string }
  | { type: 'removed' }
  | { type: 'event'; eventType: string; eventData: unknown; message: string };

export interface BullshitSessionCallbacks {
  onEvent(event: BullshitSessionEvent): void;
}

/**
 * How a rehydration ended: the game is gone (404), the token no longer resolves (403), the server could not
 * answer (5xx, network: nothing is known, so nothing is forgotten), or all is well.
 */
export type HydrateResult = 'ok' | 'gone' | 'lost' | 'unavailable';

const tokenKey = (gameId: string) => `bullshit:tokens:${gameId}`;

export default class BullshitSession {
  private gameId: string | null = null;
  private myToken: string | null = null;
  private mySeat: number | null = null;
  // Highest lobby version applied; null when none is held (a fresh bind, or a game state came in).
  private lobbyVersion: number | null = null;
  // Set once a game state came in: a lobby view is then stale (a late hydration GET, a reordered frame) until the
  // next bind, which Play again always goes through.
  private started = false;
  // Set once this client has left the lobby: nothing that still arrives concerns it.
  private left = false;
  // Name typed by this player, re-sent on Play again: the reopened lobby starts empty.
  private myName: string | null = null;
  private readonly lobby = useLobbyActions({
    publish: (destination, body) => this.webSocket.publish(destination, body),
    identity: () => (this.gameId && this.myToken ? { gameId: this.gameId, token: this.myToken } : null),
  });

  constructor(
    private readonly webSocket: BullshitWebSocketPort,
    private readonly callbacks: BullshitSessionCallbacks,
  ) {}

  /** Creates a lobby over REST (the host token goes back to this caller only) and sits at seat 0. */
  async create(name?: string): Promise<string> {
    this.myName = name ?? null;
    const res = await fetch('/api/lobby', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ gameType: 'bullshit', name: name ?? null }),
    });
    if (!res.ok) throw new HttpError(`Create failed: ${res.status}`, res.status);
    const body = await res.json() as { gameId: string; token: string };
    this.bind(body.gameId, 0, body.token);
    localStorage.setItem(tokenKey(body.gameId), JSON.stringify({ 0: body.token }));
    await this.hydrate();
    return body.gameId;
  }

  async join(gameId: string, name?: string): Promise<void> {
    this.myName = name ?? null;
    const res = await fetch(`/api/bullshit/game/${gameId}/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name ?? null }),
    });
    if (!res.ok) throw HttpError.fromResponse(res, 'Join failed');
    const body = await res.json() as { playerId: number; token: string };
    this.bind(gameId, body.playerId, body.token);
    localStorage.setItem(tokenKey(gameId), JSON.stringify({ [body.playerId]: body.token }));
    await this.hydrate();
  }

  restore(gameId: string, seat: number, token: string): void {
    this.bind(gameId, seat, token);
  }

  private bind(gameId: string, seat: number, token: string): void {
    this.gameId = gameId;
    this.myToken = token;
    this.mySeat = seat;
    this.lobbyVersion = null;
    this.started = false;
    this.left = false;
    this.webSocket.subscribeToSeat(gameId, seat, token, r => this.onResponse(r));
    // Tell the server which seat this connection occupies, so a socket drop is
    // attributable to this seat (drives the disconnect grace timer + forfeit).
    // The WebSocketService re-asserts presence on reconnect.
    this.webSocket.setPresence(JSON.stringify({ gameId, token }));
    this.callbacks.onEvent({ type: 'game-id-change', gameId });
    this.callbacks.onEvent({ type: 'seat-change', seat });
  }

  /**
   * Loads the current state; `gone` when the server no longer knows the game (404), `lost` when it refuses the
   * token (403), `unavailable` for anything else (the caller keeps the token and the seat).
   */
  async hydrate(): Promise<HydrateResult> {
    if (!this.gameId || this.myToken === null) return 'ok';
    const gameId = this.gameId;
    const token = this.myToken;
    let res: Response;
    try {
      res = await fetch(`/api/bullshit/game/${gameId}?token=${token}`);
    } catch {
      return 'unavailable';
    }
    // The player left (or took another seat) while the answer was on its way: it no longer concerns this client,
    // and a refusal is only the server having freed the seat we just gave up.
    if (this.gameId !== gameId || this.myToken !== token) return 'ok';
    if (res.status === 404) return 'gone';
    if (res.status === 403) return 'lost';
    if (!res.ok) return 'unavailable';
    this.applyView(await res.json() as BullshitView);
    return 'ok';
  }

  /**
   * Applies a view from the server: a lobby view no newer than the one held is dropped, and the seat the
   * server reports replaces ours (it moves when a seat above is vacated), in the store and in the stored entry.
   */
  private applyView(view: BullshitView): void {
    if (this.left) return;
    if (!view.started) {
      if (this.started) return;
      const incoming = view.lobbyVersion;
      if (incoming !== undefined) {
        if (this.lobbyVersion !== null && incoming <= this.lobbyVersion) return;
        this.lobbyVersion = incoming;
      }
    } else {
      this.started = true;
      this.lobbyVersion = null;
    }
    const seat = view.mySeat;
    if (typeof seat === 'number' && seat !== this.mySeat) {
      this.mySeat = seat;
      if (this.gameId && this.myToken) {
        localStorage.setItem(tokenKey(this.gameId), JSON.stringify({ [seat]: this.myToken }));
      }
      this.callbacks.onEvent({ type: 'seat-change', seat });
    }
    this.callbacks.onEvent({ type: 'state-update', state: view });
  }

  discard(cards: Card[]): void {
    this.webSocket.publish('/app/discard', JSON.stringify({ gameId: this.gameId, token: this.myToken, cards }));
  }

  callBullshit(): void {
    this.webSocket.publish('/app/callBullshit', JSON.stringify({ gameId: this.gameId, token: this.myToken }));
  }

  /** Resign the in-progress game (RESIGNED forfeit). Mirrors BatailleCorse's /app/forfeit. */
  forfeit(): void {
    this.webSocket.publish('/app/forfeit', JSON.stringify({ gameId: this.gameId, token: this.myToken }));
  }

  startGame(): void {
    this.webSocket.publish('/app/bullshit/start', JSON.stringify({ gameId: this.gameId, token: this.myToken }));
  }

  /** Host only: seat a computer-controlled player. The server answers with a lobby refresh. */
  addBot(): void { this.lobby.addBot(); }

  /** Host only: free the bot seat (the server refuses when a human sits above it). */
  removeBot(seat: number): void { this.lobby.removeBot(seat); }

  /** Host only: remove another human from the lobby, connected or not. */
  removeSeat(seat: number): void { this.lobby.removeSeat(seat); }

  /** Host only: change one game option; the answer is the next lobby view. */
  setOption(key: string, value: string): void { this.lobby.setOption(key, value); }

  /** Host only: change one option of a computer seat. */
  setSeatOption(seat: number, key: string, value: string): void { this.lobby.setSeatOption(seat, key, value); }

  /** Rename my own seat. */
  rename(name: string): void {
    this.myName = name;
    this.lobby.rename(name);
  }

  /**
   * Leaves the lobby. A success is silent (the leaver receives nothing), so the client tears down at once:
   * seat channel, game channel, presence and the stored token. Nothing that arrives afterwards concerns it.
   * A publish failure (socket down) throws before anything is torn down: the player is still seated.
   */
  leave(): void {
    const gameId = this.gameId;
    if (!gameId || this.myToken === null) return;
    this.lobby.leave();
    this.left = true;
    this.webSocket.unsubscribeFromSeat();
    this.webSocket.unsubscribeFromGame();
    this.webSocket.clearPresence();
    localStorage.removeItem(tokenKey(gameId));
    this.gameId = null;
    this.myToken = null;
    this.mySeat = null;
    this.lobbyVersion = null;
    this.started = false;
  }

  /** The host removed this seat: the server already freed it, so tear down without a Leave command. */
  private removedByHost(): void {
    const gameId = this.gameId;
    this.left = true;
    this.webSocket.unsubscribeFromSeat();
    this.webSocket.unsubscribeFromGame();
    this.webSocket.clearPresence();
    if (gameId) localStorage.removeItem(tokenKey(gameId));
    this.gameId = null;
    this.myToken = null;
    this.mySeat = null;
    this.lobbyVersion = null;
    this.started = false;
    this.callbacks.onEvent({ type: 'removed' });
  }

  async playAgain(name?: string): Promise<void> {
    if (!this.gameId) return;
    if (name !== undefined) this.myName = name;
    const res = await fetch(`/api/bullshit/game/${this.gameId}/play-again`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: this.myName }),
    });
    if (!res.ok) throw new HttpError(`Play again failed: ${res.status}`, res.status);
    const body = await res.json() as { playerId: number; token: string };
    this.bind(this.gameId, body.playerId, body.token);
    localStorage.setItem(tokenKey(this.gameId), JSON.stringify({ [body.playerId]: body.token }));
    await this.hydrate();
  }

  onResponse(response: BullshitResponse): void {
    if (this.left) return;
    if (response.eventType === 'REMOVED') {
      this.removedByHost();
      return;
    }
    if (response.success === false) {
      // Refused action (e.g. a bot change the server rejected): keep the state it carries, if any, and surface the error.
      if (response.state) this.applyView(response.state);
      this.callbacks.onEvent({ type: 'error', eventType: response.eventType, message: response.message });
      return;
    }
    if (response.state) {
      this.applyView(response.state);
    }
    this.callbacks.onEvent({ type: 'event', eventType: response.eventType, eventData: response.eventData, message: response.message });
  }
}
