import { HttpError } from '../model/HttpError';
import { useLobbyActions } from '../composables/useLobbyActions';

export interface SeatGameWebSocketPort {
  publish(destination: string, body?: string): void;
  subscribeToSeat(
    gameId: string, seat: number, token: string, onMessage: (response: any) => void, onConnected?: () => void,
  ): void;
  setPresence(body: string): void;
  unsubscribeFromSeat(): void;
  unsubscribeFromGame(): void;
  clearPresence(): void;
}

/** What a seat-game view has to carry for the session to version it: the lobby view or the running game. */
export interface SeatGameView {
  started: boolean;
  lobbyVersion?: number;
  /** Version of a running game's state, when the game carries one (Kobo, Three Men's Morris). */
  version?: number;
  mySeat?: number;
}

/** The body of `POST /api/lobby` (the backend `LobbyCreatedDto`): the room and the host's seat token. */
export interface LobbyCreatedBody {
  gameId: string;
  token: string;
}

/** The body of a join or a Play again (the backend `JoinResponseDto`): the seat taken and its token. */
export interface JoinBody {
  playerId: number;
  token: string;
}

export interface SeatGameResponse<View> {
  success?: boolean;
  eventType: string;
  eventData?: unknown;
  message: string;
  state?: View | null;
}

export type SeatGameSessionEvent<View> =
  | { type: 'state-update'; state: View }
  | { type: 'game-id-change'; gameId: string }
  | { type: 'seat-change'; seat: number }
  | { type: 'error'; eventType: string; message: string }
  | { type: 'removed' }
  | { type: 'event'; eventType: string; eventData: unknown; message: string };

export interface SeatGameSessionCallbacks<View> {
  onEvent(event: SeatGameSessionEvent<View>): void;
}

/** What differs from one seat game to the next; everything else in the session is shared. */
export interface SeatGameSessionConfig {
  /** Sent to `POST /api/lobby` to create the room. */
  gameType: string;
  /** REST base of the game, without a trailing slash: `${restBase}/{id}`, `/join`, `/play-again`. */
  restBase: string;
  /** localStorage key prefix of the seat token: `${tokenKeyPrefix}{gameId}`. */
  tokenKeyPrefix: string;
  /** STOMP destination of the host's Start command. */
  startDestination: string;
}

/**
 * How a rehydration ended: the game is gone (404), the token no longer resolves (403), the server could not
 * answer (5xx, network: nothing is known, so nothing is forgotten), or all is well.
 */
export type HydrateResult = 'ok' | 'gone' | 'lost' | 'unavailable';

/**
 * The client side of a game played from a numbered seat with a seat token: create, join, rehydrate, the
 * lobby commands, leave, removed by the host and Play again. A game subclasses it for its own commands
 * (through `publishAction`) and gives its URLs and storage key as a config.
 */
export default class SeatGameSession<
  View extends SeatGameView,
  Resp extends SeatGameResponse<View> = SeatGameResponse<View>,
