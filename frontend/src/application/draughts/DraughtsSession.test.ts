import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import DraughtsSession, {
  DRAUGHTS_SESSION_CONFIG, type DraughtsSessionEvent, type DraughtsWebSocketPort,
} from './DraughtsSession';
import { draughtsOpeningState } from '../../model/draughts/fixtures';
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
  const events: DraughtsSessionEvent[] = [];
  const published: { dest: string; body?: string }[] = [];
  const seatSubs: { gameId: string; seat: number; token: string }[] = [];
  let seatFn: ((r: any) => void) | null = null;
  const webSocket: DraughtsWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToSeat: (gameId, seat, token, onMessage) => { seatSubs.push({ gameId, seat, token }); seatFn = onMessage; },
    setPresence: () => {},
    unsubscribeFromSeat: () => {},
    unsubscribeFromGame: () => {},
    clearPresence: () => {},
  };
  const session = new DraughtsSession(webSocket, { onEvent: e => events.push(e) });
  return { session, events, published, seatSubs, fireSeat: (r: any) => seatFn?.(r) };
}

describe('DraughtsSession', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('names the draughts destinations and token key', () => {
    expect(DRAUGHTS_SESSION_CONFIG).toEqual({
      gameType: 'draughts',
      restBase: '/api/draughts/game',
      tokenKeyPrefix: 'draughts:tokens:',
      startDestination: '/app/draughts/start',
    });
  });

  it('create posts the draughts game type and stores the token under its own key', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond({ gameId: 'g1', token: 'tok-0' }))
      .mockResolvedValueOnce(respond(lobbyView({ mySeat: 0 })));
    vi.stubGlobal('fetch', fetchMock);
    const { session, seatSubs } = makeSession();
    await session.create('Alice');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/lobby');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'draughts', name: 'Alice' });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/draughts/game/g1?token=tok-0');
    expect(seatSubs).toContainEqual({ gameId: 'g1', seat: 0, token: 'tok-0' });
    expect(JSON.parse(localStorage.getItem('draughts:tokens:g1')!)).toEqual({ 0: 'tok-0' });
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
    expect(fetchMock.mock.calls[0][0]).toBe('/api/draughts/game/g1/join');
    await session.playAgain();
    expect(fetchMock.mock.calls[2][0]).toBe('/api/draughts/game/g1/play-again');
  });

  it('hydrate reads a game state of the viewer', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond(draughtsOpeningState)));
    const { session, events } = makeSession();
    session.restore('g1', 0, 'tok-0');
    expect(await session.hydrate()).toBe('ok');
    expect(events).toContainEqual({ type: 'state-update', state: draughtsOpeningState });
  });

  it('move publishes {gameId, token, from, to, captures} to /app/draughts/move', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.move(46, 19, [37, 28]);
    expect(published).toEqual([{
      dest: '/app/draughts/move',
      body: JSON.stringify({ gameId: 'g1', token: 'tok-0', from: 46, to: 19, captures: [37, 28] }),
    }]);
  });

  it('move always sends the captured set, empty for a plain move', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.move(32, 28, []);
    expect(JSON.parse(published[0].body!)).toMatchObject({ from: 32, to: 28, captures: [] });
  });

  it.each([
    ['offerDraw', '/app/draughts/offerDraw'],
    ['acceptDraw', '/app/draughts/acceptDraw'],
    ['declineDraw', '/app/draughts/declineDraw'],
  ] as const)('%s publishes {gameId, token} to %s', (command, destination) => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session[command]();
    expect(published).toEqual([{ dest: destination, body: JSON.stringify({ gameId: 'g1', token: 'tok-0' }) }]);
  });

  it('startGame and forfeit use the draughts start destination and the shared forfeit', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.startGame();
    session.forfeit();
    expect(published.map(p => p.dest)).toEqual(['/app/draughts/start', '/app/forfeit']);
  });

  it('applies the state of a refused command and reports the error', () => {
    const { session, events, fireSeat } = makeSession();
    session.restore('g1', 0, 'tok-0');
    fireSeat({ success: false, eventType: 'MOVE', eventData: null, message: 'draughts.errors.illegalMove', state: draughtsOpeningState });
    expect(events).toContainEqual({ type: 'error', eventType: 'MOVE', message: 'draughts.errors.illegalMove' });
    expect(events.some(e => e.type === 'state-update')).toBe(true);
  });
});
