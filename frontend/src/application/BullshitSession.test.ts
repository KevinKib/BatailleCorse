import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import BullshitSession, { type BullshitWebSocketPort, type BullshitSessionCallbacks, type BullshitSessionEvent } from './BullshitSession';
import type { BullshitState } from '../model/bullshit/BullshitState';
import type { LobbyView } from '../model/lobby/LobbyView';

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

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: 'Bob', joined: true }],
    hostSeat: 0, mySeat: 1, minPlayers: 2, maxPlayers: 6, canStart: true, lobbyVersion: 5,
    ...overrides,
  };
}

function respond(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function makeSession() {
  const events: BullshitSessionEvent[] = [];
  const published: { dest: string; body?: string }[] = [];
  const seatSubs: { gameId: string; seat: number; token: string }[] = [];
  const presences: string[] = [];
  const teardown: string[] = [];
  let seatFn: ((r: any) => void) | null = null;

  const webSocket: BullshitWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToSeat: (gameId, seat, token, onMessage) => { seatSubs.push({ gameId, seat, token }); seatFn = onMessage; },
    setPresence: (body) => presences.push(body),
    unsubscribeFromSeat: () => { teardown.push('seat'); seatFn = null; },
    unsubscribeFromGame: () => { teardown.push('game'); },
    clearPresence: () => { teardown.push('presence'); },
  };
  const callbacks: BullshitSessionCallbacks = { onEvent: (e) => events.push(e) };
  const session = new BullshitSession(webSocket, callbacks);
  return {
    session, events, published, seatSubs, presences, teardown,
    fireSeat: (r: any) => seatFn?.(r),
  };
}

