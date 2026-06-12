import { describe, it, expect, vi, afterEach } from 'vitest';
import GameSession, { type WebSocketPort, type GameSessionCallbacks } from './GameSession';
import AI from '../model/ai/AI';
import type { GameEvent } from './GameEvent';
import {
  buildCreateResponse,
  buildGrabResponse,
  buildSlapResponse,
  buildGame,
  buildPile,
  buildCard,
  buildResponse,
} from '../model/fixtures';

// ---------------------------------------------------------------------------
// Test harness
// ---------------------------------------------------------------------------

function makeSession() {
  const events: GameEvent[] = [];
  const published: { dest: string; body?: string }[] = [];
  const subscribed: string[] = [];

  const presence: string[] = [];
  const webSocket: WebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToGame: (id) => subscribed.push(id),
    setPresence: (body) => presence.push(body),
    clearPresence: () => { presence.length = 0; },
  };

  const callbacks: GameSessionCallbacks = {
    onEvent: (e) => events.push(e),
    awaitAnimation: () => Promise.resolve(),
  };

  const session = new GameSession(webSocket, callbacks, () => new AI(1, 0));

  return { session, events, published, subscribed, presence };
}

function buildRematchResponse(status: 'PENDING' | 'STARTED', requestedById: string) {
  return buildResponse({
    eventType: 'REMATCH',
    eventData: { status, requestedBy: { id: requestedById } },
  });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('GameSession', () => {
  afterEach(() => { vi.useRealTimers(); });

  // --- CREATE ---------------------------------------------------------------

  describe('CREATE event', () => {
    it('emits game-id-change and subscribes to per-game channel', async () => {
      const { session, events, subscribed } = makeSession();
      session.create('solo', 'Alice');
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a', 1: 'tok-b' }));
      expect(events).toContainEqual({ type: 'game-id-change', gameId: 'game-1' });
      expect(subscribed).toContain('game-1');
    });

    it('stores tokens in localStorage', async () => {
      const { session } = makeSession();
      session.create('solo');
      await session.onResponse(buildCreateResponse('game-42', { 0: 'tok-x', 1: 'tok-y' }));
      const stored = JSON.parse(localStorage.getItem('tokens:game-42')!);
      expect(stored).toEqual({ 0: 'tok-x', 1: 'tok-y' });
    });

    it('ignores CREATE events when no create is pending', async () => {
      const { session, events, subscribed } = makeSession();
      // no session.create() call — pendingCreate stays false
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a', 1: 'tok-b' }));
      expect(events.find(e => e.type === 'game-id-change')).toBeUndefined();
      expect(subscribed).toHaveLength(0);
    });
  });

  // --- Multiplayer mode -----------------------------------------------------

  describe('multiplayer mode', () => {
    it('create("multiplayer") announces waiting and sends MULTIPLAYER', () => {
      const { session, events, published } = makeSession();
      session.create('multiplayer');
      expect(events).toContainEqual({ type: 'mode-change', mode: 'multiplayer' });
      expect(events).toContainEqual({ type: 'waiting-change', waiting: true });
      expect(published).toContainEqual({ dest: '/app/create', body: JSON.stringify({ mode: 'MULTIPLAYER' }) });
    });

    it('create("solo") is not waiting and sends SOLO', () => {
      const { session, events, published } = makeSession();
      session.create('solo');
      expect(events).toContainEqual({ type: 'mode-change', mode: 'solo' });
      expect(events).toContainEqual({ type: 'waiting-change', waiting: false });
      expect(published).toContainEqual({ dest: '/app/create', body: JSON.stringify({ mode: 'SOLO' }) });
    });

    it('clears waiting when a JOIN event arrives', async () => {
      const { session, events } = makeSession();
      session.create('multiplayer');
      await session.onResponse(buildResponse({ eventType: 'JOIN', state: buildGame() }));
      const waitingChanges = events.filter(e => e.type === 'waiting-change');
      expect(waitingChanges.at(-1)).toEqual({ type: 'waiting-change', waiting: false });
    });

    it('restoreSession derives multiplayer perspective from a single token', () => {
      const { session, events } = makeSession();
      session.restoreSession({ 1: 'tok-1' });
      expect(events).toContainEqual({ type: 'mode-change', mode: 'multiplayer' });
      expect(events).toContainEqual({ type: 'my-index-change', playerIndex: 1 });
    });

    it('restoreSession derives solo perspective from both tokens', () => {
      const { session, events } = makeSession();
      session.restoreSession({ 0: 'tok-0', 1: 'tok-1' });
      expect(events).toContainEqual({ type: 'mode-change', mode: 'solo' });
      expect(events).toContainEqual({ type: 'my-index-change', playerIndex: 0 });
    });
  });

  // --- GRAB -----------------------------------------------------------------

  describe('GRAB event', () => {
    it('emits grab event with a snapshot of the pile before state update', async () => {
      const { session, events } = makeSession();
      const cardBeforeGrab = buildCard({ rank: 'A', suit: 'spade' });
      session.hydrate('game-1', buildGame({ pile: buildPile({ cards: [cardBeforeGrab] }) }));
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      // after the grab, the server clears the pile
      const response = buildGrabResponse('0', buildGame({ pile: buildPile({ cards: [] }) }));
      await session.onResponse(response);

      const grabEvent = events.find(e => e.type === 'grab') as Extract<GameEvent, { type: 'grab' }> | undefined;
      expect(grabEvent).toBeDefined();
      expect(grabEvent!.winnerPlayerIndex).toBe(0);
      expect(grabEvent!.pileCards).toContainEqual(cardBeforeGrab);
    });

    it('emits state-update after the grab', async () => {
      const { session, events } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });
      await session.onResponse(buildGrabResponse('0'));
      expect(events.find(e => e.type === 'state-update')).toBeDefined();
    });
  });

  // --- SLAP -----------------------------------------------------------------

  describe('SLAP event', () => {
    it('emits successful-slap event with winner and pile snapshot', async () => {
      const { session, events } = makeSession();
      const cardInPile = buildCard({ rank: 'K' });
      session.hydrate('game-1', buildGame({ pile: buildPile({ cards: [cardInPile] }) }));
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      await session.onResponse(buildSlapResponse(true, '1'));

      const slapEvent = events.find(e => e.type === 'successful-slap') as
        Extract<GameEvent, { type: 'successful-slap' }> | undefined;
      expect(slapEvent).toBeDefined();
      expect(slapEvent!.winnerPlayerIndex).toBe(1);
      expect(slapEvent!.pileCards).toContainEqual(cardInPile);
    });

    it('emits erroneous-slap event with the slapper index', async () => {
      const { session, events } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      await session.onResponse(buildSlapResponse(false, '0'));

      const errEvent = events.find(e => e.type === 'erroneous-slap') as
        Extract<GameEvent, { type: 'erroneous-slap' }> | undefined;
      expect(errEvent).toBeDefined();
      expect(errEvent!.playerIndex).toBe(0);
    });
  });

  // --- SEND / SLAP user actions ---------------------------------------------

  describe('send()', () => {
    it('publishes to /app/send with the correct token', () => {
      const { session, published } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      session.send(0);

      expect(published).toHaveLength(1);
      expect(published[0].dest).toBe('/app/send');
      expect(JSON.parse(published[0].body!).token).toBe('tok-a');
      expect(JSON.parse(published[0].body!).gameId).toBe('game-1');
    });

    it('emits a send event with topCard snapshot', () => {
      const { session, events } = makeSession();
      const topCard = buildCard({ rank: 'Q', suit: 'diamond' });
      session.hydrate('game-1', buildGame({ pile: buildPile({ cards: [topCard] }) }));
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      session.send(0);

      const sendEvent = events.find(e => e.type === 'send') as
        Extract<GameEvent, { type: 'send' }> | undefined;
      expect(sendEvent).toBeDefined();
      expect(sendEvent!.topCard).toEqual(topCard);
    });
  });

  describe('slap()', () => {
    it('publishes to /app/slap with the correct token', () => {
      const { session, published } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      session.slap(1);

      expect(published).toHaveLength(1);
      expect(published[0].dest).toBe('/app/slap');
      expect(JSON.parse(published[0].body!).token).toBe('tok-b');
    });

    it('emits a slap event immediately', () => {
      const { session, events } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      session.slap(0);

      expect(events).toContainEqual({ type: 'slap' });
    });
  });

  // --- Auto-grab timer ------------------------------------------------------

  describe('auto-grab timer', () => {
    it('fires grab after 1500 ms when pile becomes grabbable', async () => {
      vi.useFakeTimers();
      const { session, published } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      await session.onResponse(buildResponse({
        eventType: 'SEND',
        state: buildGame({
          pile: buildPile({ grabbable: true, playerThatAddedLastHonourCard: { id: '0' } }),
        }),
      }));

      vi.advanceTimersByTime(1499);
      expect(published.some(p => p.dest === '/app/grab')).toBe(false);

      vi.advanceTimersByTime(1);
      expect(published.some(p => p.dest === '/app/grab')).toBe(true);
    });

    it('cancels pending auto-grab when next event arrives', async () => {
      vi.useFakeTimers();
      const { session, published } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      // First event: pile is grabbable — timer starts
      await session.onResponse(buildResponse({
        eventType: 'SEND',
        state: buildGame({
          pile: buildPile({ grabbable: true, playerThatAddedLastHonourCard: { id: '0' } }),
        }),
      }));

      vi.advanceTimersByTime(500);

      // Second event: pile is no longer grabbable — timer is cancelled
      await session.onResponse(buildResponse({
        eventType: 'SEND',
        state: buildGame({ pile: buildPile({ grabbable: false }) }),
      }));

      vi.advanceTimersByTime(2000);
      expect(published.some(p => p.dest === '/app/grab')).toBe(false);
    });

    it('cancelAll stops a pending auto-grab', async () => {
      vi.useFakeTimers();
      const { session, published } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      await session.onResponse(buildResponse({
        eventType: 'SEND',
        state: buildGame({
          pile: buildPile({ grabbable: true, playerThatAddedLastHonourCard: { id: '0' } }),
        }),
      }));

      session.cancelAll();
      vi.advanceTimersByTime(2000);
      expect(published.some(p => p.dest === '/app/grab')).toBe(false);
    });
  });

  // --- Names and session view -----------------------------------------------

  describe('names and session view', () => {
    it('create("multiplayer", name) sends name in payload', () => {
      const { session, published } = makeSession();
      session.create('multiplayer', 'Alice');
      expect(published).toContainEqual({
        dest: '/app/create',
        body: JSON.stringify({ mode: 'MULTIPLAYER', name: 'Alice' }),
      });
    });

    it('emits my-name-change on create with a name', () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Alice');
      expect(events).toContainEqual({ type: 'my-name-change', name: 'Alice' });
    });

    it('applySessionView keeps waiting while opponent seat is not joined', () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Alice');
      session.applySessionView([
        { id: 0, name: 'Alice', joined: true },
        { id: 1, name: null, joined: false },
      ]);
      const waitingEvents = events.filter(e => e.type === 'waiting-change');
      expect(waitingEvents.at(-1)).toEqual({ type: 'waiting-change', waiting: true });
      expect(events).toContainEqual({ type: 'opponent-name-change', name: null });
    });

    it('applySessionView clears waiting and sets opponent name once joined', () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Alice');
      session.applySessionView([
        { id: 0, name: 'Alice', joined: true },
        { id: 1, name: 'Bob', joined: true },
      ]);
      const waitingEvents = events.filter(e => e.type === 'waiting-change');
      expect(waitingEvents.at(-1)).toEqual({ type: 'waiting-change', waiting: false });
      expect(events).toContainEqual({ type: 'opponent-name-change', name: 'Bob' });
    });

    it('applySessionView is a no-op in solo mode', () => {
      const { session, events } = makeSession();
      session.create('solo', 'Alice');
      events.length = 0;
      session.applySessionView([
        { id: 0, name: 'Alice', joined: true },
        { id: 1, name: 'Player 2', joined: true },
      ]);
      expect(events).toHaveLength(0);
    });

    it('a JOIN event carrying seats applies them (clears waiting, sets opponent name)', async () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Alice');
      await session.onResponse(buildResponse({
        eventType: 'JOIN',
        eventData: { player: { id: '1' }, players: [
          { id: 0, name: 'Alice', joined: true },
          { id: 1, name: 'Bob', joined: true },
        ] },
        state: buildGame(),
      }));
      const waitingChanges = events.filter(e => e.type === 'waiting-change');
      expect(waitingChanges.at(-1)).toEqual({ type: 'waiting-change', waiting: false });
      expect(events).toContainEqual({ type: 'opponent-name-change', name: 'Bob' });
    });

    it('restoreSession does not force waiting to false for a multiplayer host', () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Alice'); // host is waiting
      events.length = 0;
      session.restoreSession({ 0: 'tok-0' });
      const forcedFalse = events.filter(
        e => e.type === 'waiting-change' && e.waiting === false);
      expect(forcedFalse).toHaveLength(0);
    });
  });

  // --- SEND server response (opponent / echo dedup) -------------------------

  describe('SEND response', () => {
    it('emits a send event for the opponent in multiplayer, with the pre-send topCard', async () => {
      const { session, events } = makeSession();
      const topCard = buildCard({ rank: 'K', suit: 'spade' });
      session.hydrate('game-1', buildGame({ pile: buildPile({ cards: [topCard] }) }));
      session.restoreSession({ 1: 'tok-b' }); // multiplayer, my seat = 1, opponent = 0

      await session.onResponse(buildResponse({
        eventType: 'SEND',
        eventData: { player: { id: '0' } },
        state: buildGame(),
      }));

      const sendEvent = events.find(e => e.type === 'send') as
        Extract<GameEvent, { type: 'send' }> | undefined;
      expect(sendEvent).toBeDefined();
      expect(sendEvent!.playerIndex).toBe(0);
      expect(sendEvent!.topCard).toEqual(topCard);
    });

    it('does NOT emit a second send event for the local player in multiplayer (optimistic already fired)', async () => {
      const { session, events } = makeSession();
      session.hydrate('game-1', buildGame({ pile: buildPile({ cards: [buildCard()] }) }));
      session.restoreSession({ 1: 'tok-b' }); // multiplayer, my seat = 1

      session.send(1); // optimistic emit for my own send

      await session.onResponse(buildResponse({
        eventType: 'SEND',
        eventData: { player: { id: '1' } }, // server echo of my own send
        state: buildGame(),
      }));

      const mySendEvents = events.filter(
        e => e.type === 'send' && e.playerIndex === 1,
      );
      expect(mySendEvents).toHaveLength(1); // only the optimistic one
    });

    it('does NOT emit a send event from the SEND response in solo (AI already emits via send(1))', async () => {
      const { session, events } = makeSession();
      session.hydrate('game-1', buildGame({ pile: buildPile({ cards: [buildCard()] }) }));
      session.restoreSession({ 0: 'tok-a', 1: 'tok-b' }); // solo, my seat = 0

      await session.onResponse(buildResponse({
        eventType: 'SEND',
        eventData: { player: { id: '1' } },
        state: buildGame(), // empty pile + no available actions => AI stays idle
      }));

      expect(events.find(e => e.type === 'send')).toBeUndefined();
    });

    it('skips opponent send animations while catching up (queue backed up)', async () => {
      const events: GameEvent[] = [];
      let releaseAnimation!: () => void;
      const animationGate = new Promise<void>(res => { releaseAnimation = res; });

      const webSocket: WebSocketPort = {
        publish: () => {},
        subscribeToGame: () => {},
        setPresence: () => {},
        clearPresence: () => {},
      };
      const callbacks: GameSessionCallbacks = {
        onEvent: (e) => events.push(e),
        awaitAnimation: () => animationGate,
      };
      const session = new GameSession(webSocket, callbacks, () => new AI(1, 0));
      session.hydrate('game-1', buildGame());
      session.restoreSession({ 1: 'tok-b' }); // multiplayer, my seat = 1, opponent = 0

      // A GRAB by the opponent blocks on awaitAnimation, letting the queue build.
      const drain = session.onResponse(buildGrabResponse('0', buildGame()));
      // Four opponent SEND responses pile up behind the blocked grab animation.
      for (let i = 0; i < 4; i++) {
        session.onResponse(buildResponse({
          eventType: 'SEND',
          eventData: { player: { id: '0' } },
          state: buildGame(),
        }));
      }
      releaseAnimation();
      await drain;

      const sendEvents = events.filter(e => e.type === 'send');
      // With threshold 3: the first SEND (3 still queued) is skipped, the rest emit.
      expect(sendEvents).toHaveLength(3);
    });
  });

  // --- Presence + connection events -----------------------------------------

  describe('presence + connection events', () => {
    it('sends presence after a multiplayer create', async () => {
      const { session, presence } = makeSession();
      session.create('multiplayer', 'Bob');
      await session.onResponse(buildCreateResponse('game-7', { 0: 'tok-mp' }));
      expect(presence.some(p => p.includes('game-7') && p.includes('tok-mp'))).toBe(true);
    });

    it('does NOT send presence in solo mode', async () => {
      const { session, presence } = makeSession();
      session.create('solo', 'Solo');
      await session.onResponse(buildCreateResponse('game-8', { 0: 'a', 1: 'b' }));
      expect(presence.length).toBe(0);
    });

    it('emits opponent-connection on OPPONENT_DISCONNECTED', async () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Bob');
      await session.onResponse(buildCreateResponse('game-9', { 0: 'tok' }));
      await session.onResponse(buildResponse({
        eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: 1234 },
      }));
      expect(events).toContainEqual({
        type: 'opponent-connection', status: 'disconnected', seat: 1, deadlineEpochMs: 1234,
      });
    });

    it('emits opponent-connection on OPPONENT_RECONNECTED', async () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Bob');
      await session.onResponse(buildCreateResponse('game-10', { 0: 'tok' }));
      await session.onResponse(buildResponse({
        eventType: 'OPPONENT_RECONNECTED',
        eventData: { reconnectedSeat: 1 },
      }));
      expect(events).toContainEqual({
        type: 'opponent-connection', status: 'connected', seat: 1,
      });
    });
  });

  // --- REMATCH --------------------------------------------------------------

  describe('REMATCH', () => {
    it('solo rematch() publishes /app/rematch for BOTH seat tokens', async () => {
      const { session, published } = makeSession();
      session.create('solo');
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a', 1: 'tok-b' }));

      session.rematch();

      const rematchPublishes = published.filter(p => p.dest === '/app/rematch');
      expect(rematchPublishes).toHaveLength(2);
      const tokens = rematchPublishes.map(p => JSON.parse(p.body!).token).sort();
      expect(tokens).toEqual(['tok-a', 'tok-b']);
    });

    it('multiplayer rematch() publishes only for the local seat and emits pending', async () => {
      const { session, published, events } = makeSession();
      session.create('multiplayer', 'Alice');
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a' }));

      session.rematch();

      const rematchPublishes = published.filter(p => p.dest === '/app/rematch');
      expect(rematchPublishes).toHaveLength(1);
      expect(JSON.parse(rematchPublishes[0].body!).token).toBe('tok-a');
      expect(events).toContainEqual({ type: 'rematch', status: 'pending', requestedBy: 0 });
    });

    it('REMATCH pending from opponent emits a pending event with their seat', async () => {
      const { session, events } = makeSession();
      session.create('multiplayer', 'Alice');
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a' }));

      await session.onResponse(buildRematchResponse('PENDING', '1'));

      expect(events).toContainEqual({ type: 'rematch', status: 'pending', requestedBy: 1 });
    });

    it('REMATCH started emits a started event', async () => {
      const { session, events } = makeSession();
      session.create('solo');
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a', 1: 'tok-b' }));

      await session.onResponse(buildRematchResponse('STARTED', '0'));

      expect(events).toContainEqual({ type: 'rematch', status: 'started' });
    });
  });

  // --- Event queue ----------------------------------------------------------

  describe('event queue seq counters', () => {
    it('increments seq on each send event', () => {
      const { session, events } = makeSession();
      session.hydrate('game-1', buildGame());
      session.restoreTokens({ 0: 'tok-a', 1: 'tok-b' });

      session.send(0);
      session.send(0);

      const seqs = events
        .filter((e): e is Extract<GameEvent, { type: 'send' }> => e.type === 'send')
        .map(e => e.seq);
      expect(seqs[1]).toBe(seqs[0] + 1);
    });
  });
});
