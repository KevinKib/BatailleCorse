import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useKoboStore, KOBO_LOG_SIZE } from './Kobo.store';
import webSocketService from '../service/WebSocketService';
import { buildKoboState, buildKoboPlayer, buildKoboSlot, buildKoboCard } from '../model/fixtures/kobo';
import type { KoboAction, KoboState } from '../model/kobo/KoboState';
import type { LobbyView } from '../model/lobby/LobbyView';
import { slotKey } from '../model/kobo/interaction';

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1', players: [{ seat: 0, name: 'Ann', joined: true }, { seat: 1, name: 'Bob', joined: true }],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 6, canStart: true, lobbyVersion: 1, ...overrides,
  };
}

function game(actions: KoboAction[], overrides: Partial<KoboState> = {}): KoboState {
  return buildKoboState({ availableActions: actions, ...overrides });
}

describe('Kobo store', () => {
  let publish: ReturnType<typeof vi.spyOn>;

  function boot() {
    const store = useKoboStore();
    store.restore('g1', 0, 'tok');
    publish.mockClear();
    return store;
  }
  const sent = () => publish.mock.calls.map(c => ({ dest: c[0], body: JSON.parse(c[1] as string) }));

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
      const store = boot();
      expect(store.screen).toBe('connecting');
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.screen).toBe('lobby');
      expect(store.lobby).not.toBeNull();
      expect(store.game).toBeNull();
      store.applyEvent({ type: 'state-update', state: game([]) });
      expect(store.screen).toBe('playing');
      expect(store.game).not.toBeNull();
      store.applyEvent({ type: 'state-update', state: game([], { version: 2, phase: 'FINISHED', outcome: { status: 'FINISHED', winners: [0, 1], reason: 'SCORE' } }) });
      expect(store.screen).toBe('finished');
    });

    it('knows me, my turn and what the server lists', () => {
      const store = boot();
      store.applyEvent({ type: 'seat-change', seat: 1 });
      store.applyEvent({ type: 'state-update', state: game(['MATCH_DISCARD'], { currentSeat: 0 }) });
      expect(store.me?.seat).toBe(1);
      expect(store.isMyTurn).toBe(false);
      expect(store.can('MATCH_DISCARD')).toBe(true);
      expect(store.can('DRAW')).toBe(false);
      store.applyEvent({ type: 'seat-change', seat: 0 });
      expect(store.isMyTurn).toBe(true);
    });

    it('keeps every seat seen in the roster, even after it leaves the table', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game([]) });
      store.applyEvent({
        type: 'state-update',
        state: game([], { version: 2, players: [buildKoboPlayer(0, { name: 'Ann', current: true })] }),
      });
      expect(store.roster[1].name).toBe('Player 1');
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.roster).toEqual({});
    });

    it('turns the shared forfeit event into lastForfeit', () => {
      const store = boot();
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', eventData: { loserSeat: 1, reason: 'RESIGNED' }, message: '' });
      expect(store.lastForfeit).toEqual({ seat: 1, reason: 'RESIGNED' });
    });
  });

  describe('commands', () => {
    it('sends the turn commands to their endpoints', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['READY']) });
      store.ready();
      expect(sent().at(-1)).toMatchObject({ dest: '/app/kobo/ready' });
    });

    it('is busy from a turn command until the next state, and does not send twice meanwhile', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['DRAW']) });
      store.draw();
      store.draw();
      expect(sent().filter(m => m.dest === '/app/kobo/draw')).toHaveLength(1);
      expect(store.busy).toBe(true);
      store.applyEvent({ type: 'state-update', state: game([], { version: 2 }) });
      expect(store.busy).toBe(false);
    });

    it('clears busy on a refusal and says why, by the type of the refused command', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['DRAW']) });
      store.draw();
      store.applyEvent({ type: 'error', eventType: 'DRAW', message: 'nope' });
      expect(store.busy).toBe(false);
      expect(store.notice).toBe('moveImpossible');
      store.applyEvent({ type: 'event', eventType: 'DRAW', eventData: null, message: '' });
      expect(store.notice).toBeNull();
    });

    it('is not busy when the command could not be sent (socket down), and says so', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['DRAW']) });
      publish.mockImplementation(() => { throw new TypeError('no underlying STOMP connection'); });
      store.draw();
      expect(store.busy).toBe(false);
      expect(store.notice).toBe('connection');
    });

    it('a refused match reads "too late" and never blocks the next attempt', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['MATCH_DISCARD'], { discardTop: buildKoboCard('KING') }) });
      store.armMatch();
      store.pickSlot(0, 1);
      expect(store.busy).toBe(false);
      store.applyEvent({ type: 'error', eventType: 'MATCH', message: 'late' });
      expect(store.notice).toBe('matchTooLate');
      store.armMatch();
      store.pickSlot(0, 2);
      expect(sent().filter(m => m.dest === '/app/kobo/match')).toHaveLength(2);
    });

    it('sends giveTen for the chosen seat', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['GIVE_TEN'], { phase: 'GIFT' }) });
      store.giveTen(1);
      expect(sent().at(-1)).toMatchObject({ dest: '/app/kobo/giveTen', body: { seat: 1 } });
    });
  });

  describe('clicks on cards', () => {
    it('swaps the drawn card with the clicked own slot', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['SWAP_DRAWN', 'DISCARD_DRAWN'], { phase: 'DECISION' }) });
      expect(Object.keys(store.interaction.intents)).toContain(slotKey(0, 2));
      store.pickSlot(0, 2);
      expect(sent().at(-1)).toMatchObject({ dest: '/app/kobo/swapDrawn', body: { slot: 2 } });
    });

    it('ignores a click on a slot that is not clickable', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['DRAW']) });
      store.pickSlot(1, 0);
      expect(publish).not.toHaveBeenCalled();
    });

    it('a blind swap is two clicks: my card (kept locally), then theirs (sent)', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['BLIND_SWAP', 'SKIP_POWER'], { phase: 'POWER', pendingPower: 'BLIND_SWAP' }) });
      store.pickSlot(0, 3);
      expect(publish).not.toHaveBeenCalled();
      expect(store.pending).toEqual({ kind: 'blind-own', ownSlot: 3 });
      store.pickSlot(1, 0);
      expect(sent().at(-1)).toMatchObject({ dest: '/app/kobo/blindSwap', body: { ownSlot: 3, seat: 1, slot: 0 } });
      expect(store.pending).toEqual({ kind: 'none' });
    });

    it('matching own card: arm, click, one match with the rank and revision on screen', () => {
      const store = boot();
      store.applyEvent({
        type: 'state-update',
        state: game(['MATCH_DISCARD'], {
          discardTop: buildKoboCard('KING'),
          players: [buildKoboPlayer(0, { slots: [buildKoboSlot(0, { revision: 6 }), buildKoboSlot(1)] }), buildKoboPlayer(1)],
        }),
      });
      store.armMatch();
      expect(store.pending).toEqual({ kind: 'match-armed' });
      store.pickSlot(0, 0);
      expect(sent().at(-1)).toEqual({
        dest: '/app/kobo/match',
        body: { gameId: 'g1', token: 'tok', seat: 0, slot: 0, expectedTopRank: 'KING', expectedRevision: 6 },
      });
      expect(store.pending).toEqual({ kind: 'none' });
    });

    it('matching another player card: arm, their card, my card to give, then one match', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['MATCH_DISCARD'], { discardTop: buildKoboCard('9') }) });
      store.armMatch();
      store.pickSlot(1, 2);
      expect(publish).not.toHaveBeenCalled();
      expect(store.pending.kind).toBe('match-give');
      store.pickSlot(0, 1);
      expect(sent()).toHaveLength(1);
      expect(sent()[0].body).toMatchObject({ seat: 1, slot: 2, giveSlot: 1, expectedTopRank: '9', expectedRevision: 0 });
    });

    it('armMatch toggles, only while MATCH_DISCARD is listed', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['DRAW']) });
      store.armMatch();
      expect(store.pending).toEqual({ kind: 'none' });
      store.applyEvent({ type: 'state-update', state: game(['DRAW', 'MATCH_DISCARD'], { version: 2 }) });
      store.armMatch();
      expect(store.pending).toEqual({ kind: 'match-armed' });
      store.armMatch();
      expect(store.pending).toEqual({ kind: 'none' });
    });
  });

  describe('pending selection', () => {
    function armedWithTarget() {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['MATCH_DISCARD'], { discardTop: buildKoboCard('9') }) });
      store.armMatch();
      store.pickSlot(1, 2);
      return store;
    }

    it('is cleared when the action leaves availableActions', () => {
      const store = armedWithTarget();
      store.applyEvent({ type: 'state-update', state: game([], { version: 2 }) });
      expect(store.pending).toEqual({ kind: 'none' });
    });

    it('is cleared when the target revision changed', () => {
      const store = armedWithTarget();
      store.applyEvent({
        type: 'state-update',
        state: game(['MATCH_DISCARD'], {
          version: 2, discardTop: buildKoboCard('9'),
          players: [buildKoboPlayer(0, { current: true }), buildKoboPlayer(1, { slots: [0, 1, 2, 3].map(i => buildKoboSlot(i, { revision: i === 2 ? 1 : 0 })) })],
        }),
      });
      expect(store.pending).toEqual({ kind: 'none' });
    });

    it('is cleared when the target slot is emptied', () => {
      const store = armedWithTarget();
      store.applyEvent({
        type: 'state-update',
        state: game(['MATCH_DISCARD'], {
          version: 2, discardTop: buildKoboCard('9'),
          players: [buildKoboPlayer(0, { current: true }), buildKoboPlayer(1, { slots: [0, 1, 2, 3].map(i => buildKoboSlot(i, { occupied: i !== 2 })) })],
        }),
      });
      expect(store.pending).toEqual({ kind: 'none' });
    });

    it('is cleared on Escape (cancelPending)', () => {
      const store = armedWithTarget();
      store.cancelPending();
      expect(store.pending).toEqual({ kind: 'none' });
    });

    it('is cleared on a new round', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['MATCH_DISCARD']) });
      store.armMatch();
      store.applyEvent({ type: 'state-update', state: game(['MATCH_DISCARD'], { version: 2, round: 2 }) });
      expect(store.pending).toEqual({ kind: 'none' });
    });

    it('survives a state that changes nothing it depends on', () => {
      const store = armedWithTarget();
      store.applyEvent({ type: 'state-update', state: game(['MATCH_DISCARD'], { version: 2, discardTop: buildKoboCard('9') }) });
      expect(store.pending.kind).toBe('match-give');
    });

    it('is cleared when the room goes back to a lobby', () => {
      const store = armedWithTarget();
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.pending).toEqual({ kind: 'none' });
    });
  });

  describe('event log', () => {
    const entry = (eventType: string, seat = 0) => ({
      type: 'event' as const, eventType, message: '',
      eventData: { seat, action: eventType, slot: null, targetSeat: null, targetSlot: null, card: null, givenSlot: null, success: null, barrierOpened: null },
    });

    it('keeps the newest first and at most 20', () => {
      const store = boot();
      for (let i = 0; i < KOBO_LOG_SIZE + 5; i++) store.applyEvent(entry('DRAW', i));
      expect(store.events).toHaveLength(KOBO_LOG_SIZE);
      expect(store.events[0].data?.seat).toBe(KOBO_LOG_SIZE + 4);
    });

    it('is cleared on ROUND_START', () => {
      const store = boot();
      store.applyEvent(entry('DRAW'));
      store.applyEvent(entry('ROUND_START'));
      expect(store.events).toEqual([]);
    });

    it('does not record the presence and forfeit events', () => {
      const store = boot();
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED', eventData: { disconnectedSeat: 1, deadlineEpochMs: 1 }, message: '' });
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', eventData: { loserSeat: 1 }, message: '' });
      expect(store.events).toEqual([]);
    });
  });

  describe('presence', () => {
    it('tracks a disconnected opponent', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game([]) });
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED', eventData: { disconnectedSeat: 1, deadlineEpochMs: 4102444800000 }, message: '' });
      expect(store.disconnections[1]).toBe(4102444800000);
    });
  });

  describe('room', () => {
    it('Start is sent once to /app/kobo/start', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      store.startGame();
      store.startGame();
      expect(sent().filter(m => m.dest === '/app/kobo/start')).toHaveLength(1);
      expect(store.starting).toBe(true);
    });

    it('Add bot is refused by the server for Kobo: the store does not offer it', () => {
      const store = boot() as unknown as Record<string, unknown>;
      expect(store.addBot).toBeUndefined();
    });

    it('leave forgets the game', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game([]) });
      vi.spyOn(webSocketService, 'unsubscribeFromSeat').mockImplementation(() => {});
      vi.spyOn(webSocketService, 'unsubscribeFromGame').mockImplementation(() => {});
      vi.spyOn(webSocketService, 'clearPresence').mockImplementation(() => {});
      expect(store.leave()).toBe(true);
      expect(store.state).toBeNull();
      expect(store.screen).toBe('connecting');
    });
  });
});
