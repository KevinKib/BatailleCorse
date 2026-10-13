import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import SeatGameSession, {
  type SeatGameSessionConfig, type SeatGameSessionEvent, type SeatGameWebSocketPort,
} from './SeatGameSession';

interface FakeView { started: boolean; mySeat?: number; lobbyVersion?: number }
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
  const webSocket: SeatGameWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToSeat: (_gameId, _seat, _token, onMessage) => { seatFn = onMessage; },
    setPresence: () => {},
    unsubscribeFromSeat: () => { teardown.push('seat'); },
    unsubscribeFromGame: () => { teardown.push('game'); },
    clearPresence: () => { teardown.push('presence'); },
  };
  const session = new FakeSession(webSocket, e => events.push(e));
  return { session, events, published, teardown, fireSeat: (r: FakeResponse) => seatFn?.(r) };
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
