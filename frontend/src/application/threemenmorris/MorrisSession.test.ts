import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import MorrisSession, { type MorrisSessionEvent, type MorrisWebSocketPort } from './MorrisSession';
import { morrisState } from '../../model/threemenmorris/morrisFixtures';
import type { LobbyView } from '../../model/lobby/LobbyView';

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: 'Bob', joined: true }],
    hostSeat: 0, mySeat: 1, minPlayers: 2, maxPlayers: 2, canStart: true, lobbyVersion: 5,
    ...overrides,
  };
}

function respond(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function makeSession() {
  const events: MorrisSessionEvent[] = [];
  const published: { dest: string; body?: string }[] = [];
  const seatSubs: { gameId: string; seat: number; token: string }[] = [];
  let seatFn: ((r: any) => void) | null = null;
  const webSocket: MorrisWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToSeat: (gameId, seat, token, onMessage) => { seatSubs.push({ gameId, seat, token }); seatFn = onMessage; },
    setPresence: () => {},
    unsubscribeFromSeat: () => {},
    unsubscribeFromGame: () => {},
    clearPresence: () => {},
  };
  const session = new MorrisSession(webSocket, { onEvent: e => events.push(e) });
  return { session, events, published, seatSubs, fireSeat: (r: any) => seatFn?.(r) };
}

describe('MorrisSession', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('create posts the three-men-morris game type and stores the token under its own key', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond({ gameId: 'g1', token: 'tok-0' }))
      .mockResolvedValueOnce(respond(lobbyView({ mySeat: 0 })));
    vi.stubGlobal('fetch', fetchMock);
    const { session, seatSubs } = makeSession();
    await session.create('Alice');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/lobby');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'three-men-morris', name: 'Alice' });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/three-men-morris/game/g1?token=tok-0');
    expect(seatSubs).toContainEqual({ gameId: 'g1', seat: 0, token: 'tok-0' });
    expect(JSON.parse(localStorage.getItem('three-men-morris:tokens:g1')!)).toEqual({ 0: 'tok-0' });
  });

  it('join and play again use the game REST base', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond({ playerId: 1, token: 'tok-1' }))
      .mockResolvedValueOnce(respond(lobbyView()))
      .mockResolvedValueOnce(respond({ playerId: 0, token: 'tok-r' }))
      .mockResolvedValueOnce(respond(lobbyView({ mySeat: 0, lobbyVersion: 1 })));
    vi.stubGlobal('fetch', fetchMock);
    const { session } = makeSession();
    await session.join('g1', 'Bob');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/three-men-morris/game/g1/join');
    await session.playAgain();
    expect(fetchMock.mock.calls[2][0]).toBe('/api/three-men-morris/game/g1/play-again');
    expect(JSON.parse(fetchMock.mock.calls[2][1].body)).toEqual({ name: 'Bob' });
  });

  it('H1: the lobby view of the GET carries mySeat and lobbyVersion, applied and versioned', async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(lobbyView({ mySeat: 0, lobbyVersion: 7 })));
    vi.stubGlobal('fetch', fetchMock);
    const { session, events, fireSeat } = makeSession();
    session.restore('g1', 1, 'tok-1');
    await session.hydrate();
    // The server moved the seat (a seat above was vacated): the session follows it.
    expect(events).toContainEqual({ type: 'seat-change', seat: 0 });
    expect(JSON.parse(localStorage.getItem('three-men-morris:tokens:g1')!)).toEqual({ 0: 'tok-1' });
    // An older lobby view (reordered frame) is dropped by its lobbyVersion.
    const before = events.length;
    fireSeat({ success: true, eventType: 'JOIN', eventData: {}, message: '', state: lobbyView({ mySeat: 0, lobbyVersion: 6 }) });
    expect(events.slice(before).some(e => e.type === 'state-update')).toBe(false);
  });

  it('hydrate reads a game state of the viewer', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond(morrisState())));
    const { session, events } = makeSession();
    session.restore('g1', 0, 'tok-0');
    expect(await session.hydrate()).toBe('ok');
    expect(events).toContainEqual({ type: 'state-update', state: morrisState() });
  });

  it('place publishes {gameId, token, point} to /app/three-men-morris/place', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.place(4);
    expect(published).toEqual([{ dest: '/app/three-men-morris/place', body: JSON.stringify({ gameId: 'g1', token: 'tok-0', point: 4 }) }]);
  });

  it('move publishes {gameId, token, from, to} to /app/three-men-morris/move', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.move(1, 2);
    expect(published).toEqual([{ dest: '/app/three-men-morris/move', body: JSON.stringify({ gameId: 'g1', token: 'tok-0', from: 1, to: 2 }) }]);
  });

  it('remove publishes {gameId, token, point} to /app/three-men-morris/remove', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.remove(12);
    expect(published).toEqual([{ dest: '/app/three-men-morris/remove', body: JSON.stringify({ gameId: 'g1', token: 'tok-0', point: 12 }) }]);
  });

  it.each([
    ['offerDraw', '/app/three-men-morris/offerDraw'],
    ['acceptDraw', '/app/three-men-morris/acceptDraw'],
    ['declineDraw', '/app/three-men-morris/declineDraw'],
  ] as const)('%s publishes {gameId, token} to %s', (command, destination) => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session[command]();
    expect(published).toEqual([{ dest: destination, body: JSON.stringify({ gameId: 'g1', token: 'tok-0' }) }]);
  });

  it('startGame and forfeit use the Morris start destination and the shared forfeit', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.startGame();
    session.forfeit();
    expect(published.map(p => p.dest)).toEqual(['/app/three-men-morris/start', '/app/forfeit']);
  });

  it('applies the state of a refused command and reports the error', () => {
    const { session, events, fireSeat } = makeSession();
    session.restore('g1', 0, 'tok-0');
    fireSeat({ success: false, eventType: 'PLACE', eventData: null, message: 'Point occupied', state: morrisState() });
    expect(events.map(e => e.type)).toEqual(expect.arrayContaining(['state-update', 'error']));
    expect(events).toContainEqual({ type: 'error', eventType: 'PLACE', message: 'Point occupied' });
  });
});
