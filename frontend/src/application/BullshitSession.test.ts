import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import BullshitSession, { type BullshitWebSocketPort, type BullshitSessionCallbacks, type BullshitSessionEvent } from './BullshitSession';
import type { BullshitState } from '../model/bullshit/BullshitState';

function sampleState(overrides: Partial<BullshitState> = {}): BullshitState {
  return {
    started: true,
    id: 'g1', gameType: 'bullshit',
    myHand: [{ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }],
    availableActions: ['DISCARD'],
    players: [
      { id: '0', handCount: 1, isCurrentPlayer: true },
      { id: '1', handCount: 1, isCurrentPlayer: false },
    ],
    currentTarget: { label: 'ACE' },
    discardPileSize: 0,
    table: { state: 'NO_CLAIM' },
    pendingWinner: { state: 'NONE' },
    outcome: { status: 'ONGOING' },
    ...overrides,
  };
}

function makeSession() {
  const events: BullshitSessionEvent[] = [];
  const published: { dest: string; body?: string }[] = [];
  const seatSubs: { gameId: string; seat: number; token: string }[] = [];
  let lobbyFn: ((r: any) => void) | null = null;
  let seatFn: ((r: any) => void) | null = null;

  const webSocket: BullshitWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToSeat: (gameId, seat, token, onMessage) => { seatSubs.push({ gameId, seat, token }); seatFn = onMessage; },
    setLobbyListener: (fn) => { lobbyFn = fn; },
  };
  const callbacks: BullshitSessionCallbacks = { onEvent: (e) => events.push(e) };
  const session = new BullshitSession(webSocket, callbacks);
  return {
    session, events, published, seatSubs,
    fireLobby: (r: any) => lobbyFn?.(r),
    fireSeat: (r: any) => seatFn?.(r),
  };
}

describe('BullshitSession', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('create publishes only the name and registers a lobby listener', () => {
    const { session, published } = makeSession();
    session.create('Alice');
    expect(published).toContainEqual({ dest: '/app/bullshit/create', body: JSON.stringify({ name: 'Alice' }) });
  });

  it('on its own CREATE ack, subscribes seat 0 with the token and persists it', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => sampleState() }));
    const { session, seatSubs, fireLobby } = makeSession();
    session.create('Alice');
    fireLobby({ eventType: 'CREATE', eventData: { gameId: 'g1', gameType: 'bullshit', tokens: { '0': 'tok-0' } } });
    await Promise.resolve(); await Promise.resolve();
    expect(seatSubs).toContainEqual({ gameId: 'g1', seat: 0, token: 'tok-0' });
    expect(JSON.parse(localStorage.getItem('bullshit:tokens:g1')!)).toEqual({ 0: 'tok-0' });
  });

  it('ignores a foreign (non-bullshit) CREATE on the lobby', () => {
    const { session, seatSubs, fireLobby } = makeSession();
    session.create('Alice');
    fireLobby({ eventType: 'CREATE', eventData: { game: { id: 'x' }, gameType: 'bataille-corse' } });
    expect(seatSubs).toHaveLength(0);
  });

  it('join posts to the bullshit join endpoint, subscribes its seat, and hydrates', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ playerId: 1, token: 'tok-1' }) })
      .mockResolvedValueOnce({ ok: true, json: async () => sampleState() });
    vi.stubGlobal('fetch', fetchMock);
    const { session, seatSubs, events } = makeSession();
    await session.join('g1', 'Bob');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/bullshit/game/g1/join');
    expect(seatSubs).toContainEqual({ gameId: 'g1', seat: 1, token: 'tok-1' });
    expect(events.some(e => e.type === 'state-update')).toBe(true);
  });

  it('discard publishes selected cards to /app/discard', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    const cards = [{ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }];
    session.discard(cards);
    expect(published).toContainEqual({ dest: '/app/discard', body: JSON.stringify({ gameId: 'g1', token: 'tok-0', cards }) });
  });

  it('callBullshit publishes to /app/callBullshit', () => {
    const { session, published } = makeSession();
    session.restore('g1', 1, 'tok-1');
    session.callBullshit();
    expect(published).toContainEqual({ dest: '/app/callBullshit', body: JSON.stringify({ gameId: 'g1', token: 'tok-1' }) });
  });

  it('startGame publishes to /app/bullshit/start with the gameId and token', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.startGame();
    expect(published).toContainEqual({ dest: '/app/bullshit/start', body: JSON.stringify({ gameId: 'g1', token: 'tok-0' }) });
  });

  it('rematch publishes to /app/bullshit/rematch with gameId and token', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.rematch();
    expect(published).toContainEqual({ dest: '/app/bullshit/rematch', body: JSON.stringify({ gameId: 'g1', token: 'tok-0' }) });
  });

  it('on an incoming seat message, emits state-update and the event', () => {
    const { session, events, fireSeat } = makeSession();
    session.restore('g1', 0, 'tok-0');
    fireSeat({ success: true, eventType: 'DISCARD', eventData: { claimantSeat: 0, claimedTargetLabel: 'ACE', count: 1 }, message: 'm', state: sampleState() });
    expect(events.some(e => e.type === 'state-update')).toBe(true);
    expect(events).toContainEqual({ type: 'event', eventType: 'DISCARD', eventData: { claimantSeat: 0, claimedTargetLabel: 'ACE', count: 1 }, message: 'm' });
  });
});
