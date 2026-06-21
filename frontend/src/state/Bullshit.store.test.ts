import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useBullshitStore, REVEAL_HOLD_MS } from './Bullshit.store';
import type { BullshitState } from '../model/bullshit/BullshitState';
import type { LobbyView } from '../model/bullshit/LobbyView';
import webSocketService from '../service/WebSocketService';

function state(overrides: Partial<BullshitState> = {}): BullshitState {
  return {
    started: true,
    id: 'g1', gameType: 'bullshit',
    myHand: [{ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }, { rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' }],
    availableActions: ['DISCARD'],
    players: [
      { id: '0', handCount: 2, isCurrentPlayer: true },
      { id: '1', handCount: 5, isCurrentPlayer: false },
    ],
    currentTarget: { label: 'ACE' },
    discardPileSize: 0,
    table: { state: 'NO_CLAIM' },
    pendingWinner: { state: 'NONE' },
    outcome: { status: 'ONGOING' },
    ...overrides,
  };
}

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false,
    gameId: 'g1',
    players: [
      { seat: 0, name: 'Alice', joined: true },
      { seat: 1, name: null, joined: false },
    ],
    hostSeat: 0,
    mySeat: 0,
    minPlayers: 2,
    maxPlayers: 6,
    canStart: false,
    ...overrides,
  };
}

describe('Bullshit store', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  it('isMyTurn / canDiscard reflect my seat and available actions', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: state() });
    expect(store.isMyTurn).toBe(true);
    expect(store.canDiscard).toBe(true);
    expect(store.canCallBullshit).toBe(false);
  });

  it('a lobby view yields phase lobby and exposes isHost/canStart', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: lobbyView({ canStart: true }) });
    expect(store.phase).toBe('lobby');
    expect(store.lobby).not.toBeNull();
    expect(store.game).toBeNull();
    expect(store.isHost).toBe(true);
    expect(store.canStart).toBe(true);
  });

  it('a started view yields phase playing and exposes game', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: state() });
    expect(store.phase).toBe('playing');
    expect(store.game).not.toBeNull();
    expect(store.lobby).toBeNull();
  });

  it('phase is finished and iWon true when I am the winner', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: state({ outcome: { status: 'FINISHED', winnerId: '0' } }) });
    expect(store.phase).toBe('finished');
    expect(store.iWon).toBe(true);
  });

  it('records a CALL_BULLSHIT reveal', () => {
    const store = useBullshitStore();
    const reveal = { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0, revealedCards: [] };
    store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', eventData: reveal, message: 'm' });
    expect(store.reveal).toEqual(reveal);
  });

  describe('reveal timed hold', () => {
    beforeEach(() => { vi.useFakeTimers(); });
    afterEach(() => { vi.useRealTimers(); });

    it('clears the reveal after the hold window', () => {
      const store = useBullshitStore();
      const reveal = { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0, revealedCards: [] };
      store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', eventData: reveal, message: 'm' });
      expect(store.reveal).toEqual(reveal);

      vi.advanceTimersByTime(REVEAL_HOLD_MS);
      expect(store.reveal).toBeNull();
    });

    it('does not clear the reveal early when another event arrives during the hold', () => {
      const store = useBullshitStore();
      const reveal = { callerSeat: 1, claimantSeat: 0, truthful: true, pickerSeat: 1, revealedCards: [] };
      store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', eventData: reveal, message: 'm' });

      vi.advanceTimersByTime(REVEAL_HOLD_MS - 1);
      store.applyEvent({ type: 'event', eventType: 'DISCARD', eventData: {}, message: 'm' });
      expect(store.reveal).toEqual(reveal);   // still showing — the timer owns dismissal

      vi.advanceTimersByTime(1);
      expect(store.reveal).toBeNull();
    });
  });

  it('forfeit() delegates to the session (publishes /app/forfeit)', () => {
    const store = useBullshitStore();
    store.restore('g1', 0, 'tok');
    const publishSpy = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
    store.forfeit();
    expect(publishSpy).toHaveBeenCalledWith('/app/forfeit', expect.stringContaining('"gameId":"g1"'));
    publishSpy.mockRestore();
  });

  it('toggleCard selects and deselects from the hand', () => {
    const store = useBullshitStore();
    const card = { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' };
    store.toggleCard(card);
    expect(store.selectedCards).toHaveLength(1);
    store.toggleCard(card);
    expect(store.selectedCards).toHaveLength(0);
  });

  it('playAgain() posts to the play-again endpoint', async () => {
    const store = useBullshitStore();
    store.restore('g1', 0, 'tok');
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      Promise.resolve(new Response(JSON.stringify({ playerId: 0, token: 't' }), { status: 200 })));

    await store.playAgain();

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining('/api/bullshit/game/g1/play-again'),
      expect.objectContaining({ method: 'POST' }));
    fetchSpy.mockRestore();
  });

  describe('opponent presence', () => {
    it('records a disconnect, exposes it via liveDisconnections, and clears on reconnect', () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: state() });   // players 0 and 1

      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: 1_060_000 }, message: 'm' });
      expect(store.liveDisconnections).toEqual({ 1: 1_060_000 });

      store.applyEvent({ type: 'event', eventType: 'OPPONENT_RECONNECTED',
        eventData: { reconnectedSeat: 1 }, message: 'm' });
      expect(store.liveDisconnections).toEqual({});
    });

    it('drops a disconnect for a seat no longer present in the game', () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: state() });   // players 0 and 1
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: 1_060_000 }, message: 'm' });

      // seat 1 leaves the table (forfeited/removed by a later state-update)
      store.applyEvent({ type: 'state-update', state: state({
        players: [{ id: '0', handCount: 2, isCurrentPlayer: true }],
      }) });
      expect(store.liveDisconnections).toEqual({});
    });

    it('shows a forfeit notice and clears the disconnect for that seat', () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: state() });
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: 1_060_000 }, message: 'm' });

      store.applyEvent({ type: 'event', eventType: 'FORFEIT',
        eventData: { loserSeat: 1 }, message: 'm' });
      expect(store.forfeitNotice).toEqual({ seat: 1 });
      expect(store.liveDisconnections).toEqual({});
    });

    it('CALL_BULLSHIT still records a reveal (unchanged)', () => {
      const store = useBullshitStore();
      const reveal = { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0, revealedCards: [] };
      store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', eventData: reveal, message: 'm' });
      expect(store.reveal).toEqual(reveal);
    });

    it('playAgain resets stale presence', async () => {
      const store = useBullshitStore();
      store.restore('g1', 0, 'tok');
      store.applyEvent({ type: 'state-update', state: state() });
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: 1_060_000 }, message: 'm' });
      expect(store.liveDisconnections).toEqual({ 1: 1_060_000 });

      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ playerId: 0, token: 't' }), { status: 200 })));
      await store.playAgain();
      expect(store.disconnections).toEqual({});
      expect(store.forfeitNotice).toBeNull();
      fetchSpy.mockRestore();
    });
  });
});
