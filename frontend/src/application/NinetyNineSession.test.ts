import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import NinetyNineSession, {
  NINETY_NINE_SESSION_CONFIG, type NinetyNineSessionEvent, type NinetyNineWebSocketPort,
} from './NinetyNineSession';
import type { NinetyNineResponse, NinetyNineView } from '../model/ninetynine/NinetyNineState';
import { buildNinetyNineCard, buildNinetyNineFourSeatRound } from '../model/fixtures/ninetynine';

function respond(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function make() {
  const events: NinetyNineSessionEvent[] = [];
  const published: { dest: string; body: Record<string, unknown> }[] = [];
  let seatFn: ((r: NinetyNineResponse) => void) | null = null;
  const webSocket: NinetyNineWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body: body ? JSON.parse(body) : {} }),
    subscribeToSeat: (_g, _s, _t, onMessage) => { seatFn = onMessage; },
    setPresence: () => {},
    unsubscribeFromSeat: () => {},
    unsubscribeFromGame: () => {},
    clearPresence: () => {},
  };
  const session = new NinetyNineSession(webSocket, { onEvent: e => events.push(e) });
  const fire = (state: NinetyNineView, eventType = 'PLAY', success = true) =>
    seatFn?.({ success, eventType, eventData: {}, message: 'm', state });
  return { session, events, published, fire };
}

describe('NinetyNineSession', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it('is configured for the ninety-nine game type', () => {
    expect(NINETY_NINE_SESSION_CONFIG).toEqual({
      gameType: 'ninety-nine',
      restBase: '/api/ninety-nine/game',
      tokenKeyPrefix: 'ninety-nine:tokens:',
      startDestination: '/app/ninety-nine/start',
    });
  });

  it('creates a room, keeps the token under ninety-nine:tokens: and hydrates from /api/ninety-nine/game', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(respond({ gameId: 'g1', token: 't0' }))
      .mockResolvedValueOnce(respond(buildNinetyNineFourSeatRound()));
    vi.stubGlobal('fetch', fetchMock);
    const { session } = make();
    await session.create('Ann');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'ninety-nine', name: 'Ann' });
    expect(fetchMock.mock.calls[1][0]).toBe('/api/ninety-nine/game/g1?token=t0');
    expect(JSON.parse(localStorage.getItem('ninety-nine:tokens:g1')!)).toEqual({ 0: 't0' });
  });

  it('startGame publishes /app/ninety-nine/start', () => {
    const { session, published } = make();
    session.restore('g1', 0, 'tok');
    session.startGame();
    expect(published).toEqual([{ dest: '/app/ninety-nine/start', body: { gameId: 'g1', token: 'tok' } }]);
  });

  describe('commands', () => {
    const base = { gameId: 'g1', token: 'tok' };
    function bound() {
      const ctx = make();
      ctx.session.restore('g1', 0, 'tok');
      return ctx;
    }

    it('play sends the card with a null Ace value for a plain card', () => {
      const c = bound();
      const seven = buildNinetyNineCard('7', 'HEART');
      c.session.play(seven);
      expect(c.published).toEqual([{ dest: '/app/ninety-nine/play', body: { ...base, card: seven, aceValue: null } }]);
    });

    it('play sends the chosen Ace value', () => {
      const c = bound();
      const ace = buildNinetyNineCard('ACE', 'SPADE');
      c.session.play(ace, 11);
      expect(c.published).toEqual([{ dest: '/app/ninety-nine/play', body: { ...base, card: ace, aceValue: 11 } }]);
    });

    it('forfeit publishes the shared /app/forfeit', () => {
      const c = bound(); c.session.forfeit();
      expect(c.published).toEqual([{ dest: '/app/forfeit', body: base }]);
    });
  });

  it('hands over the states of a seat subscription, newest version only', () => {
    const { session, events, fire } = make();
    session.restore('g1', 0, 'tok');
    fire({ ...buildNinetyNineFourSeatRound(), version: 6 });
    fire({ ...buildNinetyNineFourSeatRound(), version: 5 });
    const versions = events
      .filter((e): e is Extract<NinetyNineSessionEvent, { type: 'state-update' }> => e.type === 'state-update')
      .map(e => (e.state as { version: number }).version);
    expect(versions).toEqual([6]);
  });
});
