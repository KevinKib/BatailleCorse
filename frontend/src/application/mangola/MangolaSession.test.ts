import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import MangolaSession, { MANGOLA_SESSION_CONFIG, type MangolaSessionEvent, type MangolaWebSocketPort } from './MangolaSession';
import { mangolaState } from '../../model/mangola/mangolaFixtures';
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
  const events: MangolaSessionEvent[] = [];
  const published: { dest: string; body?: string }[] = [];
  const seatSubs: { gameId: string; seat: number; token: string }[] = [];
  let seatFn: ((r: any) => void) | null = null;
  let connectedFn: (() => void) | null = null;
  const webSocket: MangolaWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToSeat: (gameId, seat, token, onMessage, onConnected) => {
      seatSubs.push({ gameId, seat, token });
      seatFn = onMessage;
      connectedFn = onConnected ?? null;
    },
    setPresence: () => {},
    unsubscribeFromSeat: () => {},
    unsubscribeFromGame: () => {},
    clearPresence: () => {},
  };
  const session = new MangolaSession(webSocket, { onEvent: e => events.push(e) });
  return { session, events, published, seatSubs, fireSeat: (r: any) => seatFn?.(r), connect: () => connectedFn?.() };
}

const frame = (state: unknown, eventType = 'SOWN') => ({ success: true, eventType, eventData: {}, message: '', state });

describe('MangolaSession', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('config: the mangola game type, REST base, token key and start destination', () => {
    expect(MANGOLA_SESSION_CONFIG).toEqual({
      gameType: 'mangola',
      restBase: '/api/mangola/game',
      tokenKeyPrefix: 'mangola:tokens:',
      startDestination: '/app/mangola/start',
    });
  });

  it('create posts the mangola game type and stores the token under its own key', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond({ gameId: 'g1', token: 'tok-0' }))
      .mockResolvedValueOnce(respond(lobbyView({ mySeat: 0 })));
    vi.stubGlobal('fetch', fetchMock);
    const { session, seatSubs } = makeSession();
    await session.create('Alice');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'mangola', name: 'Alice' });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/mangola/game/g1?token=tok-0');
    expect(seatSubs).toContainEqual({ gameId: 'g1', seat: 0, token: 'tok-0' });
    expect(JSON.parse(localStorage.getItem('mangola:tokens:g1')!)).toEqual({ 0: 'tok-0' });
  });

  it('hydrate reads a game state of the viewer', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond(mangolaState())));
    const { session, events } = makeSession();
    session.restore('g1', 0, 'tok-0');
    expect(await session.hydrate()).toBe('ok');
    expect(events).toContainEqual({ type: 'state-update', state: mangolaState() });
  });

  it('reloads the state after every connection of the seat channel (a frame sent before it was live is lost)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(respond(mangolaState({ version: 3 })));
    vi.stubGlobal('fetch', fetchMock);
    const { session, events, connect } = makeSession();
    session.restore('g1', 0, 'tok-0');
    connect();
    await vi.waitFor(() => expect(events.some(e => e.type === 'state-update')).toBe(true));
    expect(fetchMock.mock.calls[0][0]).toBe('/api/mangola/game/g1?token=tok-0');
    connect(); // a reconnection
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  });

  it('drops a state older than the one held: a late hydration answer or a reordered frame', () => {
    const { session, events, fireSeat } = makeSession();
    session.restore('g1', 0, 'tok-0');
    fireSeat(frame(mangolaState({ version: 7 })));
    const before = events.filter(e => e.type === 'state-update').length;
    fireSeat(frame(mangolaState({ version: 6 })));
    expect(events.filter(e => e.type === 'state-update')).toHaveLength(before);
    fireSeat(frame(mangolaState({ version: 8 })));
    expect(events.filter(e => e.type === 'state-update')).toHaveLength(before + 1);
  });

  it('applies a state of the same version again (an echo is harmless, a refused command carries the current one)', () => {
    const { session, events, fireSeat } = makeSession();
    session.restore('g1', 0, 'tok-0');
    fireSeat(frame(mangolaState({ version: 7 })));
    fireSeat(frame(mangolaState({ version: 7 })));
    expect(events.filter(e => e.type === 'state-update')).toHaveLength(2);
  });

  it('arrange publishes {gameId, token, counts} to /app/mangola/arrange', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    const counts = [2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2];
    session.arrange(counts);
    expect(published).toEqual([{ dest: '/app/mangola/arrange', body: JSON.stringify({ gameId: 'g1', token: 'tok-0', counts }) }]);
  });

  it.each([
    ['confirmLayout', '/app/mangola/confirmLayout'],
    ['editLayout', '/app/mangola/editLayout'],
  ] as const)('%s publishes {gameId, token} to %s', (command, destination) => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session[command]();
    expect(published).toEqual([{ dest: destination, body: JSON.stringify({ gameId: 'g1', token: 'tok-0' }) }]);
  });

  it('sow publishes {gameId, token, pit} to /app/mangola/sow', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.sow(10);
    expect(published).toEqual([{ dest: '/app/mangola/sow', body: JSON.stringify({ gameId: 'g1', token: 'tok-0', pit: 10 }) }]);
  });

  it('startGame and forfeit use the Mangola start destination and the shared forfeit', () => {
    const { session, published } = makeSession();
    session.restore('g1', 0, 'tok-0');
    session.startGame();
    session.forfeit();
    expect(published.map(p => p.dest)).toEqual(['/app/mangola/start', '/app/forfeit']);
  });

  it('applies the state of a refused command and reports the error', () => {
    const { session, events, fireSeat } = makeSession();
    session.restore('g1', 0, 'tok-0');
    fireSeat({ success: false, eventType: 'SOWN', eventData: null, message: 'Not your pit', state: mangolaState() });
    expect(events).toContainEqual({ type: 'error', eventType: 'SOWN', message: 'Not your pit' });
    expect(events.some(e => e.type === 'state-update')).toBe(true);
  });
});
