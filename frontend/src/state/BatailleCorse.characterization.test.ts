import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';

vi.mock('../service/WebSocketService', () => ({
  default: {
    subscribeToGame: vi.fn(),
    unsubscribeFromGame: vi.fn(),
    subscribeToSeat: vi.fn(),
    unsubscribeFromSeat: vi.fn(),
    setPresence: vi.fn(),
    clearPresence: vi.fn(),
    publish: vi.fn(),
  },
}));

import webSocketService from '../service/WebSocketService';
import { useBatailleCorseStore } from './BatailleCorse.store';

/**
 * Characterisation of Bataille Corse's client as the screens see it: the store driven through its public
 * surface, with the WebSocket service and `fetch` as the only fakes. It pins what the session behind the store
 * does today (URLs, storage key, STOMP destinations, frame shapes, animation hand-shake, auto-grab timing) so
 * that the session can be swapped without any of it moving.
 */

const ws = vi.mocked(webSocketService);

function lobbyView(overrides: Record<string, unknown> = {}) {
  return {
    started: false, gameId: 'g1',
    players: [
      { seat: 0, name: 'Alice', joined: true, bot: false, seatOptions: {} },
      { seat: 1, name: null, joined: true, bot: true, seatOptions: { tier: '3' } },
    ],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 2, canStart: true, lobbyVersion: 1,
    ...overrides,
  };
}

/** A game state as the server sends it: no `started`, no `mySeat`, a `version`. */
function gameJson(overrides: Record<string, unknown> = {}) {
  return {
    id: 'g1',
    currentPlayer: { id: '0', nbCards: 26, availableActions: ['SEND'] },
    pile: { cards: [], grabbable: false, nbCardsSinceLastHonourCard: 0, playerThatAddedLastHonourCard: { id: '0' } },
    players: [
      { id: '0', nbCards: 26, availableActions: ['SEND'] },
      { id: '1', nbCards: 26, availableActions: [] },
    ],
    winner: null,
    version: 1,
    ...overrides,
  };
}

const card = (rank: string, suit = 'spade') => ({ rank, suit, name: `${rank} of ${suit}s` });

/** A frame of the game topic. */
function frame(eventType: string, eventData: unknown, state: unknown, extra: Record<string, unknown> = {}) {
  return { success: true, eventType, eventData, message: '', state, ...extra };
}

