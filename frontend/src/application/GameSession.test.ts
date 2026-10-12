import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import GameSession, { type WebSocketPort, type GameSessionCallbacks } from './GameSession';
import type { GameEvent } from './GameEvent';
import type { LobbyView } from '../model/lobby/LobbyView';
import {
  buildGrabResponse,
  buildSlapResponse,
  buildGame,
  buildPile,
  buildCard,
  buildPlayer,
  buildResponse,
} from '../model/fixtures';

// ---------------------------------------------------------------------------
// Test harness
// ---------------------------------------------------------------------------

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'game-1',
    players: [
      { seat: 0, name: 'Alice', joined: true, bot: false, seatOptions: {} },
      { seat: 1, name: null, joined: true, bot: true, seatOptions: { tier: '3' } },
    ],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 2, canStart: true, lobbyVersion: 1,
    ...overrides,
  };
}

/** A game state as the server sends it (plain JSON). */
function gameJson(overrides: Record<string, unknown> = {}) {
  return {
    currentPlayer: { id: '0', nbCards: 26, availableActions: ['SEND'] },
    pile: { cards: [], grabbable: false, nbCardsSinceLastHonourCard: 0, playerThatAddedLastHonourCard: { id: '0' } },
    players: [
      { id: '0', nbCards: 26, availableActions: ['SEND'] },
      { id: '1', nbCards: 26, availableActions: [] },
    ],
    winner: null,
    ...overrides,
  };
}

function respond(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function makeSession(awaitAnimation: () => Promise<void> = () => Promise.resolve()) {
  const events: GameEvent[] = [];
  const published: { dest: string; body?: string }[] = [];
  const subscribed: string[] = [];
  const seatSubs: { gameId: string; seat: number; token: string }[] = [];
  const presence: string[] = [];
  const teardown: string[] = [];
  let seatFn: ((r: any) => void) | null = null;

  const webSocket: WebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToGame: (id) => subscribed.push(id),
    subscribeToSeat: (gameId, seat, token, onMessage) => { seatSubs.push({ gameId, seat, token }); seatFn = onMessage; },
    unsubscribeFromSeat: () => { teardown.push('seat'); seatFn = null; },
    unsubscribeFromGame: () => { teardown.push('game'); },
    setPresence: (body) => presence.push(body),
    clearPresence: () => { teardown.push('presence'); },
  };

  const callbacks: GameSessionCallbacks = {
    onEvent: (e) => events.push(e),
    awaitAnimation,
  };

  const session = new GameSession(webSocket, callbacks);

  return {
    session, events, published, subscribed, seatSubs, presence, teardown,
    fireSeat: (r: any) => seatFn?.(r),
  };
}

/** A session seated at `seat` of a running game (no network). */
function playing(seat = 0, state = buildGame()) {
  const harness = makeSession();
  harness.session.restore('game-1', seat, `tok-${seat}`);
  harness.session.hydrateGame(state);
  harness.events.length = 0;
  harness.published.length = 0;
  return harness;
}

