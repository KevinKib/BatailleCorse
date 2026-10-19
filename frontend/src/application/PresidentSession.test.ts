import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import PresidentSession, { PRESIDENT_SESSION_CONFIG, type PresidentWebSocketPort, type PresidentSessionEvent } from './PresidentSession';
import type { PresidentResponse } from '../model/president/PresidentEvents';
import type { PresidentState } from '../model/president/PresidentState';
import type { LobbyView } from '../model/lobby/LobbyView';
import { buildPresidentCard, buildPresidentState } from '../model/fixtures/president';

function respond(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1', players: [{ seat: 0, name: 'Ann', joined: true }, { seat: 1, name: null, joined: false }],
    hostSeat: 0, mySeat: 0, minPlayers: 3, maxPlayers: 8, canStart: false, lobbyVersion: 1, ...overrides,
  };
}

function make() {
  const events: PresidentSessionEvent[] = [];
  const published: { dest: string; body: Record<string, unknown> }[] = [];
  let seatFn: ((r: PresidentResponse) => void) | null = null;
  let connectedFn: (() => void) | undefined;
  const webSocket: PresidentWebSocketPort = {
    publish: (dest, body) => published.push({ dest, body: body ? JSON.parse(body) : {} }),
    subscribeToSeat: (_g, _s, _t, onMessage, onConnected) => { seatFn = onMessage; connectedFn = onConnected; },
    setPresence: () => {},
    unsubscribeFromSeat: () => {},
    unsubscribeFromGame: () => {},
    clearPresence: () => {},
  };
  const session = new PresidentSession(webSocket, { onEvent: e => events.push(e) });
  const fire = (state: PresidentState | LobbyView, eventType = 'PLAY', success = true) =>
    seatFn?.({ success, eventType, eventData: {}, message: 'm', state });
  const connect = () => connectedFn?.();
  const states = () => events
    .filter((e): e is Extract<PresidentSessionEvent, { type: 'state-update' }> => e.type === 'state-update')
    .map(e => e.state);
  return { session, events, published, fire, connect, states };
}

