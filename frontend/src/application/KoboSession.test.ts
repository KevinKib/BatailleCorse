import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import KoboSession, { type KoboWebSocketPort, type KoboSessionEvent } from './KoboSession';
import type { KoboResponse } from '../model/kobo/KoboEvents';
import type { LobbyView } from '../model/lobby/LobbyView';
import { buildKoboState } from '../model/fixtures/kobo';
import type { KoboState } from '../model/kobo/KoboState';

function respond(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1', players: [{ seat: 0, name: 'Ann', joined: true }, { seat: 1, name: null, joined: false }],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 6, canStart: false, lobbyVersion: 1, ...overrides,
  };
}

function make() {
  const events: KoboSessionEvent[] = [];
  const published: { dest: string; body: Record<string, unknown> }[] = [];
  let seatFn: ((r: KoboResponse) => void) | null = null;
  const webSocket: KoboWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body: body ? JSON.parse(body) : {} }),
    subscribeToSeat: (_g, _s, _t, onMessage) => { seatFn = onMessage; },
    setPresence: () => {},
    unsubscribeFromSeat: () => {},
    unsubscribeFromGame: () => {},
    clearPresence: () => {},
  };
  const session = new KoboSession(webSocket, { onEvent: e => events.push(e) });
  const fire = (state: KoboState | LobbyView, eventType = 'DRAW', success = true) =>
    seatFn?.({ success, eventType, eventData: {}, message: 'm', state });
  const states = () => events.filter((e): e is Extract<KoboSessionEvent, { type: 'state-update' }> => e.type === 'state-update')
    .map(e => e.state);
  return { session, events, published, fire, states };
}

