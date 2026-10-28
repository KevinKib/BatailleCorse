import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useBlackjackStore, MAX_EVENTS } from './Blackjack.store';
import webSocketService from '../service/WebSocketService';
import {
  blackjackBettingState, blackjackPlayingState, blackjackTournamentEndState, blackjackSoloEndState,
} from '../model/blackjack/fixtures';
import type { BlackjackEventData, BlackjackState } from '../model/blackjack/BlackjackState';
import type { LobbyView } from '../model/lobby/LobbyView';

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Ann', joined: true }, { seat: 1, name: 'Bob', joined: true }],
    hostSeat: 0, mySeat: 0, minPlayers: 1, maxPlayers: 6, canStart: true, lobbyVersion: 1, ...overrides,
  };
}

const eventData = (overrides: Partial<BlackjackEventData>): BlackjackEventData => ({
  seat: null, handIndex: null, card: null, value: null, cards: null, holeCard: null, handNumber: null,
  button: null, bets: null, nets: null, stacks: null, bestSeats: null, ...overrides,
});

describe('Blackjack store', () => {
  let publish: ReturnType<typeof vi.spyOn>;

  function boot(state: BlackjackState = blackjackBettingState) {
    const store = useBlackjackStore();
    store.restore('g1', 0, 'tok');
    store.applyEvent({ type: 'state-update', state });
    publish.mockClear();
    return store;
  }
  const sent = () => publish.mock.calls.map(c => ({ dest: c[0], body: JSON.parse(c[1] as string) }));
  // The state the server sends once my bet is in: the version moved on, my plate shows the bet placed.
  const afterMyBet = (): BlackjackState => ({
    ...blackjackBettingState, version: blackjackBettingState.version + 1, availableActions: [],
    players: blackjackBettingState.players.map(p => (p.seat === 0 ? { ...p, betPlaced: true } : p)),
  });

  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
    vi.spyOn(webSocketService, 'subscribeToSeat').mockImplementation(() => {});
    vi.spyOn(webSocketService, 'setPresence').mockImplementation(() => {});
  });
  afterEach(() => { vi.restoreAllMocks(); });

  describe('views and screen', () => {
    it('is connecting without a view, then lobby, playing and finished', () => {
      const store = useBlackjackStore();
      expect(store.screen).toBe('connecting');
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.screen).toBe('lobby');
      expect(store.lobby).not.toBeNull();
      expect(store.game).toBeNull();
      store.applyEvent({ type: 'state-update', state: blackjackBettingState });
      expect(store.screen).toBe('playing');
      store.applyEvent({ type: 'state-update', state: blackjackTournamentEndState });
      expect(store.screen).toBe('finished');
    });

    it('reads my seat, the options and the actions from the state', () => {
      const store = boot(blackjackPlayingState);
      expect(store.mySeat).toBe(0);
      expect(store.me?.name).toBe('Alice');
      expect(store.options).toMatchObject({ hands: '10', maxBet: '50' });
      expect(store.can('HIT')).toBe(true);
      expect(store.can('SPLIT')).toBe(false);
    });

    it('lists the opponents in seat order after me', () => {
      const store = boot();
      expect(store.opponents.map(p => p.seat)).toEqual([1, 2, 3]);
    });

    it('is solo with a single seat', () => {
      const store = boot(blackjackSoloEndState);
      expect(store.solo).toBe(true);
      expect(store.opponents).toEqual([]);
    });

    it('keeps every seat seen, so a forfeited name can still be read', () => {
      const store = boot();
      store.applyEvent({
        type: 'state-update',
        state: { ...blackjackBettingState, version: 8, players: blackjackBettingState.players.filter(p => p.seat !== 2) },
      });
      expect(store.roster[2]).toBeDefined();
    });
  });

  describe('event log (what the table animates)', () => {
    it('logs nothing from the first state, then each event newest first', () => {
      const store = boot();
      expect(store.events).toEqual([]);
      store.applyEvent({ type: 'event', eventType: 'HIT', eventData: eventData({ seat: 1, value: 18 }), message: '' });
      store.applyEvent({ type: 'event', eventType: 'STAND', eventData: eventData({ seat: 1 }), message: '' });
      expect(store.events.map(e => e.eventType)).toEqual(['STAND', 'HIT']);
      expect(store.events[1].data?.value).toBe(18);
      expect(store.lastEvent?.eventType).toBe('STAND');
      expect(store.events[0].id).toBeGreaterThan(store.events[1].id);
    });

    it('does not log START, presence or forfeit events', () => {
      const store = boot();
      store.applyEvent({ type: 'event', eventType: 'START', eventData: null, message: '' });
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED', eventData: { seat: 1 }, message: '' });
      expect(store.events).toEqual([]);
    });

    it('keeps at most MAX_EVENTS entries', () => {
      const store = boot();
      for (let i = 0; i < MAX_EVENTS + 5; i++) {
        store.applyEvent({ type: 'event', eventType: 'STAND', eventData: eventData({ seat: 1 }), message: '' });
      }
      expect(store.events).toHaveLength(MAX_EVENTS);
    });

    it('forgets the log when the room goes back to a lobby', () => {
      const store = boot();
      store.applyEvent({ type: 'event', eventType: 'STAND', eventData: eventData({ seat: 1 }), message: '' });
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.events).toEqual([]);
      expect(store.lastEvent).toBeNull();
    });
  });

  describe('bet', () => {
    it('sends the amount and holds the table until the state that shows it', () => {
      const store = boot();
      expect(store.bet(20)).toBe(true);
      expect(sent()).toEqual([{ dest: '/app/blackjack/bet', body: { gameId: 'g1', token: 'tok', amount: 20 } }]);
      expect(store.busy).toBe(true);
      expect(store.bet(20)).toBe(false);
      store.applyEvent({ type: 'state-update', state: afterMyBet() });
      expect(store.busy).toBe(false);
    });

    it('a state that does not show my bet (another seat bet) keeps the table locked', () => {
      const store = boot();
      store.bet(20);
      store.applyEvent({
        type: 'state-update',
        state: { ...blackjackBettingState, version: 8, players: blackjackBettingState.players.map(p => (p.seat === 3 ? { ...p, betPlaced: true } : p)) },
      });
      expect(store.busy).toBe(true);
    });

    it('a state where the hand moved on answers it too (my bet was the last one)', () => {
      const store = boot();
      store.bet(20);
      store.applyEvent({ type: 'state-update', state: { ...blackjackPlayingState, version: 30 } });
      expect(store.busy).toBe(false);
    });

    it('refuses what the limits already refuse, without a round trip', () => {
      const store = boot();
      expect(store.bet(0)).toBe(false);
      expect(store.bet(51)).toBe(false);
      expect(store.bet(2.5)).toBe(false);
      expect(sent()).toEqual([]);
    });

    it('refuses a second bet once mine is in, or outside the betting phase', () => {
      const store = boot(afterMyBet());
      expect(store.bet(10)).toBe(false);
      const playing = boot(blackjackPlayingState);
      expect(playing.bet(10)).toBe(false);
      expect(sent()).toEqual([]);
    });
  });

  describe('play commands', () => {
    const afterMyAction = (): BlackjackState => ({ ...blackjackPlayingState, version: blackjackPlayingState.version + 1 });

    it.each([
      ['hit', '/app/blackjack/hit'],
      ['stand', '/app/blackjack/stand'],
      ['doubleDown', '/app/blackjack/double'],
    ] as const)('%s goes to %s when allowed and is answered by the next version', (method, dest) => {
      const store = boot(blackjackPlayingState);
      expect(store[method]()).toBe(true);
      expect(sent()).toEqual([{ dest, body: { gameId: 'g1', token: 'tok' } }]);
      expect(store.busy).toBe(true);
      store.applyEvent({ type: 'state-update', state: afterMyAction() });
      expect(store.busy).toBe(false);
    });

    it('refuses an action the server did not offer (split here)', () => {
      const store = boot(blackjackPlayingState);
      expect(store.split()).toBe(false);
      expect(sent()).toEqual([]);
    });

    it('split is sent when offered', () => {
      const store = boot({ ...blackjackPlayingState, availableActions: ['HIT', 'STAND', 'DOUBLE', 'SPLIT'] });
      expect(store.split()).toBe(true);
      expect(sent()[0].dest).toBe('/app/blackjack/split');
    });

    it('refuses to hit while betting', () => {
      const store = boot();
      expect(store.hit()).toBe(false);
    });
  });

  describe('refusals and the socket', () => {
    it('a refusal frees the table and shows a notice', () => {
      const store = boot(blackjackPlayingState);
      store.hit();
      store.applyEvent({ type: 'error', eventType: 'ACTION_NOT_ALLOWED', message: 'no' });
      expect(store.busy).toBe(false);
      expect(store.notice).toBe('moveImpossible');
    });

    it('a socket that is down is reported rather than left pending', () => {
      const store = boot(blackjackPlayingState);
      publish.mockImplementation(() => { throw new Error('down'); });
      expect(store.hit()).toBe(false);
      expect(store.busy).toBe(false);
      expect(store.notice).toBe('connection');
    });

    it('the next table event clears the notice', () => {
      const store = boot(blackjackPlayingState);
      store.applyEvent({ type: 'error', eventType: 'X', message: 'no' });
      store.applyEvent({ type: 'event', eventType: 'HIT', eventData: eventData({ seat: 1 }), message: '' });
      expect(store.notice).toBeNull();
    });
  });

  describe('lifecycle', () => {
    it('leaving forgets the game', () => {
      const store = boot();
      store.applyEvent({ type: 'removed' });
      expect(store.state).toBeNull();
      expect(store.gameId).toBeNull();
      expect(store.removedByHost).toBe(true);
    });

    it('forfeit publishes the shared /app/forfeit', () => {
      const store = boot();
      store.forfeit();
      expect(sent()).toEqual([{ dest: '/app/forfeit', body: { gameId: 'g1', token: 'tok' } }]);
    });
  });
});
