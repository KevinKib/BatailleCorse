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

  const webSocket: WebSocketPort = {
    publish: (dest, body) => published.push({ dest, body }),
    subscribeToGame: (id) => subscribed.push(id),
  };

  const callbacks: GameSessionCallbacks = {
    onEvent: (e) => events.push(e),
    awaitAnimation: () => Promise.resolve(),
  };

  const session = new GameSession(webSocket, callbacks, () => new AI(1, 0));

  return { session, events, published, subscribed };
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
      session.create('Alice');
      await session.onResponse(buildCreateResponse('game-1', { 0: 'tok-a', 1: 'tok-b' }));
      expect(events).toContainEqual({ type: 'game-id-change', gameId: 'game-1' });
      expect(subscribed).toContain('game-1');
    });

    it('stores tokens in localStorage', async () => {
      const { session } = makeSession();
      session.create();
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