describe('KoboSession', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  describe('plumbing', () => {
    it('creates a kobo lobby, keeps the token under kobo:tokens: and hydrates from /api/kobo/game', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond({ gameId: 'g1', token: 't0' }))
        .mockResolvedValueOnce(respond(lobbyView()));
      vi.stubGlobal('fetch', fetchMock);
      const { session } = make();
      await session.create('Ann');
      expect(fetchMock.mock.calls[0][0]).toBe('/api/lobby');
      expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'kobo', name: 'Ann' });
      expect(fetchMock.mock.calls[1][0]).toBe('/api/kobo/game/g1?token=t0');
      expect(JSON.parse(localStorage.getItem('kobo:tokens:g1')!)).toEqual({ 0: 't0' });
    });

    it('joins through /api/kobo/game/{id}/join and plays again through /play-again', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond({ playerId: 1, token: 't1' }))
        .mockResolvedValueOnce(respond(lobbyView({ mySeat: 1 })))
        .mockResolvedValueOnce(respond({ playerId: 0, token: 't2' }))
        .mockResolvedValueOnce(respond(lobbyView({ mySeat: 0 })));
      vi.stubGlobal('fetch', fetchMock);
      const { session } = make();
      await session.join('g1', 'Bob');
      expect(fetchMock.mock.calls[0][0]).toBe('/api/kobo/game/g1/join');
      await session.playAgain();
      expect(fetchMock.mock.calls[2][0]).toBe('/api/kobo/game/g1/play-again');
    });

    it('startGame publishes /app/kobo/start', () => {
      const { session, published } = make();
      session.restore('g1', 0, 'tok');
      session.startGame();
      expect(published).toEqual([{ dest: '/app/kobo/start', body: { gameId: 'g1', token: 'tok' } }]);
    });
  });

  describe('commands', () => {
    function bound() {
      const ctx = make();
      ctx.session.restore('g1', 0, 'tok');
      return ctx;
    }
    const base = { gameId: 'g1', token: 'tok' };

    it('ready', () => { const c = bound(); c.session.ready(); expect(c.published).toEqual([{ dest: '/app/kobo/ready', body: base }]); });
    it('draw', () => { const c = bound(); c.session.draw(); expect(c.published).toEqual([{ dest: '/app/kobo/draw', body: base }]); });
    it('discardDrawn', () => { const c = bound(); c.session.discardDrawn(); expect(c.published).toEqual([{ dest: '/app/kobo/discardDrawn', body: base }]); });
    it('skipPower', () => { const c = bound(); c.session.skipPower(); expect(c.published).toEqual([{ dest: '/app/kobo/skipPower', body: base }]); });
    it('endTurn', () => { const c = bound(); c.session.endTurn(); expect(c.published).toEqual([{ dest: '/app/kobo/endTurn', body: base }]); });
    it('announce', () => { const c = bound(); c.session.announce(); expect(c.published).toEqual([{ dest: '/app/kobo/announce', body: base }]); });

    it('swapDrawn sends the slot', () => {
      const c = bound(); c.session.swapDrawn(2);
      expect(c.published).toEqual([{ dest: '/app/kobo/swapDrawn', body: { ...base, slot: 2 } }]);
    });
    it('kingSwap sends the slot', () => {
      const c = bound(); c.session.kingSwap(1);
      expect(c.published).toEqual([{ dest: '/app/kobo/kingSwap', body: { ...base, slot: 1 } }]);
    });
    it('peek sends the seat and the slot', () => {
      const c = bound(); c.session.peek(1, 3);
      expect(c.published).toEqual([{ dest: '/app/kobo/peek', body: { ...base, seat: 1, slot: 3 } }]);
    });
    it('blindSwap sends my slot then the target', () => {
      const c = bound(); c.session.blindSwap(0, 2, 1);
      expect(c.published).toEqual([{ dest: '/app/kobo/blindSwap', body: { ...base, ownSlot: 0, seat: 2, slot: 1 } }]);
    });
    it('giveTen sends the receiving seat', () => {
      const c = bound(); c.session.giveTen(1);
      expect(c.published).toEqual([{ dest: '/app/kobo/giveTen', body: { ...base, seat: 1 } }]);
    });

    it('match on my own card carries the expected rank and revision and no giveSlot', () => {
      const c = bound();
      c.session.match({ seat: 0, slot: 1, expectedTopRank: 'KING', expectedRevision: 5 });
      expect(c.published).toEqual([{
        dest: '/app/kobo/match', body: { ...base, seat: 0, slot: 1, expectedTopRank: 'KING', expectedRevision: 5 },
      }]);
      expect(c.published[0].body).not.toHaveProperty('giveSlot');
    });

    it('match on another player card carries giveSlot as well', () => {
      const c = bound();
      c.session.match({ seat: 1, slot: 0, giveSlot: 3, expectedTopRank: '10', expectedRevision: 0 });
      expect(c.published[0]).toEqual({
        dest: '/app/kobo/match',
        body: { ...base, seat: 1, slot: 0, giveSlot: 3, expectedTopRank: '10', expectedRevision: 0 },
      });
    });

    it('forfeit publishes the shared /app/forfeit', () => {
      const c = bound(); c.session.forfeit();
      expect(c.published).toEqual([{ dest: '/app/forfeit', body: base }]);
    });
  });

  describe('version guard', () => {
    it('drops a started state older than the one held and applies equal or newer ones', () => {
      const { session, fire, states } = make();
      session.restore('g1', 0, 'tok');
      fire(buildKoboState({ version: 5 }));
      fire(buildKoboState({ version: 4 }));
      fire(buildKoboState({ version: 5 }));
      fire(buildKoboState({ version: 6 }));
      expect(states().map(s => (s as KoboState).version)).toEqual([5, 5, 6]);
    });

    it('also drops an older state carried by a refused command', () => {
      const { session, fire, states, events } = make();
      session.restore('g1', 0, 'tok');
      fire(buildKoboState({ version: 5 }));
      fire(buildKoboState({ version: 3 }), 'MATCH', false);
      expect(states()).toHaveLength(1);
      expect(events.some(e => e.type === 'error')).toBe(true);
    });

    it('starts from zero again on a new bind (Play again restarts the aggregate at version 0)', async () => {
      vi.stubGlobal('fetch', vi.fn()
        .mockResolvedValueOnce(respond({ playerId: 0, token: 't2' }))
        .mockResolvedValueOnce(respond(lobbyView({ lobbyVersion: 1 }))));
      const { session, fire, states } = make();
      session.restore('g1', 0, 'tok');
      fire(buildKoboState({ version: 40, phase: 'FINISHED' }));
      await session.playAgain();
      fire(buildKoboState({ version: 1 }));
      expect(states().map(s => s.started ? (s as KoboState).version : 'lobby')).toEqual([40, 'lobby', 1]);
    });

    it('a lobby view holds no version: the game that follows the lobby starts from zero', () => {
      const { session, fire, states } = make();
      session.restore('g1', 0, 'tok');
      fire(lobbyView({ lobbyVersion: 9 }), 'JOIN');
      fire(buildKoboState({ version: 1 }), 'START');
      fire(buildKoboState({ version: 0 }), 'START');
      expect(states().map(s => (s.started ? (s as KoboState).version : 'lobby'))).toEqual(['lobby', 1]);
    });
  });
});
