import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useNinetyNineStore } from './NinetyNine.store';
import webSocketService from '../service/WebSocketService';
import {
  buildNinetyNineBust, buildNinetyNineFourSeatRound, buildNinetyNineQuickEnd,
} from '../model/fixtures/ninetynine';
import type { NinetyNineEventData, NinetyNineState } from '../model/ninetynine/NinetyNineState';
import type { LobbyView } from '../model/lobby/LobbyView';

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Ann', joined: true }, { seat: 1, name: 'Bob', joined: true }],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 12, canStart: true, lobbyVersion: 1, ...overrides,
  };
}

const eventData = (overrides: Partial<NinetyNineEventData>): NinetyNineEventData => ({
  seat: null, card: null, aceValue: null, totalAfter: null, reversed: null, hand: null,
  livesLeft: null, place: null, stockSize: null, ...overrides,
});

describe('NinetyNine store', () => {
  let publish: ReturnType<typeof vi.spyOn>;

  function boot(state: NinetyNineState = buildNinetyNineFourSeatRound()) {
    const store = useNinetyNineStore();
    store.restore('g1', 0, 'tok');
    store.applyEvent({ type: 'state-update', state });
    publish.mockClear();
    return store;
  }
  // The state the server sends once my card is played: the version moved on, the turn went to the next seat.
  const afterMyPlay = (card: NinetyNineState['hand'][number]): NinetyNineState => {
    const round = buildNinetyNineFourSeatRound();
    return {
      ...round, version: round.version + 1, currentSeat: 1, availableActions: [],
      lastPlay: { seat: 0, card, aceValue: null, totalBefore: round.total, totalAfter: round.total + 7, reversed: false },
    };
  };
  const sent = () =>publish.mock.calls.map(c => ({ dest: c[0], body: JSON.parse(c[1] as string) }));

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
      const store = useNinetyNineStore();
      expect(store.screen).toBe('connecting');
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.screen).toBe('lobby');
      expect(store.lobby).not.toBeNull();
      expect(store.game).toBeNull();
      store.applyEvent({ type: 'state-update', state: buildNinetyNineFourSeatRound() });
      expect(store.screen).toBe('playing');
      store.applyEvent({ type: 'state-update', state: buildNinetyNineQuickEnd() });
      expect(store.screen).toBe('finished');
    });

    it('reads the seat, the hand, the options and the turn from the state', () => {
      const store = boot();
      expect(store.mySeat).toBe(0);
      expect(store.me?.seat).toBe(0);
      expect(store.isMyTurn).toBe(true);
      expect(store.hand).toHaveLength(3);
      expect(store.options).toEqual({ lives: '3', quick: 'off' });
      expect(store.can('PLAY')).toBe(true);
    });

    it('is not my turn when another seat is to play, and I cannot play', () => {
      const store = boot({ ...buildNinetyNineFourSeatRound(), currentSeat: 2, availableActions: [] });
      expect(store.isMyTurn).toBe(false);
      expect(store.can('PLAY')).toBe(false);
    });

    it('keeps every seat seen, so a forfeited name can still be read', () => {
      const store = boot();
      const round = buildNinetyNineFourSeatRound();
      store.applyEvent({
        type: 'state-update', state: { ...round, version: 7, players: round.players.filter(p => p.seat !== 3) },
      });
      expect(store.roster[3]).toBeDefined();
    });
  });

  describe('event log (what the table animates)', () => {
    it('logs nothing from the first state, then each move newest first with the state before it', () => {
      const store = boot();
      expect(store.events).toEqual([]);
      const state = buildNinetyNineFourSeatRound();
      // The state of a move arrives first, then its event: the entry reads the total from the state before.
      store.applyEvent({ type: 'state-update', state: { ...state, version: 7, total: 49 } });
      store.applyEvent({
        type: 'event', eventType: 'PLAY',
        eventData: eventData({ seat: 3, card: state.hand[0], totalAfter: 49 }),
      });
      store.applyEvent({ type: 'event', eventType: 'RESHUFFLE', eventData: eventData({ stockSize: 30 }) });
      expect(store.events.map(e => e.eventType)).toEqual(['RESHUFFLE', 'PLAY']);
      expect(store.events[1]).toMatchObject({ seat: 3, totalBefore: 42, totalAfter: 49 });
      expect(store.lastEvent?.eventType).toBe('RESHUFFLE');
    });

    it('records the slot my played card held in the hand I saw, and none for another seat', () => {
      const store = boot();
      const state = buildNinetyNineFourSeatRound();
      store.applyEvent({ type: 'state-update', state: { ...state, version: state.version + 1 } });
      store.applyEvent({ type: 'event', eventType: 'PLAY', eventData: eventData({ seat: 0, card: state.hand[2] }) });
      store.applyEvent({ type: 'event', eventType: 'PLAY', eventData: eventData({ seat: 3, card: state.hand[2] }) });
      expect(store.events[1].handSlot).toBe(2);
      expect(store.events[0].handSlot).toBeNull();
    });

    it('gives every event of one command the state from before the command, not the one after it', () => {
      const store = boot();
      const state = buildNinetyNineFourSeatRound();
      // One command, three events: each is sent with the state after the whole command (same version).
      const after = { ...state, version: 7, total: 99 };
      store.applyEvent({ type: 'state-update', state: after });
      store.applyEvent({ type: 'event', eventType: 'PLAY', eventData: eventData({ seat: 3, card: state.hand[0], totalAfter: 99 }) });
      store.applyEvent({ type: 'state-update', state: after });
      store.applyEvent({ type: 'event', eventType: 'BUST', eventData: eventData({ seat: 0, livesLeft: 2 }) });
      store.applyEvent({ type: 'state-update', state: after });
      store.applyEvent({ type: 'event', eventType: 'RESHUFFLE', eventData: eventData({ stockSize: 30 }) });
      expect(store.events.map(e => [e.eventType, e.totalBefore])).toEqual([
        ['RESHUFFLE', 42], ['BUST', 42], ['PLAY', 42],
      ]);
      // The next command starts from the state it follows.
      store.applyEvent({ type: 'state-update', state: { ...after, version: 8, total: 0 } });
      store.applyEvent({ type: 'event', eventType: 'ROUND_START', eventData: eventData({}) });
      expect(store.events[0]).toMatchObject({ eventType: 'ROUND_START', totalBefore: 99 });
    });

    it('does not log presence events and never the start of the game', () => {
      const store = boot();
      store.applyEvent({ type: 'event', eventType: 'START', eventData: null });
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED', eventData: { seat: 2 } });
      expect(store.events).toEqual([]);
    });

    it('reports a forfeit to the room, not to the table', () => {
      const store = boot();
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', eventData: { loserSeat: 2, reason: 'RESIGNED' } });
      expect(store.lastForfeit).toEqual({ seat: 2, reason: 'RESIGNED' });
      expect(store.events).toEqual([]);
    });

    it('is cleared when the room goes back to a lobby', () => {
      const store = boot();
      store.applyEvent({ type: 'event', eventType: 'RESHUFFLE', eventData: eventData({ stockSize: 30 }) });
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.events).toEqual([]);
      expect(store.lastEvent).toBeNull();
    });

    it('shows the bust of the last round from the state', () => {
      const store = boot(buildNinetyNineBust());
      expect(store.game?.lastBust?.seat).toBe(2);
    });
  });

  describe('play', () => {
    it('sends a plain card with a null Ace value, and is busy until the next state', () => {
      const store = boot();
      const seven = store.hand[0];
      expect(store.play(seven)).toBe(true);
      expect(sent()).toEqual([{ dest: '/app/ninety-nine/play', body: { gameId: 'g1', token: 'tok', card: seven, aceValue: null } }]);
      expect(store.busy).toBe(true);
      expect(store.play(seven)).toBe(false);
      expect(sent()).toHaveLength(1);
      store.applyEvent({ type: 'state-update', state: afterMyPlay(seven) });
      expect(store.busy).toBe(false);
    });

    it('stays busy through a state that is not the answer to my command, until my play shows or the turn moves', () => {
      const store = boot();
      const seven = store.hand[0];
      store.play(seven);
      // Another seat forfeits meanwhile: the version moves, it is still my turn and my card is not played.
      store.applyEvent({ type: 'state-update', state: { ...buildNinetyNineFourSeatRound(), version: 7 } });
      expect(store.busy).toBe(true);
      expect(store.play(seven)).toBe(false);
      expect(sent()).toHaveLength(1);
      // The turn moved on without my play showing (a reload after a reconnection): nothing is pending any more.
      store.applyEvent({ type: 'state-update', state: { ...buildNinetyNineFourSeatRound(), version: 8, currentSeat: 2 } });
      expect(store.busy).toBe(false);
    });

    it('a state of the version I sent from is not the answer either', () => {
      const store = boot();
      const seven = store.hand[0];
      store.play(seven);
      store.applyEvent({ type: 'state-update', state: { ...afterMyPlay(seven), version: 6 } });
      expect(store.busy).toBe(true);
    });

    it('sends the Ace with the chosen value, and refuses an Ace without one when two are allowed', () => {
      const store = boot();
      const ace = store.hand[1];
      expect(store.play(ace)).toBe(false);
      expect(sent()).toEqual([]);
      expect(store.play(ace, 11)).toBe(true);
      expect(sent()[0].body).toMatchObject({ card: ace, aceValue: 11 });
    });

    it('fills in the only Ace value allowed, and refuses a value the server does not allow', () => {
      const round = buildNinetyNineFourSeatRound();
      const ace = round.hand[1];
      const store = boot({ ...round, playable: [round.playable[0], { card: ace, safe: true, aceValues: [1] }, round.playable[2]] });
      expect(store.play(ace, 11)).toBe(false);
      expect(store.play(ace)).toBe(true);
      expect(sent()[0].body).toMatchObject({ card: ace, aceValue: 1 });
    });

    it('refuses an unsafe card, a card not in my hand, and a play when the server lists none', () => {
      const round = buildNinetyNineFourSeatRound();
      const unsafe = { ...round, playable: [{ card: round.hand[0], safe: false, aceValues: [] }, ...round.playable.slice(1)] };
      expect(boot(unsafe).play(round.hand[0])).toBe(false);

      setActivePinia(createPinia());
      const store = boot({ ...round, availableActions: [] });
      expect(store.play(round.hand[0])).toBe(false);
      expect(store.play({ rank: '3', suit: 'CLUB', name: 'CLUB_3' })).toBe(false);
      expect(sent()).toEqual([]);
    });

    it('reports a socket that is down, and stays free to retry', () => {
      const store = boot();
      publish.mockImplementation(() => { throw new Error('down'); });
      expect(store.play(store.hand[0])).toBe(false);
      expect(store.notice).toBe('connection');
      expect(store.busy).toBe(false);
    });

    it('a refusal frees the table with a notice', () => {
      const store = boot();
      store.play(store.hand[0]);
      store.applyEvent({ type: 'error', message: 'refused' });
      expect(store.busy).toBe(false);
      expect(store.notice).toBe('moveImpossible');
    });
  });

  describe('lobby commands', () => {
    it('start, add a bot and the options go through the shared session', () => {
      const store = useNinetyNineStore();
      store.restore('g1', 0, 'tok');
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      publish.mockClear();
      store.startGame();
      store.addBot();
      store.setOption('lives', '2');
      expect(sent().map(s => s.dest)[0]).toBe('/app/ninety-nine/start');
      expect(sent()).toHaveLength(3);
      expect(sent()[2].body).toMatchObject({ values: { lives: '2' } });
    });

    it('forfeit publishes the shared /app/forfeit', () => {
      const store = boot();
      store.forfeit();
      expect(sent()).toEqual([{ dest: '/app/forfeit', body: { gameId: 'g1', token: 'tok' } }]);
    });
  });
});
