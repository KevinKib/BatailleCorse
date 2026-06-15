import type Card from '../model/Card';
import type { BullshitState } from '../model/bullshit/BullshitState';
import type { BullshitResponse } from '../model/bullshit/BullshitEvents';

export interface BullshitWebSocketPort {
  publish(destination: string, body?: string): void;
  subscribeToSeat(gameId: string, seat: number, token: string, onMessage: (response: any) => void): void;
  setLobbyListener(fn: ((response: any) => void) | null): void;
}

export type BullshitSessionEvent =
  | { type: 'state-update'; state: BullshitState }
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

  create(name?: string): void {
    this.pendingCreate = true;
    this.webSocket.setLobbyListener(r => this.onLobby(r));
    this.webSocket.publish('/app/bullshit/create', JSON.stringify({ nbPlayers: 2, mode: 'MULTIPLAYER', name: name ?? null }));
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
    this.callbacks.onEvent({ type: 'game-id-change', gameId });
    this.callbacks.onEvent({ type: 'seat-change', seat });
  }

  async hydrate(): Promise<void> {
    if (!this.gameId || this.myToken === null) return;
    const res = await fetch(`/api/bullshit/game/${this.gameId}?token=${this.myToken}`);
    if (res.ok) {
      const state = await res.json() as BullshitState;
      this.callbacks.onEvent({ type: 'state-update', state });
    }
  }

  discard(cards: Card[]): void {
    this.webSocket.publish('/app/discard', JSON.stringify({ gameId: this.gameId, token: this.myToken, cards }));
  }

  callBullshit(): void {
    this.webSocket.publish('/app/callBullshit', JSON.stringify({ gameId: this.gameId, token: this.myToken }));
  }

  onResponse(response: BullshitResponse): void {
    if (response.state) {
      this.callbacks.onEvent({ type: 'state-update', state: response.state });
    }
    this.callbacks.onEvent({ type: 'event', eventType: response.eventType, eventData: response.eventData, message: response.message });
  }
}
