import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useKiloDeMerdeStore } from './KiloDeMerde.store';
import webSocketService from '../service/WebSocketService';
import {
  kiloDeMerdeFinishedState, kiloDeMerdeRaceState, kiloDeMerdeRoundOverState, kiloDeMerdeStepState,
} from '../model/kilodemerde/fixtures';
import type { KiloDeMerdeEventData, KiloDeMerdeState } from '../model/kilodemerde/KiloDeMerdeState';
import type { LobbyView } from '../model/lobby/LobbyView';

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Ann', joined: true }, { seat: 1, name: 'Bob', joined: true }],
    hostSeat: 0, mySeat: 0, minPlayers: 3, maxPlayers: 10, canStart: true, lobbyVersion: 1, ...overrides,
  };
}

const eventData = (overrides: Partial<KiloDeMerdeEventData>): KiloDeMerdeEventData => ({
  seat: null, round: null, step: null, card: null, kilosAfter: null, rank: null, returnedCount: null, ...overrides,
});

describe('KiloDeMerde store', () => {
  let publish: ReturnType<typeof vi.spyOn>;

  function boot(state: KiloDeMerdeState = kiloDeMerdeStepState) {
    const store = useKiloDeMerdeStore();
    store.restore('g1', 0, 'tok');
    store.applyEvent({ type: 'state-update', state });
    publish.mockClear();
    return store;
  }
  const sent = () => publish.mock.calls.map(c => ({ dest: c[0], body: JSON.parse(c[1] as string) }));
  // The state the server sends once my card is chosen: the version moved on, my choice shows.
  const afterMyPass = (): KiloDeMerdeState => ({
    ...kiloDeMerdeStepState, version: kiloDeMerdeStepState.version + 1,
    myChoice: kiloDeMerdeStepState.hand[0], availableActions: ['SLAP'],
    players: kiloDeMerdeStepState.players.map(p => (p.seat === 0 ? { ...p, chosen: true } : p)),
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
      const store = useKiloDeMerdeStore();
      expect(store.screen).toBe('connecting');
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.screen).toBe('lobby');
      expect(store.lobby).not.toBeNull();
      expect(store.game).toBeNull();
      store.applyEvent({ type: 'state-update', state: kiloDeMerdeStepState });
      expect(store.screen).toBe('playing');
      store.applyEvent({ type: 'state-update', state: kiloDeMerdeRoundOverState });
      expect(store.screen).toBe('playing');
      store.applyEvent({ type: 'state-update', state: kiloDeMerdeFinishedState });
      expect(store.screen).toBe('finished');
    });

    it('reads my seat, hand, options, pile and the actions from the state', () => {
      const store = boot();
      expect(store.mySeat).toBe(0);
      expect(store.me?.seat).toBe(0);
      expect(store.hand).toHaveLength(4);
      expect(store.options).toEqual({ rounds: '10' });
      expect(store.can('PASS')).toBe(true);
      expect(store.can('SLAP')).toBe(true);
    });

    it('keeps every seat seen, so a forfeited name can still be read', () => {
      const store = boot();
      store.applyEvent({
        type: 'state-update',
        state: { ...kiloDeMerdeStepState, version: 15, players: kiloDeMerdeStepState.players.filter(p => p.seat !== 2) },
      });
      expect(store.roster[2]).toBeDefined();
    });
  });

  describe('event log (what the table animates)', () => {
    it('logs nothing from the first state, then each event newest first with the table it happened at', () => {
      const store = boot();
      expect(store.events).toEqual([]);
      store.applyEvent({ type: 'state-update', state: { ...kiloDeMerdeStepState, version: 15, step: 4 } });
      store.applyEvent({ type: 'event', eventType: 'STEP', eventData: eventData({ round: 2, step: 4 }) });
      store.applyEvent({ type: 'event', eventType: 'DRAW', eventData: eventData({ seat: 2 }) });
      expect(store.events.map(e => e.eventType)).toEqual(['DRAW', 'STEP']);
      expect(store.events[1]).toMatchObject({
        activeSeats: [0, 1, 2], received: kiloDeMerdeStepState.lastReceived, data: { step: 4 },
      });
      expect(store.events[0].id).toBeGreaterThan(store.events[1].id);
      expect(store.lastEvent?.eventType).toBe('DRAW');
    });

    it('lists the active seats only', () => {
      const store = boot();
      store.applyEvent({
        type: 'state-update',
        state: {
          ...kiloDeMerdeStepState, version: 15,
          players: kiloDeMerdeStepState.players.map(p => (p.seat === 1 ? { ...p, active: false } : p)),
        },
      });
      store.applyEvent({ type: 'event', eventType: 'STEP', eventData: eventData({}) });
      expect(store.events[0].activeSeats).toEqual([0, 2]);
    });

    it('does not log presence events and never the start of the game', () => {
      const store = boot();
      store.applyEvent({ type: 'event', eventType: 'START', eventData: null });
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED', eventData: { seat: 2 } });
      expect(store.events).toEqual([]);
    });

    it('reports a forfeit to the room and to the table (the round is voided)', () => {
      const store = boot();
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', eventData: { loserSeat: 2, reason: 'RESIGNED' } });
      expect(store.lastForfeit).toEqual({ seat: 2, reason: 'RESIGNED' });
      expect(store.events).toEqual([]);
    });

    it('is capped to the newest entries', () => {
      const store = boot();
      for (let i = 0; i < 40; i++) {
        store.applyEvent({ type: 'event', eventType: 'PASS_CHOSEN', eventData: eventData({ seat: 1 }) });
      }
      expect(store.events.length).toBeLessThanOrEqual(20);
    });

    it('is cleared when the room goes back to a lobby', () => {
      const store = boot();
      store.applyEvent({ type: 'event', eventType: 'STEP', eventData: eventData({}) });
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.events).toEqual([]);
      expect(store.lastEvent).toBeNull();
    });
  });

  describe('pass', () => {
    it('sends a card of my hand, and is busy until my choice shows', () => {
      const store = boot();
      const card = store.hand[1];
      expect(store.pass(card)).toBe(true);
      expect(sent()).toEqual([{ dest: '/app/kilo-de-merde/pass', body: { gameId: 'g1', token: 'tok', card } }]);
      expect(store.busy).toBe(true);
      expect(store.pass(store.hand[0])).toBe(false);
      expect(sent()).toHaveLength(1);
      store.applyEvent({ type: 'state-update', state: afterMyPass() });
      expect(store.busy).toBe(false);
    });

    it('stays busy through a state that is not the answer (another seat chose), until my choice shows', () => {
      const store = boot();
      store.pass(store.hand[0]);
      store.applyEvent({
        type: 'state-update',
        state: {
          ...kiloDeMerdeStepState, version: 15,
          players: kiloDeMerdeStepState.players.map(p => (p.seat === 2 ? { ...p, chosen: true } : p)),
        },
      });
      expect(store.busy).toBe(true);
      store.applyEvent({ type: 'state-update', state: afterMyPass() });
      expect(store.busy).toBe(false);
    });

    it('is freed by a resolved step even if my choice no longer shows', () => {
      const store = boot();
      store.pass(store.hand[0]);
      store.applyEvent({
        type: 'state-update', state: { ...kiloDeMerdeStepState, version: 16, step: kiloDeMerdeStepState.step + 1 },
      });
      expect(store.busy).toBe(false);
    });

    it('is refused for a card I do not hold, once I chose, or when the server did not offer PASS', () => {
      const store = boot();
      expect(store.pass({ rank: '2', suit: 'HEART', name: 'HEART_2' })).toBe(false);
      expect(sent()).toEqual([]);
      const chosen = boot(afterMyPass());
      expect(chosen.pass(chosen.hand[1])).toBe(false);
      const race = boot(kiloDeMerdeRaceState);
      expect(race.pass(race.hand[0])).toBe(false);
      expect(sent()).toEqual([]);
    });

    it('reports a socket that is down, and a refusal from the server', () => {
      const store = boot();
      publish.mockImplementation(() => { throw new Error('down'); });
      expect(store.pass(store.hand[0])).toBe(false);
      expect(store.notice).toBe('connection');
      expect(store.busy).toBe(false);
      publish.mockImplementation(() => {});
      expect(store.pass(store.hand[0])).toBe(true);
      store.applyEvent({ type: 'error', message: 'ALREADY_CHOSE' });
      expect(store.busy).toBe(false);
      expect(store.notice).toBe('moveImpossible');
    });
  });

  describe('slap', () => {
    it('sends the slap when the server offers it, and is busy until my slap or the phase shows', () => {
      const store = boot(kiloDeMerdeRaceState);
      expect(store.slap()).toBe(true);
      expect(sent()).toEqual([{ dest: '/app/kilo-de-merde/slap', body: { gameId: 'g1', token: 'tok' } }]);
      expect(store.busy).toBe(true);
      expect(store.slap()).toBe(false);
      // Another seat slaps meanwhile: the race moved, my slap did not show yet.
      store.applyEvent({
        type: 'state-update',
        state: {
          ...kiloDeMerdeRaceState, version: 53, race: { firstSlapper: 3, order: [3, 7, 5, 8] },
          players: kiloDeMerdeRaceState.players.map(p => (p.seat === 8 ? { ...p, slapRank: 4 } : p)),
        },
      });
      expect(store.busy).toBe(true);
      store.applyEvent({
        type: 'state-update',
        state: {
          ...kiloDeMerdeRaceState, version: 54, availableActions: [],
          players: kiloDeMerdeRaceState.players.map(p => (p.seat === 0 ? { ...p, slapRank: 4 } : p)),
        },
      });
      expect(store.busy).toBe(false);
    });

    it('is freed when the slap opened a race or ended the round', () => {
      const store = boot();
      expect(store.slap()).toBe(true);
      store.applyEvent({
        type: 'state-update', state: { ...kiloDeMerdeRaceState, version: 99 },
      });
      expect(store.busy).toBe(false);
    });

    it('is refused when the server did not offer SLAP', () => {
      const store = boot(kiloDeMerdeRoundOverState);
      expect(store.slap()).toBe(false);
      expect(sent()).toEqual([]);
    });
  });

  describe('room', () => {
    it('forfeits through the shared command and leaves cleanly', () => {
      const store = boot();
      store.forfeit();
      expect(sent()).toEqual([{ dest: '/app/forfeit', body: { gameId: 'g1', token: 'tok' } }]);
    });

    it('forgets everything on leave', () => {
      const store = boot();
      store.leave();
      expect(store.state).toBeNull();
      expect(store.events).toEqual([]);
      expect(store.gameId).toBeNull();
    });
  });
});
