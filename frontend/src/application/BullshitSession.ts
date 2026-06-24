import type Card from '../model/Card';
import type { BullshitView } from '../model/bullshit/BullshitState';
import type { BullshitResponse } from '../model/bullshit/BullshitEvents';
import { DEFAULT_CLAIM_MODE, type ClaimMode } from '../model/bullshit/claimMode';

export interface BullshitWebSocketPort {
  publish(destination: string, body?: string): void;
  subscribeToSeat(gameId: string, seat: number, token: string, onMessage: (response: any) => void): void;
  setLobbyListener(fn: ((response: any) => void) | null): void;
  setPresence(body: string): void;
}

export type BullshitSessionEvent =
  | { type: 'state-update'; state: BullshitView }
  | { type: 'game-id-change'; gameId: string }
  | { type: 'seat-change'; seat: number }
  | { type: 'event'; eventType: string; eventData: unknown; message: string };

export interface BullshitSessionCallbacks {
  onEvent(event: BullshitSessionEvent): void;
}

export default class BullshitSession {
  private gameId: string | null = null;
  private myToken: string | null = null;
  private pendingCreate = false;

  constructor(
    private readonly webSocket: BullshitWebSocketPort,
    private readonly callbacks: BullshitSessionCallbacks,
  ) {}

  create(name?: string, claimMode: ClaimMode = DEFAULT_CLAIM_MODE): void {
    this.pendingCreate = true;
    this.webSocket.setLobbyListener(r => this.onLobby(r));
    this.webSocket.publish('/app/bullshit/create', JSON.stringify({ name: name ?? null, claimMode }));
  }

  private onLobby(response: any): void {
    if (!this.pendingCreate) return;
    if (response?.eventType !== 'CREATE') return;
    const data = response.eventData;
    if (!data || data.gameType !== 'bullshit') return;
    this.pendingCreate = false;
    this.webSocket.setLobbyListener(null);

    const gameId: string = data.gameId;
    const token: string = data.tokens['0'];
    this.bind(gameId, 0, token);
    localStorage.setItem(`bullshit:tokens:${gameId}`, JSON.stringify({ 0: token }));
    void this.hydrate();
  }

  async join(gameId: string, name?: string): Promise<void> {
    const res = await fetch(`/api/bullshit/game/${gameId}/join`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name ?? null }),
    });
    if (!res.ok) throw new Error(`Join failed: ${res.status}`);
    const body = await res.json() as { playerId: number; token: string };
    this.bind(gameId, body.playerId, body.token);
    localStorage.setItem(`bullshit:tokens:${gameId}`, JSON.stringify({ [body.playerId]: body.token }));
    await this.hydrate();
  }

  restore(gameId: string, seat: number, token: string): void {
    this.bind(gameId, seat, token);
  }

  private bind(gameId: string, seat: number, token: string): void {
    this.gameId = gameId;
    this.myToken = token;
    this.webSocket.subscribeToSeat(gameId, seat, token, r => this.onResponse(r));
    // Tell the server which seat this connection occupies, so a socket drop is
    // attributable to this seat (drives the disconnect grace timer + forfeit).
    // The WebSocketService re-asserts presence on reconnect.
    this.webSocket.setPresence(JSON.stringify({ gameId, token }));
    this.callbacks.onEvent({ type: 'game-id-change', gameId });
    this.callbacks.onEvent({ type: 'seat-change', seat });
  }

  async hydrate(): Promise<void> {
    if (!this.gameId || this.myToken === null) return;
    const res = await fetch(`/api/bullshit/game/${this.gameId}?token=${this.myToken}`);
    if (res.ok) {
      const state = await res.json() as BullshitView;
      this.callbacks.onEvent({ type: 'state-update', state });
    }
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

  async playAgain(name?: string): Promise<void> {
    if (!this.gameId) return;
    const res = await fetch(`/api/bullshit/game/${this.gameId}/play-again`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: name ?? null }),
    });
    if (!res.ok) throw new Error(`Play again failed: ${res.status}`);
    const body = await res.json() as { playerId: number; token: string };
    this.bind(this.gameId, body.playerId, body.token);
    localStorage.setItem(`bullshit:tokens:${this.gameId}`, JSON.stringify({ [body.playerId]: body.token }));
    await this.hydrate();
  }

  onResponse(response: BullshitResponse): void {
    if (response.state) {
      this.callbacks.onEvent({ type: 'state-update', state: response.state });
    }
    this.callbacks.onEvent({ type: 'event', eventType: response.eventType, eventData: response.eventData, message: response.message });
  }
}
