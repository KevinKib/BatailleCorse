import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import BlackjackSession, {
  BLACKJACK_SESSION_CONFIG, type BlackjackSessionEvent, type BlackjackWebSocketPort,
} from './BlackjackSession';
import type { BlackjackResponse, BlackjackView } from '../model/blackjack/BlackjackState';
import { blackjackBettingState } from '../model/blackjack/fixtures';

function respond(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function make() {
  const events: BlackjackSessionEvent[] = [];
  const published: { dest: string; body: Record<string, unknown> }[] = [];
  let seatFn: ((r: BlackjackResponse) => void) | null = null;
  const webSocket: BlackjackWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body: body ? JSON.parse(body) : {} }),
    subscribeToSeat: (_g, _s, _t, onMessage) => { seatFn = onMessage; },
    setPresence: () => {},
    unsubscribeFromSeat: () => {},
    unsubscribeFromGame: () => {},
    clearPresence: () => {},
  };
  const session = new BlackjackSession(webSocket, { onEvent: e => events.push(e) });
  const fire = (state: BlackjackView, eventType = 'BET', success = true) =>
    seatFn?.({ success, eventType, eventData: {}, message: 'm', state });
  return { session, events, published, fire };
}

describe('BlackjackSession', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it('is configured for the blackjack game type', () => {
    expect(BLACKJACK_SESSION_CONFIG).toEqual({
      gameType: 'blackjack',
      restBase: '/api/blackjack/game',
      tokenKeyPrefix: 'blackjack:tokens:',
      startDestination: '/app/blackjack/start',
    });
  });

  it('creates a room, keeps the token under blackjack:tokens: and hydrates from /api/blackjack/game', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond({ gameId: 'g1', token: 't0' }))
      .mockResolvedValueOnce(respond(blackjackBettingState));
    vi.stubGlobal('fetch', fetchMock);
    const { session } = make();
    await session.create('Ann');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'blackjack', name: 'Ann' });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/blackjack/game/g1?token=t0');
    expect(JSON.parse(localStorage.getItem('blackjack:tokens:g1')!)).toEqual({ 0: 't0' });
  });

  it('startGame publishes /app/blackjack/start', () => {
    const { session, published } = make();
    session.restore('g1', 0, 'tok');
    session.startGame();
    expect(published).toEqual([{ dest: '/app/blackjack/start', body: { gameId: 'g1', token: 'tok' } }]);
  });

  describe('commands', () => {
    const base = { gameId: 'g1', token: 'tok' };
    function bound() {
      const ctx = make();
      ctx.session.restore('g1', 0, 'tok');
      return ctx;
    }

    it('bet sends the amount to /app/blackjack/bet', () => {
      const c = bound(); c.session.bet(25);
      expect(c.published).toEqual([{ dest: '/app/blackjack/bet', body: { ...base, amount: 25 } }]);
    });

    it.each([
      ['hit', '/app/blackjack/hit'],
      ['stand', '/app/blackjack/stand'],
      ['doubleDown', '/app/blackjack/double'],
      ['split', '/app/blackjack/split'],
    ] as const)('%s sends the bare action to %s', (method, dest) => {
      const c = bound(); c.session[method]();
      expect(c.published).toEqual([{ dest, body: base }]);
    });

    it('forfeit publishes the shared /app/forfeit', () => {
      const c = bound(); c.session.forfeit();
      expect(c.published).toEqual([{ dest: '/app/forfeit', body: base }]);
    });
  });

  it('hands over the states of a seat subscription, newest version only', () => {
    const { session, events, fire } = make();
    session.restore('g1', 0, 'tok');
    fire({ ...blackjackBettingState, version: 6 });
    fire({ ...blackjackBettingState, version: 5 });
    const versions = events
      .filter((e): e is Extract<BlackjackSessionEvent, { type: 'state-update' }> => e.type === 'state-update')
      .map(e => (e.state as { version: number }).version);
    expect(versions).toEqual([6]);
  });
});