function respond(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

/** The callbacks the session registered on the seat channel at its last bind. */
function seatChannel() {
  const call = ws.subscribeToSeat.mock.calls.at(-1)!;
  return {
    gameId: call[0], seat: call[1], token: call[2],
    deliver: call[3] as (r: unknown) => void,
    connected: call[4] as () => void,
  };
}

const publishedTo = (dest: string) => ws.publish.mock.calls.filter(c => c[0] === dest);

type Store = ReturnType<typeof useBatailleCorseStore>;

/** Seats the store at `seat` of a game already running (the way a reload does), fetch answering the game. */
async function running(seat = 0, state: Record<string, unknown> = gameJson()): Promise<Store> {
  vi.stubGlobal('fetch', vi.fn(async (url: string) =>
    url.includes('/session') ? respond({ players: [] }) : respond(state)));
  const store = useBatailleCorseStore();
  store.restore('g1', seat, `tok-${seat}`);
  expect(await store.hydrate()).toBe('ok');
  ws.publish.mockClear();
  return store;
}

describe('Bataille Corse client (store + session characterisation)', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    Object.values(ws).forEach(fn => (fn as ReturnType<typeof vi.fn>).mockReset());
  });
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

  describe('creating, joining and the stored token', () => {
    it('create: POST /api/lobby, host seat 0, the one token stored under tokens:<gameId>, seat channel + game topic + presence', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond({ gameId: 'g1', token: 'host-tok' }))
        .mockResolvedValueOnce(respond(lobbyView()));
      vi.stubGlobal('fetch', fetchMock);
      const store = useBatailleCorseStore();

      expect(await store.create('Alice', true)).toBe('g1');

      expect(fetchMock.mock.calls[0][0]).toBe('/api/lobby');
      expect(fetchMock.mock.calls[0][1].method).toBe('POST');
      expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'bataille-corse', name: 'Alice', privateGame: true });
      expect(fetchMock.mock.calls[1][0]).toBe('/api/game/g1?token=host-tok');
      expect(JSON.parse(localStorage.getItem('tokens:g1')!)).toEqual({ 0: 'host-tok' });
      expect(ws.subscribeToSeat.mock.calls[0].slice(0, 3)).toEqual(['g1', 0, 'host-tok']);
      expect(ws.subscribeToGame).toHaveBeenCalledWith('g1');
      expect(JSON.parse(ws.setPresence.mock.calls[0][0])).toEqual({ gameId: 'g1', token: 'host-tok' });
      expect(store.gameId).toBe('g1');
      expect(store.myPlayerIndex).toBe(0);
      expect(store.phase).toBe('lobby');
      expect(store.seats).toEqual([
        { id: 0, name: 'Alice', joined: true, bot: false, seatOptions: {} },
        { id: 1, name: null, joined: true, bot: true, seatOptions: { tier: '3' } },
      ]);
    });

    it('create: a public lobby sends no privateGame field', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond({ gameId: 'g1', token: 't' }))
        .mockResolvedValueOnce(respond(lobbyView()));
      vi.stubGlobal('fetch', fetchMock);
      await useBatailleCorseStore().create('Alice');
      expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ gameType: 'bataille-corse', name: 'Alice' });
    });

    it('create: a refused creation rejects with the status and stores nothing', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(respond({}, 503)));
      await expect(useBatailleCorseStore().create('Alice')).rejects.toMatchObject({ status: 503 });
      expect(localStorage.length).toBe(0);
      expect(ws.subscribeToSeat).not.toHaveBeenCalled();
    });

    it('join: POST /api/game/<id>/join, the seat and token the server gives are stored alone', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond({ playerId: 1, token: 'guest-tok' }))
        .mockResolvedValueOnce(respond(lobbyView({ mySeat: 1 })));
      vi.stubGlobal('fetch', fetchMock);
      const store = useBatailleCorseStore();

      await store.join('g1', 'Bob');

      expect(fetchMock.mock.calls[0][0]).toBe('/api/game/g1/join');
      expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ name: 'Bob' });
      expect(JSON.parse(localStorage.getItem('tokens:g1')!)).toEqual({ 1: 'guest-tok' });
      expect(seatChannel()).toMatchObject({ gameId: 'g1', seat: 1, token: 'guest-tok' });
      expect(store.myPlayerIndex).toBe(1);
    });

    it('join: a refused join rejects and stores nothing', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(respond({}, 409)));
      await expect(useBatailleCorseStore().join('g1', 'Bob')).rejects.toBeTruthy();
      expect(localStorage.getItem('tokens:g1')).toBeNull();
    });

    it('restore: re-binds with the stored seat and token without touching storage or the network', () => {
      const fetchMock = vi.fn();
      vi.stubGlobal('fetch', fetchMock);
      localStorage.setItem('tokens:g1', JSON.stringify({ 1: 'old-tok' }));

      useBatailleCorseStore().restore('g1', 1, 'old-tok');

      expect(seatChannel()).toMatchObject({ gameId: 'g1', seat: 1, token: 'old-tok' });
      expect(ws.subscribeToGame).toHaveBeenCalledWith('g1');
      expect(fetchMock).not.toHaveBeenCalled();
      expect(JSON.parse(localStorage.getItem('tokens:g1')!)).toEqual({ 1: 'old-tok' });
    });

    it('the server moving my seat rewrites the stored entry under the same key, token unchanged', async () => {
      localStorage.setItem('tokens:g1', JSON.stringify({ 2: 'tok-2' }));
      const store = useBatailleCorseStore();
      store.restore('g1', 2, 'tok-2');
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond(lobbyView({ mySeat: 1 }))));

      await store.hydrate();

      expect(store.myPlayerIndex).toBe(1);
      expect(JSON.parse(localStorage.getItem('tokens:g1')!)).toEqual({ 1: 'tok-2' });
    });
  });

  describe('hydration and reconnection', () => {
    it('a running game loads as is, then its seats come from /api/game/<id>/session', async () => {
      const urls: string[] = [];
      vi.stubGlobal('fetch', vi.fn(async (url: string) => {
        urls.push(url);
        return url.includes('/session')
          ? respond({ players: [{ id: 0, name: 'Alice', joined: true }, { id: 1, name: null, joined: true, bot: true, seatOptions: { tier: '5' } }] })
          : respond(gameJson());
      }));
      const store = useBatailleCorseStore();
      store.restore('g1', 0, 'tok-0');

      expect(await store.hydrate()).toBe('ok');

      expect(urls).toEqual(['/api/game/g1?token=tok-0', '/api/game/g1/session']);
      expect(store.phase).toBe('playing');
      expect(store.state?.players).toHaveLength(2);
      expect(store.opponentSeat).toMatchObject({ id: 1, bot: true, seatOptions: { tier: '5' } });
    });

    it('a hydration answers gone (404), lost (403) or unavailable (5xx, network) without forgetting the token', async () => {
      const store = useBatailleCorseStore();
      store.restore('g1', 0, 'tok-0');
      localStorage.setItem('tokens:g1', JSON.stringify({ 0: 'tok-0' }));

      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond({}, 404)));
      expect(await store.hydrate()).toBe('gone');
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond({}, 403)));
      expect(await store.hydrate()).toBe('lost');
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond({}, 500)));
      expect(await store.hydrate()).toBe('unavailable');
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
      expect(await store.hydrate()).toBe('unavailable');

      expect(localStorage.getItem('tokens:g1')).not.toBeNull();
    });

    it('a late hydration older than the held game version is dropped', async () => {
      const store = await running(0, gameJson({ version: 5 }));
      store.onResponse(frame('SEND', { player: { id: '0' } }, gameJson({ version: 6, players: [
        { id: '0', nbCards: 20, availableActions: [] }, { id: '1', nbCards: 32, availableActions: [] }] })) as never);
      await vi.waitFor(() => expect(store.state?.players[0].nbCards).toBe(20));

      vi.stubGlobal('fetch', vi.fn(async (url: string) =>
        url.includes('/session') ? respond({ players: [] }) : respond(gameJson({ version: 4 }))));
      await store.hydrate();

      expect(store.state?.players[0].nbCards).toBe(20);
    });

    it('once the seat channel is live (every connection) the lobby or game is reloaded', async () => {
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond(lobbyView({ lobbyVersion: 1 })))
        .mockResolvedValueOnce(respond(lobbyView({ lobbyVersion: 2, canStart: false })));
      vi.stubGlobal('fetch', fetchMock);
      const store = useBatailleCorseStore();
      store.restore('g1', 0, 'tok-0');
      await store.hydrate();

      seatChannel().connected();

      await vi.waitFor(() => expect(store.lobby?.lobbyVersion).toBe(2));
      expect(store.lobby?.canStart).toBe(false);
    });

    it('a reconnection mid-animation does not replace the running game under it', async () => {
      const store = await running(0);
      const fetchMock = vi.fn(async (url: string) =>
        url.includes('/session') ? respond({ players: [] }) : respond(gameJson({ version: 9 })));
      vi.stubGlobal('fetch', fetchMock);
      // A GRAB frame holds the queue until the animation reports completion.
      store.onResponse(frame('GRAB', { player: { id: '1' } }, gameJson({ version: 2 })) as never);
      await vi.waitFor(() => expect(store.lastGrab).not.toBeNull());

      seatChannel().connected();
      await Promise.resolve();

      expect(fetchMock).not.toHaveBeenCalled();
      store.notifyAnimationComplete();
    });

    it('a reconnection that learns the seat is gone (403) tears down as removed', async () => {
      const store = await running(1);
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respond({}, 403)));
      localStorage.setItem('tokens:g1', JSON.stringify({ 1: 'tok-1' }));

      seatChannel().connected();

      await vi.waitFor(() => expect(store.removedByHost).toBe(true));
      expect(localStorage.getItem('tokens:g1')).toBeNull();
      expect(ws.unsubscribeFromSeat).toHaveBeenCalled();
      expect(ws.unsubscribeFromGame).toHaveBeenCalled();
      expect(ws.clearPresence).toHaveBeenCalled();
    });
  });

  describe('lobby commands (bots included)', () => {
    it('start publishes on /app/bataillecorse/start with the game id and my token', async () => {
      const store = await running(0, lobbyView());
      store.startGame();
      expect(publishedTo('/app/bataillecorse/start')).toHaveLength(1);
      expect(JSON.parse(publishedTo('/app/bataillecorse/start')[0][1]!)).toEqual({ gameId: 'g1', token: 'tok-0' });
    });

    it('add bot, remove bot, set the tier, rename, remove seat, set private and leave all carry my token', async () => {
      const store = await running(0, lobbyView());
      store.addBot();
      store.removeBot(1);
      store.setSeatOption(1, 'tier', '7');
      store.rename('Zed');
      store.removeSeat(1);
      store.setPrivate(true);
      store.leave();

      const bodies = ws.publish.mock.calls.map(c => ({ dest: c[0], ...JSON.parse(c[1]!) }));
      expect(bodies.map(b => b.dest)).toEqual([
        '/app/lobby/addBot', '/app/lobby/removeBot', '/app/lobby/setSeatOption', '/app/lobby/rename',
        '/app/lobby/removeSeat', '/app/lobby/setPrivate', '/app/lobby/leave',
      ]);
      bodies.forEach(b => expect(b).toMatchObject({ gameId: 'g1', token: 'tok-0' }));
    });

    it('a lobby view older than the one held is dropped, a newer one replaces the seats', async () => {
      const store = await running(0, lobbyView({ lobbyVersion: 3 }));
      seatChannel().deliver(frame('JOIN', null, lobbyView({ lobbyVersion: 2, canStart: false })));
      expect(store.lobby?.canStart).toBe(true);

      seatChannel().deliver(frame('JOIN', null, lobbyView({
        lobbyVersion: 4,
        players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: 'Bob', joined: true }],
      })));

      expect(store.lobby?.lobbyVersion).toBe(4);
      expect(store.opponentSeat).toMatchObject({ id: 1, name: 'Bob', bot: false, seatOptions: {} });
    });

    it('a refused lobby command surfaces its message and keeps the lobby it carries', async () => {
      const store = await running(0, lobbyView());
      seatChannel().deliver({
        success: false, eventType: 'ADD_BOT', message: 'Room full', state: lobbyView({ lobbyVersion: 7 }),
      });
      expect(store.lobbyError).toBe('Room full');
      expect(store.lobby?.lobbyVersion).toBe(7);
    });

    it('a REMOVED frame tears the seat down: token forgotten, channels closed, the screen told', async () => {
      const store = await running(1, lobbyView({ mySeat: 1 }));
      localStorage.setItem('tokens:g1', JSON.stringify({ 1: 'tok-1' }));

      seatChannel().deliver({ success: true, eventType: 'REMOVED', message: '', state: null });

      expect(store.removedByHost).toBe(true);
      expect(store.gameId).toBeNull();
      expect(localStorage.getItem('tokens:g1')).toBeNull();
      expect(ws.unsubscribeFromSeat).toHaveBeenCalled();
      expect(ws.unsubscribeFromGame).toHaveBeenCalled();
      expect(ws.clearPresence).toHaveBeenCalled();
    });

    it('leave forgets the stored token and closes the channels; later frames are ignored', async () => {
      const store = await running(0, lobbyView());
      localStorage.setItem('tokens:g1', JSON.stringify({ 0: 'tok-0' }));
      const { deliver } = seatChannel();

      store.leave();
      deliver(frame('JOIN', null, lobbyView({ lobbyVersion: 99 })));

      expect(localStorage.getItem('tokens:g1')).toBeNull();
      expect(ws.unsubscribeFromSeat).toHaveBeenCalled();
      expect(ws.unsubscribeFromGame).toHaveBeenCalled();
      expect(ws.clearPresence).toHaveBeenCalled();
      expect(store.lobby).toBeNull();
    });
  });

  describe('send, slap, grab, forfeit', () => {
    it('send publishes /app/send with my token and shows my card at once (optimistic), with the top card of the pile', async () => {
      const top = card('K');
      const store = await running(0, gameJson({ pile: { cards: [top], grabbable: false, nbCardsSinceLastHonourCard: 0, playerThatAddedLastHonourCard: { id: '0' } } }));

      store.send();
      store.send();

      expect(JSON.parse(publishedTo('/app/send')[0][1]!)).toEqual({ gameId: 'g1', token: 'tok-0' });
      expect(store.lastSend).toMatchObject({ playerIndex: 0, seq: 2, topCard: top });
    });

    it('slap flashes at once and publishes /app/slap; each press advances the sequence', async () => {
      const store = await running(0);
      store.slap();
      expect(store.lastSlap).toEqual({ seq: 1 });
      store.slap();
      expect(store.lastSlap).toEqual({ seq: 2 });
      expect(publishedTo('/app/slap')).toHaveLength(2);
      expect(JSON.parse(publishedTo('/app/slap')[0][1]!)).toEqual({ gameId: 'g1', token: 'tok-0' });
    });

    it('forfeit publishes /app/forfeit with my token', async () => {
      const store = await running(0);
      store.forfeit();
      expect(JSON.parse(publishedTo('/app/forfeit')[0][1]!)).toEqual({ gameId: 'g1', token: 'tok-0' });
    });

    it('a failed publish (socket down) throws to the caller', async () => {
      const store = await running(0);
      ws.publish.mockImplementationOnce(() => { throw new Error('no STOMP connection'); });
      expect(() => store.send()).toThrow();
    });
  });

  describe('game topic frames', () => {
    it('an opponent SEND shows their card with the pre-send top card; my own echo shows nothing more', async () => {
      const top = card('Q', 'heart');
      const store = await running(1, gameJson({ pile: { cards: [top], grabbable: false, nbCardsSinceLastHonourCard: 0, playerThatAddedLastHonourCard: { id: '0' } } }));

      store.onResponse(frame('SEND', { player: { id: '0' } }, gameJson({ version: 2 })) as never);
      await vi.waitFor(() => expect(store.lastSend).toMatchObject({ playerIndex: 0, seq: 1, topCard: top }));

      store.send();
      store.onResponse(frame('SEND', { player: { id: '1' } }, gameJson({ version: 3 })) as never);
      await vi.waitFor(() => expect(store.state).toBeDefined());
      expect(store.lastSend).toMatchObject({ playerIndex: 1, seq: 2 });
    });

    it('a GRAB announces the winner with a snapshot of the pile, then waits for the animation before the next frame', async () => {
      const pile = [card('A'), card('7')];
      const store = await running(0, gameJson({ pile: { cards: pile, grabbable: true, nbCardsSinceLastHonourCard: 1, playerThatAddedLastHonourCard: { id: '1' } } }));
      const after = gameJson({ version: 2, players: [{ id: '0', nbCards: 28, availableActions: [] }, { id: '1', nbCards: 24, availableActions: [] }] });

      store.onResponse(frame('GRAB', { player: { id: '0' } }, after) as never);
      store.onResponse(frame('SEND', { player: { id: '1' } }, gameJson({ version: 3, players: [{ id: '0', nbCards: 1, availableActions: [] }, { id: '1', nbCards: 2, availableActions: [] }] })) as never);

      await vi.waitFor(() => expect(store.lastGrab).toMatchObject({ winnerPlayerIndex: 0, seq: 1, pileCards: pile }));
      expect(store.state?.players[0].nbCards).toBe(28);
      // The second frame waits for the animation to report completion.
      await new Promise(r => setTimeout(r, 20));
      expect(store.state?.players[0].nbCards).toBe(28);

      store.notifyAnimationComplete();
      await vi.waitFor(() => expect(store.state?.players[0].nbCards).toBe(1));
    });

    it('a successful SLAP and an erroneous SLAP announce themselves (and wait for the animation)', async () => {
      const pile = [card('J')];
      const store = await running(0, gameJson({ pile: { cards: pile, grabbable: false, nbCardsSinceLastHonourCard: 0, playerThatAddedLastHonourCard: { id: '0' } } }));

      store.onResponse(frame('SLAP', { isSuccessful: true, player: { id: '1' } }, gameJson({ version: 2 })) as never);
      await vi.waitFor(() => expect(store.lastSuccessfulSlap).toMatchObject({ winnerPlayerIndex: 1, seq: 1, pileCards: pile }));
      store.notifyAnimationComplete();

      store.onResponse(frame('SLAP', { isSuccessful: false, player: { id: '0' } }, gameJson({ version: 3 })) as never);
      await vi.waitFor(() => expect(store.lastErroneousSlap).toMatchObject({ playerIndex: 0, seq: 1 }));
      store.notifyAnimationComplete();
    });

    it('opponent presence frames reach the disconnect banner state verbatim', async () => {
      const store = await running(0);
      store.onResponse(frame('OPPONENT_DISCONNECTED', { disconnectedSeat: 1, deadlineEpochMs: 1_060_000 }, gameJson({ version: 2 })) as never);
      await vi.waitFor(() => expect(store.liveDisconnections).toEqual({ 1: 1_060_000 }));

      store.onResponse(frame('OPPONENT_RECONNECTED', { reconnectedSeat: 1 }, gameJson({ version: 3 })) as never);
      await vi.waitFor(() => expect(store.liveDisconnections).toEqual({}));
    });

    it('a frame older than the held state is dropped, an equal one applied', async () => {
      const store = await running(0, gameJson({ version: 5 }));
      const withCards = (n: number, version: number) => gameJson({ version, players: [{ id: '0', nbCards: n, availableActions: [] }, { id: '1', nbCards: 1, availableActions: [] }] });

      store.onResponse(frame('SEND', { player: { id: '1' } }, withCards(11, 4)) as never);
      store.onResponse(frame('SEND', { player: { id: '1' } }, withCards(12, 5)) as never);

      await vi.waitFor(() => expect(store.state?.players[0].nbCards).toBe(12));
    });

    it('a refusal on the seat channel keeps the game state it carries and shows no lobby error at the table', async () => {
      const store = await running(0, gameJson({ version: 5 }));
      seatChannel().deliver({
        success: false, eventType: 'SLAP', message: 'Not allowed',
        state: gameJson({ version: 5, players: [{ id: '0', nbCards: 9, availableActions: [] }, { id: '1', nbCards: 43, availableActions: [] }] }),
      });
      expect(store.state?.players[0].nbCards).toBe(9);
      expect(store.lobbyError).toBeNull();
      expect(store.phase).toBe('playing');
    });
  });

  describe('auto-grab', () => {
    const grabbableBy = (id: string, version: number) => frame('SEND', { player: { id: '1' } }, gameJson({
      version, pile: { cards: [card('A')], grabbable: true, nbCardsSinceLastHonourCard: 1, playerThatAddedLastHonourCard: { id } },
    }));

    it('my pile to collect is grabbed after exactly 1500 ms, with my token', async () => {
      const store = await running(0);
      vi.useFakeTimers();

      store.onResponse(grabbableBy('0', 2) as never);
      await vi.advanceTimersByTimeAsync(1499);
      expect(publishedTo('/app/grab')).toHaveLength(0);
      await vi.advanceTimersByTimeAsync(1);

      expect(publishedTo('/app/grab')).toHaveLength(1);
      expect(JSON.parse(publishedTo('/app/grab')[0][1]!)).toEqual({ gameId: 'g1', token: 'tok-0' });
    });

    it('the other seat is never grabbed for', async () => {
      const store = await running(0);
      vi.useFakeTimers();
      store.onResponse(grabbableBy('1', 2) as never);
      await vi.advanceTimersByTimeAsync(10_000);
      expect(publishedTo('/app/grab')).toHaveLength(0);
    });

    it('the next frame, or cancelAutoGrab, cancels the pending grab', async () => {
      const store = await running(0);
      vi.useFakeTimers();

      store.onResponse(grabbableBy('0', 2) as never);
      await vi.advanceTimersByTimeAsync(500);
      store.onResponse(frame('SEND', { player: { id: '1' } }, gameJson({ version: 3 })) as never);
      await vi.advanceTimersByTimeAsync(5000);
      expect(publishedTo('/app/grab')).toHaveLength(0);

      store.onResponse(grabbableBy('0', 4) as never);
      await vi.advanceTimersByTimeAsync(100);
      store.cancelAutoGrab();
      await vi.advanceTimersByTimeAsync(5000);
      expect(publishedTo('/app/grab')).toHaveLength(0);
    });

    it('leaving the room cancels the pending grab', async () => {
      const store = await running(0);
      vi.useFakeTimers();
      store.onResponse(grabbableBy('0', 2) as never);
      await vi.advanceTimersByTimeAsync(100);

      store.leave();
      await vi.advanceTimersByTimeAsync(5000);

      expect(publishedTo('/app/grab')).toHaveLength(0);
    });
  });

  describe('end of the game and Play again', () => {
    const over = (version: number) => gameJson({ version, winner: { id: '0' } });

    it('a state with a winner finishes the game; the next game\'s frames are ignored until I play again', async () => {
      const store = await running(0);
      store.onResponse(frame('SEND', { player: { id: '0' } }, over(2)) as never);
      await vi.waitFor(() => expect(store.phase).toBe('finished'));

      store.onResponse(frame('SEND', { player: { id: '1' } }, gameJson({ version: 3, players: [{ id: '0', nbCards: 3, availableActions: [] }, { id: '1', nbCards: 3, availableActions: [] }] })) as never);
      await new Promise(r => setTimeout(r, 20));

      expect(store.phase).toBe('finished');
      expect(store.state?.players[0].nbCards).toBe(26);
    });

    it('reloading into a finished game shows the end state', async () => {
      const store = await running(0, over(7));
      expect(store.phase).toBe('finished');
      expect(store.state?.isWinnerAt(0)).toBe(true);
    });

    it('a forfeit reason travels on the player of the finished game', async () => {
      const store = await running(0, gameJson({
        winner: { id: '0' },
        players: [{ id: '0', nbCards: 52, availableActions: [] }, { id: '1', nbCards: 0, availableActions: [], forfeitReason: 'DISCONNECTED' }],
      }));
      expect(store.state?.opponentForfeitReason(0)).toBe('DISCONNECTED');
    });

    it('Play again: POST /api/game/<id>/play-again with my name, the new token stored alone, rebound, lobby back, then a new game is played', async () => {
      const store = await running(0, over(7));
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond({ playerId: 1, token: 'new-tok' }))
        .mockResolvedValueOnce(respond(lobbyView({ mySeat: 1, lobbyVersion: 1 })));
      vi.stubGlobal('fetch', fetchMock);

      await store.playAgain('Alice');

      expect(fetchMock.mock.calls[0][0]).toBe('/api/game/g1/play-again');
      expect(fetchMock.mock.calls[0][1].method).toBe('POST');
      expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ name: 'Alice' });
      expect(JSON.parse(localStorage.getItem('tokens:g1')!)).toEqual({ 1: 'new-tok' });
      expect(seatChannel()).toMatchObject({ gameId: 'g1', seat: 1, token: 'new-tok' });
      expect(ws.subscribeToGame).toHaveBeenLastCalledWith('g1');
      expect(store.myPlayerIndex).toBe(1);
      expect(store.phase).toBe('lobby');
      expect(store.state).toBeUndefined();
      expect(store.rematch).toBe('idle');

      // The game that follows is played again: frames are accepted, from version zero.
      store.onResponse(frame('START', null, gameJson({ version: 0 })) as never);
      await vi.waitFor(() => expect(store.phase).toBe('playing'));
    });

    it('Play again without a name falls back on the name given at creation', async () => {
      vi.stubGlobal('fetch', vi.fn()
        .mockResolvedValueOnce(respond({ gameId: 'g1', token: 't' }))
        .mockResolvedValueOnce(respond(lobbyView())));
      const store = useBatailleCorseStore();
      await store.create('Alice');
      const fetchMock = vi.fn()
        .mockResolvedValueOnce(respond({ playerId: 0, token: 't2' }))
        .mockResolvedValueOnce(respond(lobbyView()));
      vi.stubGlobal('fetch', fetchMock);

      await store.playAgain();

      expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ name: 'Alice' });
    });

    it('a refused Play again leaves the rematch button failed and the finished game on screen', async () => {
      const store = await running(0, over(7));
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(respond({}, 409)));
      await store.playAgain();
      expect(store.rematch).toBe('failed');
      expect(store.phase).toBe('finished');
    });

    it('Play again with a bot: the bot seat and its tier come back with the lobby', async () => {
      const store = await running(0, over(7));
      vi.stubGlobal('fetch', vi.fn()
        .mockResolvedValueOnce(respond({ playerId: 0, token: 'again-tok' }))
        .mockResolvedValueOnce(respond(lobbyView())));

      await store.playAgain('Alice');

      expect(store.opponentSeat).toMatchObject({ id: 1, bot: true, seatOptions: { tier: '3' } });
    });
  });
});
