import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import SeatGameSession, {
  type SeatGameSessionConfig, type SeatGameSessionEvent, type SeatGameWebSocketPort,
} from './SeatGameSession';

interface FakeView { started: boolean; mySeat?: number; lobbyVersion?: number; version?: number }
interface FakeResponse { eventType: string; success?: boolean; message: string; eventData?: unknown; state?: FakeView }

// A game that is none of the real ones: every URL, destination and storage key comes from this config.
const FAKE: SeatGameSessionConfig = {
  gameType: 'fake',
  restBase: '/api/fake/game',
  tokenKeyPrefix: 'fake:tokens:',
  startDestination: '/app/fake/start',
};

function respond(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

class FakeSession extends SeatGameSession<FakeView, FakeResponse> {
  constructor(webSocket: SeatGameWebSocketPort, onEvent: (e: SeatGameSessionEvent<FakeView>) => void) {
    super(FAKE, webSocket, { onEvent });
  }
  act(extra: object) { this.publishAction('/app/fake/act', extra); }
}

function make() {
  const events: SeatGameSessionEvent<FakeView>[] = [];
  const published: { dest: string; body?: string }[] = [];
  const teardown: string[] = [];
  let seatFn: ((r: FakeResponse) => void) | null = null;
  let connectedFn: (() => void) | null = null;
  const webSocket: SeatGameWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToSeat: (_gameId, _seat, _token, onMessage, onConnected) => { seatFn = onMessage; connectedFn = onConnected ?? null; },
    setPresence: () => {},
    unsubscribeFromSeat: () => { teardown.push('seat'); },
    unsubscribeFromGame: () => { teardown.push('game'); },
    clearPresence: () => { teardown.push('presence'); },
  };
  const session = new FakeSession(webSocket, e => events.push(e));
  return {
    session, events, published, teardown, fireSeat: (r: FakeResponse) => seatFn?.(r),
    // The broker accepted the seat subscription (every connection, the first included).
    connect: () => connectedFn?.(),
  };
}

describe('SeatGameSession built from a configuration', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('create posts the configured game type and stores the token under the configured prefix', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond({ gameId: 'g1', token: 't0' }))
      .mockResolvedValueOnce(respond({ started: false, mySeat: 0, lobbyVersion: 1 }));
    vi.stubGlobal('fetch', fetchMock);
    const { session } = make();
    await session.create('Ann');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/lobby');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'fake', name: 'Ann' });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/fake/game/g1?token=t0');
    expect(JSON.parse(localStorage.getItem('fake:tokens:g1')!)).toEqual({ 0: 't0' });
    expect(localStorage.getItem('bullshit:tokens:g1')).toBeNull();
  });

  it('join and play again use the configured REST base', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond({ playerId: 1, token: 't1' }))
      .mockResolvedValueOnce(respond({ started: false, mySeat: 1, lobbyVersion: 1 }))
      .mockResolvedValueOnce(respond({ playerId: 0, token: 't2' }))
      .mockResolvedValueOnce(respond({ started: false, mySeat: 0, lobbyVersion: 1 }));
    vi.stubGlobal('fetch', fetchMock);
    const { session } = make();
    await session.join('g1', 'Bob');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/fake/game/g1/join');
    await session.playAgain();
    expect(fetchMock.mock.calls[2][0]).toBe('/api/fake/game/g1/play-again');
    expect(JSON.parse(localStorage.getItem('fake:tokens:g1')!)).toEqual({ 0: 't2' });
  });

  it('startGame, forfeit and game commands publish with the game id and the seat token', () => {
    const { session, published } = make();
    session.restore('g1', 0, 'tok');
    session.startGame();
    session.forfeit();
    session.act({ cards: [1] });
    expect(published).toEqual([
      { dest: '/app/fake/start', body: JSON.stringify({ gameId: 'g1', token: 'tok' }) },
      { dest: '/app/forfeit', body: JSON.stringify({ gameId: 'g1', token: 'tok' }) },
      { dest: '/app/fake/act', body: JSON.stringify({ gameId: 'g1', token: 'tok', cards: [1] }) },
    ]);
  });

  it('drops a lobby view no newer than the one held, and any lobby view once the game started', () => {
    const { session, events, fireSeat } = make();
    session.restore('g1', 0, 'tok');
    const updates = () => events.filter(e => e.type === 'state-update').length;
    fireSeat({ eventType: 'JOIN', message: '', state: { started: false, lobbyVersion: 3 } });
    fireSeat({ eventType: 'JOIN', message: '', state: { started: false, lobbyVersion: 3 } });
    expect(updates()).toBe(1);
    fireSeat({ eventType: 'START', message: '', state: { started: true } });
    fireSeat({ eventType: 'JOIN', message: '', state: { started: false, lobbyVersion: 9 } });
    expect(updates()).toBe(2);
  });

  it('removed by the host tears down, forgets the configured token and reports it', () => {
    const { session, events, teardown, fireSeat } = make();
    session.restore('g1', 1, 'tok');
    localStorage.setItem('fake:tokens:g1', '{"1":"tok"}');
    fireSeat({ eventType: 'REMOVED', message: '' });
    expect(events.at(-1)).toEqual({ type: 'removed' });
    expect(teardown).toEqual(['seat', 'game', 'presence']);
    expect(localStorage.getItem('fake:tokens:g1')).toBeNull();
  });

  it('leave publishes the Leave command then forgets the configured token', () => {
    const { session, published, teardown } = make();
    session.restore('g1', 1, 'tok');
    localStorage.setItem('fake:tokens:g1', '{"1":"tok"}');
    session.leave();
    expect(published).toHaveLength(1);
    expect(teardown).toEqual(['seat', 'game', 'presence']);
    expect(localStorage.getItem('fake:tokens:g1')).toBeNull();
  });
});