> {
  private gameId: string | null = null;
  private myToken: string | null = null;
  private mySeat: number | null = null;
  // Highest lobby version applied; null when none is held (a fresh bind, or a game state came in).
  private lobbyVersion: number | null = null;
  // Highest started-state version applied; null when none is held (a fresh bind, or a lobby view came in).
  private gameVersion: number | null = null;
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
    private readonly config: SeatGameSessionConfig,
    private readonly webSocket: SeatGameWebSocketPort,
    private readonly callbacks: SeatGameSessionCallbacks<View>,
  ) {}

  private tokenKey(gameId: string): string {
    return `${this.config.tokenKeyPrefix}${gameId}`;
  }

  /** Creates a lobby over REST (the host token goes back to this caller only) and sits at seat 0. */
  async create(name?: string, privateGame = false): Promise<string> {
    this.myName = name ?? null;
    const res = await fetch('/api/lobby', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ gameType: this.config.gameType, name: name ?? null, ...(privateGame ? { privateGame } : {}) }),
    });
    if (!res.ok) throw new HttpError(`Create failed: ${res.status}`, res.status);
    const body = await res.json() as LobbyCreatedBody;
    this.bind(body.gameId, 0, body.token);
    localStorage.setItem(this.tokenKey(body.gameId), JSON.stringify({ 0: body.token }));
    await this.hydrate();
    return body.gameId;
  }

  async join(gameId: string, name?: string): Promise<void> {
    this.myName = name ?? null;
    const res = await fetch(`${this.config.restBase}/${gameId}/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name ?? null }),
    });
    if (!res.ok) throw HttpError.fromResponse(res, 'Join failed');
    const body = await res.json() as JoinBody;
    this.bind(gameId, body.playerId, body.token);
    localStorage.setItem(this.tokenKey(gameId), JSON.stringify({ [body.playerId]: body.token }));
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
    this.gameVersion = null;
    this.started = false;
    this.left = false;
    // A view broadcast before the seat channel was live (between the REST load and the first connection, or
    // while offline) is lost for good: reload once it is, after every connection. The version guards in
    // `applyView` (and Kobo's) keep an older answer from overwriting a newer frame.
    this.webSocket.subscribeToSeat(gameId, seat, token, r => this.onResponse(r), () => { void this.onConnected(); });
    // Tell the server which seat this connection occupies, so a socket drop is
    // attributable to this seat (drives the disconnect grace timer + forfeit).
    // The WebSocketService re-asserts presence on reconnect.
    this.webSocket.setPresence(JSON.stringify({ gameId, token }));
    this.onBind(gameId, seat, token);
    this.callbacks.onEvent({ type: 'game-id-change', gameId });
    this.callbacks.onEvent({ type: 'seat-change', seat });
  }

  /**
   * The seat channel is live: reload what was broadcast before it was. A refusal (403) or a missing game (404)
   * means the removal of this seat was among the lost frames: tear down as for a REMOVED frame.
   */
  private async onConnected(): Promise<void> {
    if (!this.shouldHydrateOnConnect()) return;
    const result = await this.hydrate();
    if ((result === 'lost' || result === 'gone') && !this.left) this.removedByHost();
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
      res = await fetch(`${this.config.restBase}/${gameId}?token=${token}`);
    } catch {
      return 'unavailable';
    }
    // The player left (or took another seat) while the answer was on its way: it no longer concerns this client,
    // and a refusal is only the server having freed the seat we just gave up.
    if (this.gameId !== gameId || this.myToken !== token) return 'ok';
    if (res.status === 404) return 'gone';
    if (res.status === 403) return 'lost';
    if (!res.ok) return 'unavailable';
    const view = this.parseView(await res.json());
    if (this.applyView(view)) await this.onViewHydrated(view);
    return 'ok';
  }

  // --- Extension points -----------------------------------------------------------------------------------
  // Every hook below has a neutral default: a game that needs none of them is unaffected.

  /** Turns a server payload (REST answer or seat-channel state) into the session's view. */
  protected parseView(raw: unknown): View {
    return raw as View;
  }

  /** The session just bound to a seat (create, join, restore, Play again). */
  protected onBind(_gameId: string, _seat: number, _token: string): void {}

  /** The client left, or was removed from, the room. */
  protected onTearDown(): void {}

  /** False to skip the reload that follows a (re)connection of the seat channel. */
  protected shouldHydrateOnConnect(): boolean {
    return true;
  }

  /** A hydration just applied this view (not a stale one); the hydrate call resolves once it settles. */
  protected async onViewHydrated(_view: View): Promise<void> {}

  protected get isLeft(): boolean { return this.left; }
  protected get hasStarted(): boolean { return this.started; }
  protected get currentSeat(): number | null { return this.mySeat; }
  protected get currentGameId(): string | null { return this.gameId; }

  /** Whether a started state of this version is older than the one held (a late answer overtaken by a frame). */
  protected isStaleVersion(version: number | undefined): boolean {
    return version !== undefined && this.gameVersion !== null && version < this.gameVersion;
  }

  /**
   * Applies a view from the server: a lobby view no newer than the one held is dropped, and the seat the
   * server reports replaces ours (it moves when a seat above is vacated), in the store and in the stored entry.
   */
  protected applyView(view: View): boolean {
    if (this.left) return false;
    if (!view.started) {
      if (this.started) return false;
      const incoming = view.lobbyVersion;
      if (incoming !== undefined) {
        if (this.lobbyVersion !== null && incoming <= this.lobbyVersion) return false;
        this.lobbyVersion = incoming;
      }
    } else {
      // A started state older than the one held is a late hydration answer overtaken by a broadcast.
      if (view.version !== undefined) {
        if (this.isStaleVersion(view.version)) return false;
        this.gameVersion = view.version;
      }
      this.started = true;
      this.lobbyVersion = null;
    }
    const seat = view.mySeat;
    if (typeof seat === 'number' && seat !== this.mySeat) {
      this.mySeat = seat;
      if (this.gameId && this.myToken) {
        localStorage.setItem(this.tokenKey(this.gameId), JSON.stringify({ [seat]: this.myToken }));
      }
      this.callbacks.onEvent({ type: 'seat-change', seat });
    }
    this.callbacks.onEvent({ type: 'state-update', state: view });
    return true;
  }

  /**
   * Sends a game command to a STOMP destination with the game id and the seat token, plus the command's own
   * fields. Publishing throws while the socket is down.
   */
  protected publishAction(destination: string, extra: Record<string, unknown> = {}): void {
    this.webSocket.publish(destination, JSON.stringify({ gameId: this.gameId, token: this.myToken, ...extra }));
  }

  /** Resign the in-progress game (RESIGNED forfeit). Mirrors BatailleCorse's /app/forfeit. */
  forfeit(): void {
    this.publishAction('/app/forfeit');
  }

  startGame(): void {
    this.publishAction(this.config.startDestination);
  }

  /** Host only: seat a computer-controlled player. The server answers with a lobby refresh. */
  addBot(): void { this.lobby.addBot(); }

  /** Host only: free the bot seat (the server refuses when a human sits above it). */
  removeBot(seat: number): void { this.lobby.removeBot(seat); }

  /** Host only: remove another human from the lobby, connected or not. */
  removeSeat(seat: number): void { this.lobby.removeSeat(seat); }

  /** Host only: change one game option; the answer is the next lobby view. */
  setOption(key: string, value: string): void { this.lobby.setOption(key, value); }
  setPrivate(privateGame: boolean): void { this.lobby.setPrivate(privateGame); }

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
    this.onTearDown();
    this.webSocket.unsubscribeFromSeat();
    this.webSocket.unsubscribeFromGame();
    this.webSocket.clearPresence();
    localStorage.removeItem(this.tokenKey(gameId));
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
    this.onTearDown();
    this.webSocket.unsubscribeFromSeat();
    this.webSocket.unsubscribeFromGame();
    this.webSocket.clearPresence();
    if (gameId) localStorage.removeItem(this.tokenKey(gameId));
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
    const res = await fetch(`${this.config.restBase}/${this.gameId}/play-again`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: this.myName }),
    });
    if (!res.ok) throw new HttpError(`Play again failed: ${res.status}`, res.status);
    const body = await res.json() as JoinBody;
    this.bind(this.gameId, body.playerId, body.token);
    localStorage.setItem(this.tokenKey(this.gameId), JSON.stringify({ [body.playerId]: body.token }));
    await this.hydrate();
  }

  onResponse(response: Resp): void {
    if (this.left) return;
    if (response.eventType === 'REMOVED') {
      this.removedByHost();
      return;
    }
    if (response.success === false) {
      // Refused action (e.g. a bot change the server rejected): keep the state it carries, if any, and surface the error.
      if (response.state) this.applyView(this.parseView(response.state));
      this.callbacks.onEvent({ type: 'error', eventType: response.eventType, message: response.message });
      return;
    }
    if (response.state) {
      this.applyView(this.parseView(response.state));
    }
    this.callbacks.onEvent({ type: 'event', eventType: response.eventType, eventData: response.eventData, message: response.message });
  }
}