describe('BullshitSession', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('create posts the name to /api/lobby, binds seat 0 with the returned token and persists it', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond({ gameId: 'g1', token: 'tok-0' }))
      .mockResolvedValueOnce(respond(lobbyView({ mySeat: 0 })));
    vi.stubGlobal('fetch', fetchMock);
    const { session, seatSubs, events } = makeSession();
    const id = await session.create('Alice');
    expect(id).toBe('g1');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/lobby');
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'POST' });
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'bullshit', name: 'Alice' });
    expect(seatSubs).toContainEqual({ gameId: 'g1', seat: 0, token: 'tok-0' });
    expect(JSON.parse(localStorage.getItem('bullshit:tokens:g1')!)).toEqual({ 0: 'tok-0' });
    expect(events.some(e => e.type === 'state-update')).toBe(true);
  });

  it('create without a name sends none, and a refusal throws without binding anything', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(respond({}, 500));
    vi.stubGlobal('fetch', fetchMock);
    const { session, seatSubs } = makeSession();
    await expect(session.create()).rejects.toThrow();
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'bullshit', name: null });
    expect(seatSubs).toHaveLength(0);
  });

  it('create never listens on the shared /topic/game, whose tokens used to leak', () => {
    const { session } = makeSession();
    expect((session as unknown as Record<string, unknown>).onLobby).toBeUndefined();
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

  it('playAgain posts to play-again, re-binds to the new seat, and hydrates', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ playerId: 0, token: 'new-tok' }) })
      .mockResolvedValueOnce({ ok: true, json: async () => sampleState() });
    vi.stubGlobal('fetch', fetchMock);
    const { session, seatSubs, events } = makeSession();
    session.restore('g1', 2, 'old-tok');
    await session.playAgain('Alice');
    expect(fetchMock.mock.calls[0][0]).toContain('/api/bullshit/game/g1/play-again');
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'POST' });
    expect(seatSubs).toContainEqual({ gameId: 'g1', seat: 0, token: 'new-tok' });
    expect(JSON.parse(localStorage.getItem('bullshit:tokens:g1')!)).toEqual({ 0: 'new-tok' });
    expect(events.some(e => e.type === 'seat-change' && (e as any).seat === 0)).toBe(true);
  });

  it('playAgain re-sends the name typed at join, so the rematch lobby keeps it', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ playerId: 1, token: 't1' }) })
      .mockResolvedValueOnce({ ok: true, json: async () => sampleState() })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ playerId: 0, token: 't2' }) })
      .mockResolvedValueOnce({ ok: true, json: async () => sampleState() });
    vi.stubGlobal('fetch', fetchMock);
    const { session } = makeSession();
    await session.join('g1', 'Bob');
    await session.playAgain();
    expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({ name: 'Bob' });
  });

  it('playAgain re-sends the name typed at create (host)', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond({ playerId: 0, token: 't1' }))
      .mockResolvedValueOnce(respond(sampleState()))
      .mockResolvedValueOnce(respond({ playerId: 0, token: 't2' }))
      .mockResolvedValueOnce(respond(sampleState()));
    vi.stubGlobal('fetch', fetchMock);
    const { session } = makeSession();
    await session.join('g1', 'Alice');
    await session.playAgain();
    expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({ name: 'Alice' });
  });

  it('join keeps the HTTP status of a refusal so the form can word a missing game', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(respond({}, 404)));
    const { session } = makeSession();
    await expect(session.join('gone', 'Bob')).rejects.toMatchObject({ status: 404 });
  });

  it('registers presence (gameId + token) when binding a seat, so a drop is attributable', () => {
    const { session, presences } = makeSession();
    session.restore('g1', 2, 'tok-2');
    expect(presences).toContainEqual(JSON.stringify({ gameId: 'g1', token: 'tok-2' }));
  });

  it('forfeit publishes to /app/forfeit with the gameId and token', () => {
    const { session, published } = makeSession();
    session.restore('g1', 1, 'tok-1');
    session.forfeit();
    expect(published).toContainEqual({ dest: '/app/forfeit', body: JSON.stringify({ gameId: 'g1', token: 'tok-1' }) });
  });

  it('addBot publishes to /app/lobby/addBot with the gameId and token', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.addBot();
    expect(published).toContainEqual({ dest: '/app/lobby/addBot', body: JSON.stringify({ gameId: 'g1', token: 'tok-0' }) });
  });

  it('removeBot publishes to /app/lobby/removeBot with the gameId, token and seat', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.removeBot(2);
    expect(published).toContainEqual({ dest: '/app/lobby/removeBot', body: JSON.stringify({ gameId: 'g1', token: 'tok-0', seat: 2 }) });
  });

  it('surfaces an ErrorResponse as an error event, without a regular event', () => {
    const { session, events, fireSeat } = makeSession();
    session.restore('g1', 0, 'tok-0');
    fireSeat({ success: false, eventType: 'JOIN', eventData: {}, message: 'Room is full', state: null });
    expect(events).toContainEqual({ type: 'error', eventType: 'JOIN', message: 'Room is full' });
    expect(events.some(e => e.type === 'event')).toBe(false);
  });

  it('applies the sender state carried by an ErrorResponse, then surfaces the error without a regular event', () => {
    const { session, events, fireSeat } = makeSession();
    session.restore('g1', 0, 'tok-0');
    fireSeat({ success: false, eventType: 'DISCARD', eventData: {}, message: 'Not the turn of player 0', state: sampleState() });
    const kinds = events.map(e => e.type);
    expect(kinds.indexOf('state-update')).toBeGreaterThanOrEqual(0);
    expect(kinds.indexOf('state-update')).toBeLessThan(kinds.indexOf('error'));
    expect(events).toContainEqual({ type: 'error', eventType: 'DISCARD', message: 'Not the turn of player 0' });
    expect(events.some(e => e.type === 'event')).toBe(false);
  });

  it('on an incoming seat message, emits state-update and the event', () => {
    const { session, events, fireSeat } = makeSession();
    session.restore('g1', 0, 'tok-0');
    fireSeat({ success: true, eventType: 'DISCARD', eventData: { claimantSeat: 0, claimedTargetLabel: 'ACE', count: 1 }, message: 'm', state: sampleState() });
    expect(events.some(e => e.type === 'state-update')).toBe(true);
    expect(events).toContainEqual({ type: 'event', eventType: 'DISCARD', eventData: { claimantSeat: 0, claimedTargetLabel: 'ACE', count: 1 }, message: 'm' });
  });

  it('setOption, setSeatOption and rename publish the shared lobby commands', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.setOption('deckSize', '52');
    session.setSeatOption(1, 'tier', '3');
    session.rename('Zed');
    expect(published.map(p => p.dest)).toEqual(['/app/lobby/setOptions', '/app/lobby/setSeatOption', '/app/lobby/rename']);
  });

  describe('hydrate', () => {
    it('answers ok and applies the view', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(respond(lobbyView())));
      const { session, events } = makeSession();
      session.restore('g1', 1, 'tok-1');
      expect(await session.hydrate()).toBe('ok');
      expect(events.some(e => e.type === 'state-update')).toBe(true);
    });

    it('answers unavailable, and applies nothing, when the server fails (5xx) or cannot be reached', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(respond({}, 500)).mockRejectedValueOnce(new TypeError('Failed to fetch')));
      const { session, events } = makeSession();
      session.restore('g1', 1, 'tok-1');
      expect(await session.hydrate()).toBe('unavailable');
      expect(await session.hydrate()).toBe('unavailable');
      expect(events.some(e => e.type === 'state-update')).toBe(false);
    });

    it('answers gone for a game the server does not know (404) and lost for a refused token (403)', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(respond({}, 404)).mockResolvedValueOnce(respond({}, 403)));
      const { session } = makeSession();
      session.restore('g1', 1, 'tok-1');
      expect(await session.hydrate()).toBe('gone');
      expect(await session.hydrate()).toBe('lost');
    });
  });

  describe('lobby versions', () => {
    it('ignores a lobby view whose version is lower than or equal to the one held', () => {
      const { session, events, fireSeat } = makeSession();
      session.restore('g1', 1, 'tok-1');
      fireSeat({ success: true, eventType: 'JOIN', eventData: {}, message: 'm', state: lobbyView({ lobbyVersion: 5 }) });
      fireSeat({ success: true, eventType: 'JOIN', eventData: {}, message: 'm', state: lobbyView({ lobbyVersion: 4, canStart: false }) });
      fireSeat({ success: true, eventType: 'JOIN', eventData: {}, message: 'm', state: lobbyView({ lobbyVersion: 5, canStart: false }) });
      const states = events.filter(e => e.type === 'state-update') as Extract<BullshitSessionEvent, { type: 'state-update' }>[];
      expect(states).toHaveLength(1);
      expect((states[0].state as LobbyView).canStart).toBe(true);
      fireSeat({ success: true, eventType: 'JOIN', eventData: {}, message: 'm', state: lobbyView({ lobbyVersion: 6, canStart: false }) });
      expect(events.filter(e => e.type === 'state-update')).toHaveLength(2);
    });

    it('ignores a lobby view once the game has started, until the next bind', () => {
      const { session, events, fireSeat } = makeSession();
      session.restore('g1', 1, 'tok-1');
      fireSeat({ success: true, eventType: 'JOIN', eventData: {}, message: 'm', state: lobbyView({ lobbyVersion: 9 }) });
      fireSeat({ success: true, eventType: 'START', eventData: {}, message: 'm', state: sampleState({ mySeat: 1 }) });
      fireSeat({ success: true, eventType: 'JOIN', eventData: {}, message: 'm', state: lobbyView({ lobbyVersion: 10 }) });
      expect(events.filter(e => e.type === 'state-update')).toHaveLength(2);
    });

    it('drops a late hydration GET of the lobby that arrives after the Start frame', async () => {
      let answer: (r: unknown) => void = () => {};
      vi.stubGlobal('fetch', vi.fn().mockReturnValueOnce(new Promise(resolve => { answer = resolve; })));
      const { session, events, fireSeat } = makeSession();
      session.restore('g1', 1, 'tok-1');
      const pending = session.hydrate();
      fireSeat({ success: true, eventType: 'START', eventData: {}, message: 'm', state: sampleState({ mySeat: 1 }) });
      answer(respond(lobbyView({ lobbyVersion: 3 })));
      await pending;
      const states = events.filter(e => e.type === 'state-update') as Extract<BullshitSessionEvent, { type: 'state-update' }>[];
      expect(states).toHaveLength(1);
      expect(states[0].state.started).toBe(true);
    });

    it('lets a reopened lobby through after a new bind (Play again), whatever its version', () => {
      const { session, events, fireSeat } = makeSession();
      session.restore('g1', 1, 'tok-1');
      fireSeat({ success: true, eventType: 'JOIN', eventData: {}, message: 'm', state: lobbyView({ lobbyVersion: 9 }) });
      fireSeat({ success: true, eventType: 'START', eventData: {}, message: 'm', state: sampleState({ mySeat: 1 }) });
      session.restore('g1', 1, 'tok-2');
      fireSeat({ success: true, eventType: 'JOIN', eventData: {}, message: 'm', state: lobbyView({ lobbyVersion: 1 }) });
      expect(events.filter(e => e.type === 'state-update')).toHaveLength(3);
    });

    it('does not trust the event type: a leave typed JOIN still applies by its state', () => {
      const { session, events, fireSeat } = makeSession();
      session.restore('g1', 1, 'tok-1');
      fireSeat({ success: true, eventType: 'JOIN', eventData: {}, message: 'A player left.', state: lobbyView({ lobbyVersion: 7, mySeat: 0 }) });
      expect(events.some(e => e.type === 'state-update')).toBe(true);
    });
  });

  describe('seat from the server', () => {
    it('takes mySeat from a lobby view, tells the store and rewrites the stored entry', () => {
      const { session, events, fireSeat } = makeSession();
      session.restore('g1', 1, 'tok-1');
      localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 1: 'tok-1' }));
      fireSeat({ success: true, eventType: 'JOIN', eventData: {}, message: 'm', state: lobbyView({ mySeat: 0, lobbyVersion: 8 }) });
      expect(events).toContainEqual({ type: 'seat-change', seat: 0 });
      expect(JSON.parse(localStorage.getItem('bullshit:tokens:g1')!)).toEqual({ 0: 'tok-1' });
    });

    it('takes mySeat from a game state as well', () => {
      const { session, events, fireSeat } = makeSession();
      session.restore('g1', 2, 'tok-2');
      fireSeat({ success: true, eventType: 'START', eventData: {}, message: 'm', state: sampleState({ mySeat: 1 }) });
      expect(events).toContainEqual({ type: 'seat-change', seat: 1 });
    });

    it('stays quiet when the server reports the seat already held', () => {
      const { session, events, fireSeat } = makeSession();
      session.restore('g1', 1, 'tok-1');
      events.length = 0;
      fireSeat({ success: true, eventType: 'JOIN', eventData: {}, message: 'm', state: lobbyView({ mySeat: 1 }) });
      expect(events.some(e => e.type === 'seat-change')).toBe(false);
    });
  });

  describe('removed by the host', () => {
    const removedNotice = { success: true, eventType: 'REMOVED', eventData: {}, message: 'You were removed from the lobby by the host', state: null };

    it('removeSeat publishes the seat with my token', () => {
      const { session, published } = makeSession();
      session.restore('g1', 0, 'tok-0');
      session.removeSeat(2);
      expect(published).toContainEqual({ dest: '/app/lobby/removeSeat', body: JSON.stringify({ gameId: 'g1', token: 'tok-0', seat: 2 }) });
    });

    it('a REMOVED notice tears the client down, forgets the token and reports it once', () => {
      const { session, events, teardown, fireSeat, published } = makeSession();
      session.restore('g1', 1, 'tok-1');
      localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 1: 'tok-1' }));
      events.length = 0;

      fireSeat(removedNotice);
      fireSeat(removedNotice);

      expect(events).toEqual([{ type: 'removed' }]);
      expect(teardown).toEqual(['seat', 'game', 'presence']);
      expect(localStorage.getItem('bullshit:tokens:g1')).toBeNull();
      expect(published.some(p => p.dest === '/app/lobby/leave')).toBe(false);
    });
  });

  describe('leave', () => {
    it('publishes /app/lobby/leave, then unsubscribes, clears presence and forgets the stored token', () => {
      const { session, published, teardown } = makeSession();
      session.restore('g1', 1, 'tok-1');
      localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 1: 'tok-1' }));
      session.leave();
      expect(published).toContainEqual({ dest: '/app/lobby/leave', body: JSON.stringify({ gameId: 'g1', token: 'tok-1' }) });
      expect(teardown).toEqual(['seat', 'game', 'presence']);
      expect(localStorage.getItem('bullshit:tokens:g1')).toBeNull();
    });

    it('ignores every frame that arrives after the departure', () => {
      const { session, events } = makeSession();
      session.restore('g1', 1, 'tok-1');
      session.leave();
      events.length = 0;
      session.onResponse({ success: true, eventType: 'JOIN', eventData: {}, message: 'm', state: lobbyView({ lobbyVersion: 99 }) } as any);
      session.onResponse({ success: false, eventType: 'JOIN', eventData: {}, message: 'Game already started', state: null } as any);
      expect(events).toEqual([]);
    });

    it('stays seated when the socket is down: the publish error reaches the caller and nothing is torn down', () => {
      const { session, teardown } = makeSession();
      session.restore('g1', 1, 'tok-1');
      localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 1: 'tok-1' }));
      (session as any).webSocket.publish = () => { throw new Error('no underlying STOMP connection'); };
      expect(() => session.leave()).toThrow();
      expect(teardown).toEqual([]);
      expect(localStorage.getItem('bullshit:tokens:g1')).not.toBeNull();
    });

    it('does not report a seat as lost when the answer of a hydration sent before the departure arrives after it', async () => {
      const { session } = makeSession();
      let answer!: (r: unknown) => void;
      vi.stubGlobal('fetch', vi.fn().mockReturnValueOnce(new Promise((resolve) => { answer = resolve; })));
      session.restore('g1', 1, 'tok-1');
      const hydration = session.hydrate();
      session.leave();
      // The server freed the seat when it handled the leave: the token no longer resolves.
      answer(respond({}, 403));
      expect(await hydration).toBe('ok');
    });

    it('does nothing before the player holds a seat', () => {
      const { session, published, teardown } = makeSession();
      session.leave();
      expect(published).toEqual([]);
      expect(teardown).toEqual([]);
    });
  });
});