describe('PresidentSession', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  describe('plumbing', () => {
    it('is configured for the president game type', () => {
      expect(PRESIDENT_SESSION_CONFIG).toEqual({
        gameType: 'president',
        restBase: '/api/president/game',
        tokenKeyPrefix: 'president:tokens:',
        startDestination: '/app/president/start',
      });
    });

    it('creates a president lobby, keeps the token under president:tokens: and hydrates from /api/president/game', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond({ gameId: 'g1', token: 't0' }))
        .mockResolvedValueOnce(respond(lobbyView()));
      vi.stubGlobal('fetch', fetchMock);
      const { session } = make();
      await session.create('Ann');
      expect(fetchMock.mock.calls[0][0]).toBe('/api/lobby');
      expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'president', name: 'Ann' });
      expect(fetchMock.mock.calls[1][0]).toBe('/api/president/game/g1?token=t0');
      expect(JSON.parse(localStorage.getItem('president:tokens:g1')!)).toEqual({ 0: 't0' });
    });

    it('joins through /api/president/game/{id}/join and plays again through /play-again', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond({ playerId: 1, token: 't1' }))
        .mockResolvedValueOnce(respond(lobbyView({ mySeat: 1 })))
        .mockResolvedValueOnce(respond({ playerId: 0, token: 't2' }))
        .mockResolvedValueOnce(respond(lobbyView({ mySeat: 0 })));
      vi.stubGlobal('fetch', fetchMock);
      const { session } = make();
      await session.join('g1', 'Bob');
      expect(fetchMock.mock.calls[0][0]).toBe('/api/president/game/g1/join');
      await session.playAgain();
      expect(fetchMock.mock.calls[2][0]).toBe('/api/president/game/g1/play-again');
    });

    it('startGame publishes /app/president/start', () => {
      const { session, published } = make();
      session.restore('g1', 0, 'tok');
      session.startGame();
      expect(published).toEqual([{ dest: '/app/president/start', body: { gameId: 'g1', token: 'tok' } }]);
    });

    it('setOption goes through the shared lobby command', () => {
      const { session, published } = make();
      session.restore('g1', 0, 'tok');
      session.setOption('scoring', 'titles');
      expect(published).toHaveLength(1);
      expect(published[0].body).toMatchObject({ gameId: 'g1', token: 'tok', values: { scoring: 'titles' } });
    });
  });

  describe('commands', () => {
    function bound() {
      const ctx = make();
      ctx.session.restore('g1', 0, 'tok');
      return ctx;
    }
    const base = { gameId: 'g1', token: 'tok' };

    it('play sends the cards as the server names them', () => {
      const c = bound();
      const cards = [buildPresidentCard('9', 'HEART'), buildPresidentCard('9', 'SPADE')];
      c.session.play(cards);
      expect(c.published).toEqual([{ dest: '/app/president/play', body: { ...base, cards } }]);
    });

    it('pass', () => {
      const c = bound(); c.session.pass();
      expect(c.published).toEqual([{ dest: '/app/president/pass', body: base }]);
    });

    it('giveBack sends the cards given back', () => {
      const c = bound();
      const cards = [buildPresidentCard('3', 'CLUB')];
      c.session.giveBack(cards);
      expect(c.published).toEqual([{ dest: '/app/president/giveBack', body: { ...base, cards } }]);
    });

    it('ready', () => {
      const c = bound(); c.session.ready();
      expect(c.published).toEqual([{ dest: '/app/president/ready', body: base }]);
    });

    it('seeExchange sends the round whose exchange was seen', () => {
      const c = bound(); c.session.seeExchange(3);
      expect(c.published).toEqual([{ dest: '/app/president/seeExchange', body: { ...base, round: 3 } }]);
    });

    it('forfeit publishes the shared /app/forfeit', () => {
      const c = bound(); c.session.forfeit();
      expect(c.published).toEqual([{ dest: '/app/forfeit', body: base }]);
    });
  });

  describe('hydration on connection', () => {
    it('reloads the state from the REST endpoint every time the seat channel goes live', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond(buildPresidentState({ version: 3 })))
        .mockResolvedValueOnce(respond(buildPresidentState({ version: 4 })));
      vi.stubGlobal('fetch', fetchMock);
      const { session, connect, states } = make();
      session.restore('g1', 0, 'tok');
      connect();
      await vi.waitFor(() => expect(states()).toHaveLength(1));
      connect();
      await vi.waitFor(() => expect(states()).toHaveLength(2));
      expect(fetchMock.mock.calls.map(c => c[0])).toEqual(Array(2).fill('/api/president/game/g1?token=tok'));
      expect(states().map(s => (s as PresidentState).version)).toEqual([3, 4]);
    });

    it('a late hydration older than a frame already applied is dropped', async () => {
      const fetchMock = vi.fn().mockResolvedValueOnce(respond(buildPresidentState({ version: 2 })));
      vi.stubGlobal('fetch', fetchMock);
      const { session, connect, fire, states } = make();
      session.restore('g1', 0, 'tok');
      fire(buildPresidentState({ version: 5 }));
      connect();
      await vi.waitFor(() => expect(fetchMock).toHaveBeenCalled());
      await new Promise(r => setTimeout(r, 0));
      expect(states().map(s => (s as PresidentState).version)).toEqual([5]);
    });

    it('a refused token on connection tears the seat down as a removal', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(respond({}, 403)));
      const { session, connect, events } = make();
      session.restore('g1', 0, 'tok');
      connect();
      await vi.waitFor(() => expect(events.some(e => e.type === 'removed')).toBe(true));
    });
  });

  describe('version guard', () => {
    it('drops a started state older than the one held and applies equal or newer ones', () => {
      const { session, fire, states } = make();
      session.restore('g1', 0, 'tok');
      fire(buildPresidentState({ version: 5 }));
      fire(buildPresidentState({ version: 4 }));
      fire(buildPresidentState({ version: 5 }));
      fire(buildPresidentState({ version: 6 }));
      expect(states().map(s => (s as PresidentState).version)).toEqual([5, 5, 6]);
    });

    it('also drops an older state carried by a refused command, and still reports the refusal', () => {
      const { session, fire, states, events } = make();
      session.restore('g1', 0, 'tok');
      fire(buildPresidentState({ version: 5 }));
      fire(buildPresidentState({ version: 3 }), 'PLAY', false);
      expect(states()).toHaveLength(1);
      expect(events.some(e => e.type === 'error')).toBe(true);
    });

    it('starts from zero again on a new bind (Play again restarts the aggregate at version 0)', async () => {
      vi.stubGlobal('fetch', vi.fn()
        .mockResolvedValueOnce(respond({ playerId: 0, token: 't2' }))
        .mockResolvedValueOnce(respond(lobbyView({ lobbyVersion: 1 }))));
      const { session, fire, states } = make();
      session.restore('g1', 0, 'tok');
      fire(buildPresidentState({ version: 40, phase: 'FINISHED' }));
      await session.playAgain();
      fire(buildPresidentState({ version: 1 }));
      expect(states().map(s => (s.started ? (s as PresidentState).version : 'lobby'))).toEqual([40, 'lobby', 1]);
    });

    it('a lobby view holds no version: the game that follows the lobby starts from zero', () => {
      const { session, fire, states } = make();
      session.restore('g1', 0, 'tok');
      fire(lobbyView({ lobbyVersion: 9 }), 'JOIN');
      fire(buildPresidentState({ version: 1 }), 'START');
      fire(buildPresidentState({ version: 0 }), 'START');
      expect(states().map(s => (s.started ? (s as PresidentState).version : 'lobby'))).toEqual(['lobby', 1]);
    });
  });
});
