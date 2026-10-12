import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useBullshitStore, REVEAL_HOLD_MS } from './Bullshit.store';
import type { BullshitState } from '../model/bullshit/BullshitState';
import type { LobbyView } from '../model/lobby/LobbyView';
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

  it('a lobby view yields phase lobby', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: lobbyView({ canStart: true }) });
    expect(store.phase).toBe('lobby');
    expect(store.lobby).not.toBeNull();
    expect(store.game).toBeNull();
  });

  it('keeps the lobby refusal until the next lobby refresh, and clears it on a new request', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'error', eventType: 'JOIN', message: 'Room is full' });
    expect(store.lobbyError).toBe('Room is full');
    store.applyEvent({ type: 'state-update', state: lobbyView() });
    expect(store.lobbyError).toBeNull();
    store.applyEvent({ type: 'error', eventType: 'JOIN', message: 'x' });
    vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
    store.addBot();
    expect(store.lobbyError).toBeNull();
  });

  it('reports a bot request as failed when the socket is down, without throwing', () => {
    const store = useBullshitStore();
    store.restore('g1', 0, 'tok');
    vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new TypeError('There is no underlying STOMP connection'); });
    expect(() => store.addBot()).not.toThrow();
    expect(store.lobbyError).not.toBeNull();
    store.applyEvent({ type: 'state-update', state: lobbyView() });
    expect(() => store.removeBot(1)).not.toThrow();
    expect(store.lobbyError).not.toBeNull();
  });

  it('shows a refusal in the lobby whatever its event type (a Start refusal is typed START, a Leave JOIN)', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'state-update', state: lobbyView() });
    store.applyEvent({ type: 'error', eventType: 'START', message: 'Not enough players' });
    expect(store.lobbyError).toBe('Not enough players');
  });

  it('keeps the lobby message out of the game: a refused discard is not a lobby error', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'state-update', state: state() });
    store.applyEvent({ type: 'error', eventType: 'DISCARD', message: 'nope' });
    expect(store.lobbyError).toBeNull();
  });

  it('follows the seat the server reports', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 2 });
    expect(store.mySeat).toBe(2);
    store.applyEvent({ type: 'seat-change', seat: 0 });
    expect(store.mySeat).toBe(0);
  });

  describe('starting the game', () => {
    it('is busy from the Start until a state or a refusal comes back, and sends the Start once', () => {
      const store = useBullshitStore();
      store.restore('g1', 0, 'tok');
      store.applyEvent({ type: 'state-update', state: lobbyView({ canStart: true }) });
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      publish.mockClear();
      store.startGame();
      store.startGame();
      expect(publish.mock.calls.filter(c => c[0] === '/app/bullshit/start')).toHaveLength(1);
      expect(store.starting).toBe(true);
      store.applyEvent({ type: 'error', eventType: 'START', message: 'Not enough players' });
      expect(store.starting).toBe(false);
      store.startGame();
      expect(store.starting).toBe(true);
      store.applyEvent({ type: 'state-update', state: state() });
      expect(store.starting).toBe(false);
    });

    it('is not busy when the Start could not be sent', () => {
      const store = useBullshitStore();
      store.restore('g1', 0, 'tok');
      vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new TypeError('no underlying STOMP connection'); });
      store.startGame();
      expect(store.starting).toBe(false);
    });
  });

  describe('lobby commands', () => {
    it('reports a Start as failed when the socket is down, without throwing', () => {
      const store = useBullshitStore();
      store.restore('g1', 0, 'tok');
      vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new TypeError('There is no underlying STOMP connection'); });
      let sent: unknown;
      expect(() => { sent = store.startGame(); }).not.toThrow();
      expect(sent).toBe(false);
      expect(store.lobbyError).not.toBeNull();
    });

    it('sends option, seat option and rename changes through the shared lobby destinations', () => {
      const store = useBullshitStore();
      store.restore('g1', 0, 'tok');
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      publish.mockClear();
      store.setOption('deckSize', '52');
      store.setSeatOption(1, 'tier', '2');
      store.rename('Zed');
      expect(publish.mock.calls.map(c => c[0])).toEqual(['/app/lobby/setOptions', '/app/lobby/setSeatOption', '/app/lobby/rename']);
    });

    it('reports a change as failed when the socket is down, without throwing', () => {
      const store = useBullshitStore();
      store.restore('g1', 0, 'tok');
      vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new TypeError('There is no underlying STOMP connection'); });
      expect(() => store.setOption('deckSize', '52')).not.toThrow();
      expect(store.lobbyError).not.toBeNull();
    });
  });

  describe('leaving the lobby', () => {
    it('tears the seat down and forgets the lobby, so the next room starts clean', () => {
      const store = useBullshitStore();
      store.restore('g1', 1, 'tok');
      store.applyEvent({ type: 'state-update', state: lobbyView({ mySeat: 1 }) });
      vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const unsubscribeSeat = vi.spyOn(webSocketService, 'unsubscribeFromSeat').mockImplementation(() => {});
      const unsubscribeGame = vi.spyOn(webSocketService, 'unsubscribeFromGame').mockImplementation(() => {});
      const clearPresence = vi.spyOn(webSocketService, 'clearPresence').mockImplementation(() => {});
      localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 1: 'tok' }));

      store.leave();

      expect(unsubscribeSeat).toHaveBeenCalled();
      expect(unsubscribeGame).toHaveBeenCalled();
      expect(clearPresence).toHaveBeenCalled();
      expect(localStorage.getItem('bullshit:tokens:g1')).toBeNull();
      expect(store.state).toBeNull();
      expect(store.gameId).toBeNull();
      expect(store.phase).toBe('connecting');
    });

    it('stays in the lobby with an error when the socket is down', () => {
      const store = useBullshitStore();
      store.restore('g1', 1, 'tok');
      store.applyEvent({ type: 'state-update', state: lobbyView({ mySeat: 1 }) });
      vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new TypeError('no underlying STOMP connection'); });
      store.leave();
      expect(store.phase).toBe('lobby');
      expect(store.lobbyError).not.toBeNull();
    });
  });

  it('creates a lobby over REST and exposes its id', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => ({ gameId: 'g9', token: 't9' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => lobbyView({ gameId: 'g9', lobbyVersion: 1 }) });
    vi.stubGlobal('fetch', fetchMock);
    const store = useBullshitStore();
    expect(await store.create('Alice')).toBe('g9');
    expect(store.gameId).toBe('g9');
    expect(store.phase).toBe('lobby');
    vi.unstubAllGlobals();
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

  describe('rematch state', () => {
    afterEach(() => vi.restoreAllMocks());

    it('is pending while the play-again request is in flight, then idle once the lobby arrives', async () => {
      const store = useBullshitStore();
      store.restore('g1', 0, 'tok');
      let release!: (r: Response) => void;
      const fetchSpy = vi.spyOn(globalThis, 'fetch')
        .mockImplementationOnce(() => new Promise<Response>(res => { release = res; }))
        .mockImplementation(() => Promise.resolve(new Response(JSON.stringify(lobbyView()), { status: 200 })));
      const pending = store.playAgain();
      expect(store.rematch).toBe('pending');

      release(new Response(JSON.stringify({ playerId: 0, token: 't' }), { status: 200 }));
      await pending;
      expect(fetchSpy).toHaveBeenCalledTimes(2);
      expect(store.rematch).toBe('idle');
    });

    it('is failed, without throwing, when the server refuses the rematch', async () => {
      const store = useBullshitStore();
      store.restore('g1', 0, 'tok');
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 409 }));
      await expect(store.playAgain()).resolves.toBeUndefined();
      expect(store.rematch).toBe('failed');
    });

    it('can be retried after a failure', async () => {
      const store = useBullshitStore();
      store.restore('g1', 0, 'tok');
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response('', { status: 409 }));
      await store.playAgain();
      fetchSpy.mockImplementation(() => new Promise(() => {}));
      void store.playAgain();
      expect(store.rematch).toBe('pending');
    });
  });

  describe('how the game ended', () => {
    it('remembers the reason of the last forfeit after the notice has gone', () => {
      vi.useFakeTimers();
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: state() });
      store.applyEvent({ type: 'event', eventType: 'FORFEIT',
        eventData: { loserSeat: 1, reason: 'DISCONNECTED' }, message: '' });
      vi.advanceTimersByTime(60_000);
      expect(store.forfeitNotice).toBeNull();
      expect(store.lastForfeit).toEqual({ seat: 1, reason: 'DISCONNECTED' });
      vi.useRealTimers();
    });

    it('forgets it when the room goes back to a lobby', () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: state() });
      store.applyEvent({ type: 'event', eventType: 'FORFEIT',
        eventData: { loserSeat: 1, reason: 'RESIGNED' }, message: '' });
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.lastForfeit).toBeNull();
    });
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
  describe('roster', () => {
    const named = (players: { id: string; name?: string }[]) => ({
      ...state(),
      players: players.map(p => ({ handCount: 3, isCurrentPlayer: false, ...p })),
    });

    it('keeps a seat that left the table (forfeit) so it can still be named', () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'state-update', state: named([{ id: '0', name: 'Alice' }, { id: '1', name: 'Bob' }, { id: '2', name: 'Cy' }]) });
      store.applyEvent({ type: 'state-update', state: named([{ id: '0', name: 'Alice' }, { id: '2', name: 'Cy' }]) });
      expect(store.roster['1']?.name).toBe('Bob');
      expect(store.game?.players).toHaveLength(2);
    });

    it('forgets everyone when the room goes back to a lobby', () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'state-update', state: named([{ id: '0', name: 'Alice' }]) });
      store.applyEvent({ type: 'state-update', state: { started: false, gameId: 'g1', players: [], hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 6, canStart: false } });
      expect(store.roster).toEqual({});
    });
  });
  describe('card selection', () => {
    const ace = { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' } as const;
    const king = { rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' } as const;
    const started = () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: state() });
      return store;
    };

    it('is emptied when the room goes back to a lobby, so a redealt card is not pre-selected', () => {
      const store = started();
      store.toggleCard(ace);
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      store.applyEvent({ type: 'state-update', state: state() });
      expect(store.selectedCards).toEqual([]);
    });

    it('is emptied when a game starts after a finished one', () => {
      const store = started();
      store.toggleCard(ace);
      store.applyEvent({ type: 'state-update', state: state({ outcome: { status: 'FINISHED', winnerId: '1' } }) });
      store.applyEvent({ type: 'state-update', state: state({ id: 'g2' }) });
      expect(store.selectedCards).toEqual([]);
    });

    it('is emptied when another game is rehydrated', () => {
      const store = started();
      store.toggleCard(ace);
      store.applyEvent({ type: 'state-update', state: state({ id: 'other' }) });
      expect(store.selectedCards).toEqual([]);
    });

    it('is emptied by leave() and playAgain()', async () => {
      const store = started();
      store.toggleCard(ace);
      store.leave();
      expect(store.selectedCards).toEqual([]);
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
        Promise.resolve(new Response(JSON.stringify({ playerId: 0, token: 't' }), { status: 200 })));
      store.applyEvent({ type: 'state-update', state: state() });
      store.toggleCard(king);
      await store.playAgain();
      expect(store.selectedCards).toEqual([]);
      fetchSpy.mockRestore();
    });

    it('drops a card that left my hand but keeps the others', () => {
      const store = started();
      store.toggleCard(ace);
      store.toggleCard(king);
      store.applyEvent({ type: 'state-update', state: state({ myHand: [king] }) });
      expect(store.selectedCards.map(c => c.name)).toEqual(['SPADE_KING']);
    });

    it('keeps the selection across an unrelated update of the same game', () => {
      const store = started();
      store.toggleCard(ace);
      store.applyEvent({ type: 'state-update', state: state({ discardPileSize: 1 }) });
      expect(store.selectedCards.map(c => c.name)).toEqual(['HEART_ACE']);
    });
  });
});
