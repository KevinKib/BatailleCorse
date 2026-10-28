import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import KiloDeMerdeSession, {
  KILO_DE_MERDE_SESSION_CONFIG, type KiloDeMerdeSessionEvent, type KiloDeMerdeWebSocketPort,
} from './KiloDeMerdeSession';
import type { KiloDeMerdeResponse, KiloDeMerdeView } from '../model/kilodemerde/KiloDeMerdeState';
import { kiloDeMerdeStepState } from '../model/kilodemerde/fixtures';

function respond(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function make() {
  const events: KiloDeMerdeSessionEvent[] = [];
  const published: { dest: string; body: Record<string, unknown> }[] = [];
  let seatFn: ((r: KiloDeMerdeResponse) => void) | null = null;
  const webSocket: KiloDeMerdeWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body: body ? JSON.parse(body) : {} }),
    subscribeToSeat: (_g, _s, _t, onMessage) => { seatFn = onMessage; },
    setPresence: () => {},
    unsubscribeFromSeat: () => {},
    unsubscribeFromGame: () => {},
    clearPresence: () => {},
  };
  const session = new KiloDeMerdeSession(webSocket, { onEvent: e => events.push(e) });
  const fire = (state: KiloDeMerdeView, eventType = 'STEP', success = true) =>
    seatFn?.({ success, eventType, eventData: {}, message: 'm', state });
  return { session, events, published, fire };
}

describe('KiloDeMerdeSession', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it('is configured for the kilo-de-merde game type', () => {
    expect(KILO_DE_MERDE_SESSION_CONFIG).toEqual({
      gameType: 'kilo-de-merde',
      restBase: '/api/kilo-de-merde/game',
      tokenKeyPrefix: 'kilo-de-merde:tokens:',
      startDestination: '/app/kilo-de-merde/start',
    });
  });

  it('creates a room, keeps the token under kilo-de-merde:tokens: and hydrates from /api/kilo-de-merde/game', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond({ gameId: 'g1', token: 't0' }))
      .mockResolvedValueOnce(respond(kiloDeMerdeStepState));
    vi.stubGlobal('fetch', fetchMock);
    const { session } = make();
    await session.create('Ann');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'kilo-de-merde', name: 'Ann' });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/kilo-de-merde/game/g1?token=t0');
    expect(JSON.parse(localStorage.getItem('kilo-de-merde:tokens:g1')!)).toEqual({ 0: 't0' });
  });

  it('startGame publishes /app/kilo-de-merde/start', () => {
    const { session, published } = make();
    session.restore('g1', 0, 'tok');
    session.startGame();
    expect(published).toEqual([{ dest: '/app/kilo-de-merde/start', body: { gameId: 'g1', token: 'tok' } }]);
  });

  describe('commands', () => {
    const base = { gameId: 'g1', token: 'tok' };
    function bound() {
      const ctx = make();
      ctx.session.restore('g1', 0, 'tok');
      return ctx;
    }

    it('pass sends the chosen card to /app/kilo-de-merde/pass', () => {
      const c = bound();
      const card = kiloDeMerdeStepState.hand[2];
      c.session.pass(card);
      expect(c.published).toEqual([{ dest: '/app/kilo-de-merde/pass', body: { ...base, card } }]);
    });

    it('slap sends the bare action to /app/kilo-de-merde/slap', () => {
      const c = bound(); c.session.slap();
      expect(c.published).toEqual([{ dest: '/app/kilo-de-merde/slap', body: base }]);
    });

    it('forfeit publishes the shared /app/forfeit', () => {
      const c = bound(); c.session.forfeit();
      expect(c.published).toEqual([{ dest: '/app/forfeit', body: base }]);
    });
  });

  it('hands over the states of a seat subscription, newest version only', () => {
    const { session, events, fire } = make();
    session.restore('g1', 0, 'tok');
    fire({ ...kiloDeMerdeStepState, version: 6 });
    fire({ ...kiloDeMerdeStepState, version: 5 });
    const versions = events
      .filter((e): e is Extract<KiloDeMerdeSessionEvent, { type: 'state-update' }> => e.type === 'state-update')
      .map(e => (e.state as { version: number }).version);
    expect(versions).toEqual([6]);
  });
});