describe('SeatGameSession re-hydrates once the seat subscription is live', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  const lobby = (lobbyVersion: number): FakeView => ({ started: false, mySeat: 0, lobbyVersion });
  const stateUpdates = (events: SeatGameSessionEvent<FakeView>[]) =>
    events.filter(e => e.type === 'state-update').map(e => (e as { state: FakeView }).state.lobbyVersion);

  it('picks up a view broadcast between the REST hydrate and the first connection (v2 was lost)', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond(lobby(1)))
      .mockResolvedValueOnce(respond(lobby(2)));
    vi.stubGlobal('fetch', fetchMock);
    const { session, events, connect } = make();
    session.restore('g1', 0, 'tok');
    await session.hydrate();
    expect(stateUpdates(events)).toEqual([1]);

    // The server broadcast v2 while SockJS was connecting; nobody was subscribed to hear it.
    connect();
    await vi.waitFor(() => expect(stateUpdates(events)).toEqual([1, 2]));
  });

  it('does not overwrite a newer view with the older answer of the connection hydrate', async () => {
    let answer!: (r: unknown) => void;
    const fetchMock = vi.fn().mockImplementationOnce(() => new Promise(r => { answer = r; }));
    vi.stubGlobal('fetch', fetchMock);
    const { session, events, connect, fireSeat } = make();
    session.restore('g1', 0, 'tok');
    connect();
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled());
    fireSeat({ eventType: 'LOBBY', message: '', state: lobby(3) });
    answer(respond(lobby(2)));
    await new Promise(r => setTimeout(r, 0));
    expect(stateUpdates(events)).toEqual([3]);
  });

  it('does nothing once the player has left', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const { session, connect } = make();
    session.restore('g1', 0, 'tok');
    session.leave();
    connect();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('drops a started state older than the one held, and accepts the same version', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond({ started: true, mySeat: 0, version: 4 })));
    const { session, events, fireSeat } = make();
    session.restore('g1', 0, 'tok');
    fireSeat({ eventType: 'MOVE', message: '', state: { started: true, mySeat: 0, version: 5 } });
    await session.hydrate();
    expect(events.filter(e => e.type === 'state-update')).toHaveLength(1);
    fireSeat({ eventType: 'MOVE', message: '', state: { started: true, mySeat: 0, version: 5 } });
    expect(events.filter(e => e.type === 'state-update')).toHaveLength(2);
  });

  it('tears down as removed when the connection hydrate learns the seat is gone (REMOVED frame lost)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond({}, 403)));
    const { session, events, connect, teardown } = make();
    session.restore('g1', 1, 'tok');
    localStorage.setItem('fake:tokens:g1', JSON.stringify({ 1: 'tok' }));
    connect();
    await vi.waitFor(() => expect(events.some(e => e.type === 'removed')).toBe(true));
    expect(teardown).toContain('seat');
    expect(localStorage.getItem('fake:tokens:g1')).toBeNull();
  });
});