const lastOf = <T extends GameEvent['type']>(events: GameEvent[], type: T) =>
  events.filter((e): e is Extract<GameEvent, { type: T }> => e.type === type).at(-1);

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('GameSession', () => {
  beforeEach(() => { localStorage.clear(); });
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });

  // --- Lobby: create, join, restore ----------------------------------------

  describe('create', () => {
    it('posts the name to /api/lobby, sits at seat 0 with the one returned token and shows the lobby', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond({ gameId: 'game-1', token: 'tok-0' }))
        .mockResolvedValueOnce(respond(lobbyView()));
      vi.stubGlobal('fetch', fetchMock);
      const { session, events, seatSubs, subscribed, presence, published } = makeSession();

      const id = await session.create('Alice');

      expect(id).toBe('game-1');
      expect(fetchMock.mock.calls[0][0]).toBe('/api/lobby');
      expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'bataille-corse', name: 'Alice' });
      expect(fetchMock.mock.calls[1][0]).toBe('/api/game/game-1?token=tok-0');
      expect(JSON.parse(localStorage.getItem('tokens:game-1')!)).toEqual({ 0: 'tok-0' });
      expect(seatSubs).toEqual([{ gameId: 'game-1', seat: 0, token: 'tok-0' }]);
      expect(subscribed).toEqual(['game-1']);
      expect(JSON.parse(presence[0])).toEqual({ gameId: 'game-1', token: 'tok-0' });
      expect(events).toContainEqual({ type: 'game-id-change', gameId: 'game-1' });
      expect(events).toContainEqual({ type: 'my-index-change', playerIndex: 0 });
      expect(lastOf(events, 'lobby-update')?.lobby.gameId).toBe('game-1');
      // Nothing goes over the old shared create channel any more.
      expect(published.some(p => p.dest === '/app/create')).toBe(false);
    });

    it('rejects with the HTTP status when the lobby cannot be created', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(respond({}, 500)));
      const { session } = makeSession();
      await expect(session.create('Alice')).rejects.toMatchObject({ status: 500 });
    });
  });

  describe('join', () => {
    it('claims the seat the server gives, stores its token alone and shows the lobby', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond({ playerId: 1, token: 'tok-1' }))
        .mockResolvedValueOnce(respond(lobbyView({ mySeat: 1 })));
      vi.stubGlobal('fetch', fetchMock);
      const { session, events, seatSubs } = makeSession();

      await session.join('game-1', 'Bob');

      expect(fetchMock.mock.calls[0][0]).toBe('/api/game/game-1/join');
      expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ name: 'Bob' });
      expect(JSON.parse(localStorage.getItem('tokens:game-1')!)).toEqual({ 1: 'tok-1' });
      expect(seatSubs).toEqual([{ gameId: 'game-1', seat: 1, token: 'tok-1' }]);
      expect(lastOf(events, 'my-index-change')).toEqual({ type: 'my-index-change', playerIndex: 1 });
      expect(lastOf(events, 'lobby-update')).toBeDefined();
    });

    it('rejects with the HTTP status of a refused join (404: the lobby is gone)', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(respond({}, 404)));
      const { session } = makeSession();
      await expect(session.join('game-1')).rejects.toMatchObject({ status: 404 });
    });
  });

  describe('hydrate', () => {
    it.each([[404, 'gone'], [403, 'lost'], [500, 'unavailable']] as const)('answers %s with %s', async (status, result) => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(respond({}, status)));
      const { session } = makeSession();
      session.restore('game-1', 0, 'tok-0');
      expect(await session.hydrate()).toBe(result);
    });

    it('answers unavailable when the server cannot be reached', async () => {
      vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new Error('offline')));
      const { session } = makeSession();
      session.restore('game-1', 0, 'tok-0');
      expect(await session.hydrate()).toBe('unavailable');
    });

    it('loads a running game, then its seats with the bot tier', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond(gameJson()))
        .mockResolvedValueOnce(respond({ players: [
          { id: 0, name: 'Alice', joined: true, bot: false, seatOptions: {} },
          { id: 1, name: null, joined: true, bot: true, seatOptions: { tier: '5' } },
        ] }));
      vi.stubGlobal('fetch', fetchMock);
      const { session, events } = makeSession();
      session.restore('game-1', 0, 'tok-0');

      expect(await session.hydrate()).toBe('ok');

      expect(fetchMock.mock.calls[1][0]).toBe('/api/game/game-1/session');
      expect(lastOf(events, 'state-update')?.state.players).toHaveLength(2);
      expect(lastOf(events, 'seats-change')?.seats[1]).toEqual(
        { id: 1, name: null, joined: true, bot: true, seatOptions: { tier: '5' } });
    });
  });

  // --- Lobby views on the seat channel --------------------------------------

  describe('lobby views', () => {
    it('apply the seats of the lobby, and drop a view older than the one held', () => {
      const { session, events, fireSeat } = makeSession();
      session.restore('game-1', 0, 'tok-0');

      fireSeat(buildResponse({ eventType: 'JOIN', state: lobbyView({ lobbyVersion: 3 }) as never }));
      fireSeat(buildResponse({ eventType: 'JOIN', state: lobbyView({ lobbyVersion: 2, canStart: false }) as never }));

      const lobbies = events.filter(e => e.type === 'lobby-update');
      expect(lobbies).toHaveLength(1);
      expect(lastOf(events, 'seats-change')?.seats.map(s => s.bot)).toEqual([false, true]);
    });

    it('take my seat from the server and rewrite the stored token entry when it moves', () => {
      const { session, events, fireSeat } = makeSession();
      session.restore('game-1', 1, 'tok-1');

      fireSeat(buildResponse({ eventType: 'JOIN', state: lobbyView({ mySeat: 0 }) as never }));

      expect(lastOf(events, 'my-index-change')).toEqual({ type: 'my-index-change', playerIndex: 0 });
      expect(JSON.parse(localStorage.getItem('tokens:game-1')!)).toEqual({ 0: 'tok-1' });
    });

    it('surface a refused command as an error, keeping the lobby it carries', () => {
      const { session, events, fireSeat } = makeSession();
      session.restore('game-1', 0, 'tok-0');

      fireSeat({ success: false, eventType: 'START', eventData: {}, message: 'Not enough players', state: null });

      expect(events).toContainEqual({ type: 'error', eventType: 'START', message: 'Not enough players' });
    });

    it('are ignored once the game state came in', async () => {
      const { session, events, fireSeat } = makeSession();
      session.restore('game-1', 0, 'tok-0');
      await session.onResponse(buildResponse({ eventType: 'START' as never, state: buildGame() }));
      events.length = 0;

      fireSeat(buildResponse({ eventType: 'JOIN', state: lobbyView({ lobbyVersion: 9 }) as never }));

      expect(events.find(e => e.type === 'lobby-update')).toBeUndefined();
    });
  });

  // --- Lobby commands --------------------------------------------------------

  describe('lobby commands', () => {
    it('start, add a bot and set its tier with my one token', () => {
      const { session, published } = makeSession();
      session.restore('game-1', 0, 'tok-0');

      session.startGame();
      session.addBot();
      session.setSeatOption(1, 'tier', '6');

      expect(published.map(p => p.dest)).toEqual(['/app/bataillecorse/start', '/app/lobby/addBot', '/app/lobby/setSeatOption']);
      expect(JSON.parse(published[0].body!)).toEqual({ gameId: 'game-1', token: 'tok-0' });
      expect(JSON.parse(published[2].body!)).toEqual({ gameId: 'game-1', token: 'tok-0', seat: 1, key: 'tier', value: '6' });
    });

    it('removeSeat publishes the seat with my token', () => {
      const { session, published } = makeSession();
      session.restore('game-1', 0, 'tok-0');
      session.removeSeat(2);
      expect(published.at(-1)).toEqual({ dest: '/app/lobby/removeSeat', body: JSON.stringify({ gameId: 'game-1', token: 'tok-0', seat: 2 }) });
    });

    it('a REMOVED notice tears the client down, forgets the token and reports it once', () => {
      const { session, teardown, events, fireSeat, published } = makeSession();
      session.restore('game-1', 1, 'tok-1');
      localStorage.setItem('tokens:game-1', JSON.stringify({ 1: 'tok-1' }));
      events.length = 0;
      const notice = buildResponse({ eventType: 'REMOVED' as never, state: null as never });

      fireSeat(notice);
      fireSeat(notice);

      expect(events.filter(e => e.type === 'removed')).toHaveLength(1);
      expect(teardown).toEqual(['seat', 'game', 'presence']);
      expect(localStorage.getItem('tokens:game-1')).toBeNull();
      expect(published.some(p => p.dest === '/app/lobby/leave')).toBe(false);
    });

    it('leave tears everything down and forgets the stored token; nothing that arrives afterwards counts', async () => {
      const { session, published, teardown, events, fireSeat } = makeSession();
      session.restore('game-1', 1, 'tok-1');
      localStorage.setItem('tokens:game-1', JSON.stringify({ 1: 'tok-1' }));

      session.leave();
      fireSeat(buildResponse({ eventType: 'JOIN', state: lobbyView({ lobbyVersion: 9 }) as never }));
      await session.onResponse(buildResponse({ eventType: 'START' as never, state: buildGame() }));

      expect(published.at(-1)?.dest).toBe('/app/lobby/leave');
      expect(teardown).toEqual(['seat', 'game', 'presence']);
      expect(localStorage.getItem('tokens:game-1')).toBeNull();
      expect(events.find(e => e.type === 'lobby-update' || e.type === 'state-update')).toBeUndefined();
    });
  });

  // --- Play again ------------------------------------------------------------

  describe('playAgain', () => {
    it('reopens the lobby through the REST endpoint and binds to the seat it gives', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond({ playerId: 0, token: 'tok-new' }))
        .mockResolvedValueOnce(respond(lobbyView({ lobbyVersion: 7 })));
      vi.stubGlobal('fetch', fetchMock);
      const { session, events, seatSubs } = playing(1);

      await session.playAgain('Bob');

      expect(fetchMock.mock.calls[0][0]).toBe('/api/game/game-1/play-again');
      expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ name: 'Bob' });
      expect(seatSubs.at(-1)).toEqual({ gameId: 'game-1', seat: 0, token: 'tok-new' });
      expect(JSON.parse(localStorage.getItem('tokens:game-1')!)).toEqual({ 0: 'tok-new' });
      expect(lastOf(events, 'lobby-update')?.lobby.lobbyVersion).toBe(7);
    });

    it('rejects when the server refuses it', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(respond({}, 409)));
      const { session } = playing(0);
      await expect(session.playAgain()).rejects.toMatchObject({ status: 409 });
    });

    it('a finished game ignores the states of the next one until I play again', async () => {
      const { session, events } = playing(1);
      await session.onResponse(buildResponse({ eventType: 'GRAB', state: buildGame({ winner: { id: '0' } }) }));
      events.length = 0;

      // The other player already played again and started a new game on the same id.
      await session.onResponse(buildResponse({ eventType: 'START' as never, state: buildGame() }));

      expect(events).toHaveLength(0);
    });
  });

  // --- Game topic ------------------------------------------------------------

  describe('refused action on the game topic', () => {
    it('keeps the state it carries and reports the error, without any animation', async () => {
      const { session, events } = playing(0);

      await session.onResponse({ ...buildResponse({ eventType: 'SLAP', state: buildGame() }), success: false, message: 'nope' });

      expect(events.map(e => e.type)).toEqual(['state-update', 'error']);
    });
  });

  describe('GRAB event', () => {
    it('emits grab event with a snapshot of the pile before state update', async () => {
      const cardBeforeGrab = buildCard({ rank: 'A', suit: 'spade' });
      const { session, events } = playing(0, buildGame({ pile: buildPile({ cards: [cardBeforeGrab] }) }));

      await session.onResponse(buildGrabResponse('0', buildGame({ pile: buildPile({ cards: [] }) })));

      const grabEvent = lastOf(events, 'grab');
      expect(grabEvent!.winnerPlayerIndex).toBe(0);
      expect(grabEvent!.pileCards).toContainEqual(cardBeforeGrab);
      expect(lastOf(events, 'state-update')).toBeDefined();
    });
  });

  describe('SLAP event', () => {
    it('emits successful-slap event with winner and pile snapshot', async () => {
      const cardInPile = buildCard({ rank: 'K' });
      const { session, events } = playing(0, buildGame({ pile: buildPile({ cards: [cardInPile] }) }));

      await session.onResponse(buildSlapResponse(true, '1'));

      const slapEvent = lastOf(events, 'successful-slap');
      expect(slapEvent!.winnerPlayerIndex).toBe(1);
      expect(slapEvent!.pileCards).toContainEqual(cardInPile);
    });

    it('emits erroneous-slap event with the slapper index', async () => {
      const { session, events } = playing(0);
      await session.onResponse(buildSlapResponse(false, '0'));
      expect(lastOf(events, 'erroneous-slap')!.playerIndex).toBe(0);
    });
  });

  // --- My actions ------------------------------------------------------------

  describe('my actions', () => {
    it('send, slap, grab and forfeit publish with my one token', () => {
      const { session, published } = playing(1);

      session.send();
      session.slap();
      session.grab();
      session.forfeit();

      expect(published.map(p => p.dest)).toEqual(['/app/send', '/app/slap', '/app/grab', '/app/forfeit']);
      for (const p of published) expect(JSON.parse(p.body!)).toEqual({ gameId: 'game-1', token: 'tok-1' });
    });

    it('send emits an optimistic send event for my seat with the top card snapshot', () => {
      const topCard = buildCard({ rank: 'Q', suit: 'diamond' });
      const { session, events } = playing(1, buildGame({ pile: buildPile({ cards: [topCard] }) }));

      session.send();
      session.send();

      const sends = events.filter((e): e is Extract<GameEvent, { type: 'send' }> => e.type === 'send');
      expect(sends[0]).toMatchObject({ playerIndex: 1, topCard });
      expect(sends[1].seq).toBe(sends[0].seq + 1);
    });

    it('slap emits a slap event immediately', () => {
      const { session, events } = playing(0);
      session.slap();
      expect(events).toContainEqual({ type: 'slap' });
    });
  });

  // --- Auto-grab timer ------------------------------------------------------

  describe('auto-grab timer', () => {
    const grabbableBy = (id: string) => buildResponse({
      eventType: 'SEND',
      state: buildGame({ pile: buildPile({ grabbable: true, playerThatAddedLastHonourCard: { id } }) }),
    });

    it('fires my grab after 1500 ms when the pile is mine to grab', async () => {
      vi.useFakeTimers();
      const { session, published } = playing(0);

      await session.onResponse(grabbableBy('0'));

      vi.advanceTimersByTime(1499);
      expect(published.some(p => p.dest === '/app/grab')).toBe(false);
      vi.advanceTimersByTime(1);
      expect(published.filter(p => p.dest === '/app/grab')).toHaveLength(1);
      expect(JSON.parse(published.at(-1)!.body!).token).toBe('tok-0');
    });

    it('never grabs for the other seat (a bot grabs for itself on the server)', async () => {
      vi.useFakeTimers();
      const { session, published } = playing(0);

      await session.onResponse(grabbableBy('1'));
      vi.advanceTimersByTime(5000);

      expect(published.some(p => p.dest === '/app/grab')).toBe(false);
    });

    it('cancels a pending auto-grab when the next event arrives, or on cancelAll', async () => {
      vi.useFakeTimers();
      const { session, published } = playing(0);

      await session.onResponse(grabbableBy('0'));
      vi.advanceTimersByTime(500);
      await session.onResponse(buildResponse({ eventType: 'SEND', state: buildGame() }));
      await session.onResponse(grabbableBy('0'));
      session.cancelAll();
      vi.advanceTimersByTime(5000);

      expect(published.some(p => p.dest === '/app/grab')).toBe(false);
    });
  });

  // --- SEND server response (opponent / echo dedup) -------------------------

  describe('SEND response', () => {
    it('emits a send event for the opponent, with the pre-send top card', async () => {
      const topCard = buildCard({ rank: 'K', suit: 'spade' });
      const { session, events } = playing(1, buildGame({ pile: buildPile({ cards: [topCard] }) }));

      await session.onResponse(buildResponse({ eventType: 'SEND', eventData: { player: { id: '0' } }, state: buildGame() }));

      expect(lastOf(events, 'send')).toMatchObject({ playerIndex: 0, topCard });
    });

    it('does not emit a second send event for my own send (the optimistic one already fired)', async () => {
      const { session, events } = playing(1, buildGame({ pile: buildPile({ cards: [buildCard()] }) }));

      session.send();
      await session.onResponse(buildResponse({ eventType: 'SEND', eventData: { player: { id: '1' } }, state: buildGame() }));

      expect(events.filter(e => e.type === 'send')).toHaveLength(1);
    });

    it('skips opponent send animations while catching up (queue backed up)', async () => {
      let releaseAnimation!: () => void;
      const animationGate = new Promise<void>(res => { releaseAnimation = res; });
      const { session, events } = makeSession(() => animationGate);
      session.restore('game-1', 1, 'tok-1');
      session.hydrateGame(buildGame({ players: [buildPlayer({ id: '0' }), buildPlayer({ id: '1' })] }));

      // A GRAB by the opponent blocks on awaitAnimation, letting the queue build.
      const drain = session.onResponse(buildGrabResponse('0', buildGame()));
      for (let i = 0; i < 4; i++) {
        session.onResponse(buildResponse({ eventType: 'SEND', eventData: { player: { id: '0' } }, state: buildGame() }));
      }
      releaseAnimation();
      await drain;

      // With threshold 3: the first SEND (3 still queued) is skipped, the rest emit.
      expect(events.filter(e => e.type === 'send')).toHaveLength(3);
    });
  });

  // --- Presence --------------------------------------------------------------

  describe('presence events', () => {
    it.each([
      ['OPPONENT_DISCONNECTED', { disconnectedSeat: 1, deadlineEpochMs: 1234 }],
      ['OPPONENT_RECONNECTED', { reconnectedSeat: 1 }],
    ] as const)('forwards %s verbatim', async (eventType, eventData) => {
      const { session, events } = playing(0);
      await session.onResponse(buildResponse({ eventType, eventData }));
      expect(events).toContainEqual({ type: 'presence-event', eventType, eventData });
    });
  });
});
