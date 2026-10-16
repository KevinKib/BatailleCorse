import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { usePresidentStore } from './President.store';
import webSocketService from '../service/WebSocketService';
import {
  buildPresidentCard as card, buildPresidentHand, buildPresidentPlayer, buildPresidentState, buildPresidentTrick,
} from '../model/fixtures/president';
import type { PresidentAction, PresidentPattern, PresidentState } from '../model/president/PresidentState';
import type { LobbyView } from '../model/lobby/LobbyView';

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Ann', joined: true }, { seat: 1, name: 'Bob', joined: true }, { seat: 2, name: 'Cy', joined: true }],
    hostSeat: 0, mySeat: 0, minPlayers: 3, maxPlayers: 8, canStart: true, lobbyVersion: 1, ...overrides,
  };
}

const set = (rank: string, count: number): PresidentPattern => ({ kind: 'SET', rank, count });
const HAND = buildPresidentHand(['4', 'DIAMOND'], ['9', 'HEART'], ['9', 'SPADE'], ['KING', 'HEART']);

/** A game where seat 0 (the viewer) holds `HAND` and may do `actions`. */
function game(actions: PresidentAction[], overrides: Partial<PresidentState> = {}): PresidentState {
  return buildPresidentState({ hand: HAND, availableActions: actions, ...overrides });
}

describe('President store', () => {
  let publish: ReturnType<typeof vi.spyOn>;

  function boot() {
    const store = usePresidentStore();
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
      store.applyEvent({
        type: 'state-update',
        state: game([], { version: 2, phase: 'FINISHED', outcome: { status: 'FINISHED', winners: [2], reason: 'ROUNDS' } }),
      });
      expect(store.screen).toBe('finished');
    });

    it('knows me, my turn and what the server lists', () => {
      const store = boot();
      store.applyEvent({ type: 'seat-change', seat: 1 });
      store.applyEvent({ type: 'state-update', state: game(['READY'], { currentSeat: 0 }) });
      expect(store.me?.seat).toBe(1);
      expect(store.isMyTurn).toBe(false);
      expect(store.can('READY')).toBe(true);
      expect(store.can('PLAY')).toBe(false);
      store.applyEvent({ type: 'seat-change', seat: 0 });
      expect(store.isMyTurn).toBe(true);
    });

    it('keeps every seat seen in the roster, even after it leaves the table, until the lobby', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game([]) });
      store.applyEvent({
        type: 'state-update',
        state: game([], { version: 2, players: [buildPresidentPlayer(0), buildPresidentPlayer(1, { name: 'Bob' })] }),
      });
      expect(store.roster[2].name).toBe('Player 2');
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.roster).toEqual({});
    });

    it('reads the rule chips from the options of the state', () => {
      const store = boot();
      expect(store.options).toEqual({});
      store.applyEvent({ type: 'state-update', state: game([], { options: { scoring: 'titles', length: 'endless' } }) });
      expect(store.options).toEqual({ scoring: 'titles', length: 'endless' });
    });

    it('turns the shared forfeit event into lastForfeit', () => {
      const store = boot();
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', eventData: { loserSeat: 1, reason: 'RESIGNED' }, message: '' });
      expect(store.lastForfeit).toEqual({ seat: 1, reason: 'RESIGNED' });
    });

    it('keeps the last table event, forgets it at a round start, and keeps presence events out of it', () => {
      const store = boot();
      const data = { seat: 1, action: 'PLAY', cards: [card('9', 'HEART')], count: null, outPlace: null, trickWinner: null, trickEndReason: null, barrierOpened: null };
      store.applyEvent({ type: 'event', eventType: 'PLAY', eventData: data, message: '' });
      expect(store.lastEvent).toEqual({ eventType: 'PLAY', data });
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED', eventData: { seat: 2 }, message: '' });
      expect(store.lastEvent?.eventType).toBe('PLAY');
      store.applyEvent({ type: 'event', eventType: 'ROUND_START', eventData: null, message: '' });
      expect(store.lastEvent).toBeNull();
    });
  });

  describe('hand and selection', () => {
    function turn(overrides: Partial<PresidentState> = {}) {
      const store = boot();
      store.applyEvent({
        type: 'state-update',
        state: game(['PLAY', 'PASS'], {
          legalPlays: [set('4', 1), set('9', 1), set('9', 2), set('KING', 1)], ...overrides,
        }),
      });
      return store;
    }

    it('taps select and unselect cards of my hand, and ignore any other card', () => {
      const store = turn();
      store.toggleCard('HEART_9');
      store.toggleCard('SPADE_9');
      expect(store.selectedCards.map(c => c.name)).toEqual(['HEART_9', 'SPADE_9']);
      store.toggleCard('HEART_9');
      expect(store.selectedCards.map(c => c.name)).toEqual(['SPADE_9']);
      store.toggleCard('CLUB_QUEEN');
      expect(store.selectedCards).toHaveLength(1);
    });

    it('lists the selection in hand order, whatever the tapping order', () => {
      const store = turn();
      store.toggleCard('SPADE_9');
      store.toggleCard('HEART_9');
      expect(store.selectedCards.map(c => c.name)).toEqual(['HEART_9', 'SPADE_9']);
    });

    it('enables Play on a legal combination only, and says why not otherwise', () => {
      const store = turn();
      expect(store.canPlay).toBe(false);
      expect(store.playHint).toEqual({ code: 'nothingSelected' });
      store.toggleCard('HEART_9');
      store.toggleCard('HEART_KING');
      expect(store.canPlay).toBe(false);
      expect(store.playHint).toEqual({ code: 'mixedRanks' });
      store.toggleCard('HEART_KING');
      expect(store.canPlay).toBe(true);
      expect(store.playHint).toBeNull();
      expect(store.selectedPattern).toEqual(set('9', 1));
    });

    it('suggests the cards that complete the selection', () => {
      const store = turn();
      store.toggleCard('HEART_9');
      expect(store.completions.map(c => c.name)).toEqual(['SPADE_9']);
    });

    it('does not select while the server lists no play', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['READY']) });
      store.toggleCard('HEART_9');
      expect(store.selectedCards).toEqual([]);
    });

    it('drops the cards that left my hand and keeps the others', () => {
      const store = turn();
      store.toggleCard('HEART_9');
      store.toggleCard('SPADE_9');
      store.applyEvent({
        type: 'state-update',
        state: game(['PLAY', 'PASS'], { version: 2, hand: HAND.filter(c => c.name !== 'HEART_9') }),
      });
      expect(store.selectedCards.map(c => c.name)).toEqual(['SPADE_9']);
    });

    it('is cleared by a new trick', () => {
      const first = buildPresidentTrick({ leader: 1, plays: [{ seat: 1, cards: [card('3', 'CLUB')] }], top: set('3', 1), lastPlayer: 1 });
      const store = turn({ trick: first });
      store.toggleCard('HEART_9');
      // someone plays on the same trick: the selection stays
      store.applyEvent({
        type: 'state-update',
        state: game(['PLAY', 'PASS'], {
          version: 2, trick: { ...first, plays: [...first.plays, { seat: 2, cards: [card('5', 'CLUB')] }], top: set('5', 1) },
        }),
      });
      expect(store.selectedCards).toHaveLength(1);
      // the trick closed and a new one opened
      store.applyEvent({
        type: 'state-update',
        state: game(['PLAY', 'PASS'], {
          version: 3, trick: buildPresidentTrick({ leader: 2, plays: [{ seat: 2, cards: [card('6', 'CLUB')] }], top: set('6', 1), lastPlayer: 2 }),
        }),
      });
      expect(store.selectedCards).toEqual([]);
    });

    it('is cleared by a new round', () => {
      const store = turn();
      store.toggleCard('HEART_9');
      store.applyEvent({ type: 'state-update', state: game(['PLAY', 'PASS'], { version: 2, round: 2 }) });
      expect(store.selectedCards).toEqual([]);
    });

    it('is cleared by a refused command, with a notice', () => {
      const store = turn();
      store.toggleCard('HEART_9');
      store.play();
      store.applyEvent({ type: 'error', eventType: 'PLAY', message: 'nope' });
      expect(store.selectedCards).toEqual([]);
      expect(store.busy).toBe(false);
      expect(store.notice).toBe('moveImpossible');
    });
  });

  describe('commands', () => {
    function turn() {
      const store = boot();
      store.applyEvent({
        type: 'state-update',
        state: game(['PLAY', 'PASS'], { legalPlays: [set('9', 1), set('9', 2), set('KING', 1)] }),
      });
      return store;
    }

    it('play sends the selected cards of a legal combination', () => {
      const store = turn();
      store.toggleCard('SPADE_9');
      store.toggleCard('HEART_9');
      expect(store.play()).toBe(true);
      expect(sent()).toEqual([{
        dest: '/app/president/play',
        body: { gameId: 'g1', token: 'tok', cards: [card('9', 'HEART'), card('9', 'SPADE')] },
      }]);
    });

    it('play sends nothing for an illegal or empty selection', () => {
      const store = turn();
      expect(store.play()).toBe(false);
      store.toggleCard('HEART_9');
      store.toggleCard('HEART_KING');
      expect(store.play()).toBe(false);
      expect(sent()).toEqual([]);
    });

    it('complete sends the completion cards the server offers, in or out of turn', () => {
      const store = boot();
      const completion = [card('9', 'HEART'), card('9', 'SPADE')];
      store.applyEvent({
        type: 'state-update',
        state: game(['COMPLETE'], { currentSeat: 1, completion }),
      });
      expect(store.canComplete).toBe(true);
      expect(store.complete()).toBe(true);
      expect(sent()).toEqual([{ dest: '/app/president/play', body: { gameId: 'g1', token: 'tok', cards: completion } }]);
    });

    it('complete sends nothing without a completion on offer', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['PLAY', 'PASS']) });
      expect(store.canComplete).toBe(false);
      expect(store.complete()).toBe(false);
      expect(sent()).toEqual([]);
    });

    it('pass is sent when the server lists it', () => {
      const store = turn();
      expect(store.pass()).toBe(true);
      expect(sent()).toEqual([{ dest: '/app/president/pass', body: { gameId: 'g1', token: 'tok' } }]);
    });

    it('pass and ready are not sent when the server does not list them', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game([]) });
      expect(store.pass()).toBe(false);
      expect(store.ready()).toBe(false);
      expect(sent()).toEqual([]);
    });

    it('ready is sent at the round-over barrier', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['READY'], { phase: 'ROUND_OVER' }) });
      expect(store.ready()).toBe(true);
      expect(sent()).toEqual([{ dest: '/app/president/ready', body: { gameId: 'g1', token: 'tok' } }]);
    });

    it('giveBack sends the selected cards once their number is the one owed', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['GIVE_BACK'], { phase: 'EXCHANGE', toGiveBack: 2 }) });
      store.toggleCard('DIAMOND_4');
      expect(store.canGiveBack).toBe(false);
      expect(store.giveBack()).toBe(false);
      store.toggleCard('HEART_9');
      expect(store.canGiveBack).toBe(true);
      expect(store.giveBack()).toBe(true);
      expect(sent()).toEqual([{
        dest: '/app/president/giveBack',
        body: { gameId: 'g1', token: 'tok', cards: [card('4', 'DIAMOND'), card('9', 'HEART')] },
      }]);
    });

    it('suggestGiveBack selects the lowest cards that break no pair, then giveBack sends them', () => {
      const store = boot();
      const hand = buildPresidentHand(['4', 'DIAMOND'], ['4', 'SPADE'], ['6', 'HEART'], ['9', 'HEART'], ['KING', 'HEART']);
      store.applyEvent({ type: 'state-update', state: game(['GIVE_BACK'], { phase: 'EXCHANGE', toGiveBack: 1, hand }) });
      store.suggestGiveBack();
      expect(store.selectedCards.map(c => c.name)).toEqual(['HEART_6']);
      expect(store.canGiveBack).toBe(true);
    });

    it('suggestGiveBack does nothing when no return is owed', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game([], { phase: 'EXCHANGE', toGiveBack: 0 }) });
      store.suggestGiveBack();
      expect(store.selectedCards).toEqual([]);
    });

    it('is busy from a command until the next state, and does not send twice meanwhile', () => {
      const store = turn();
      store.pass();
      store.pass();
      expect(sent()).toHaveLength(1);
      expect(store.busy).toBe(true);
      store.applyEvent({ type: 'state-update', state: game([], { version: 2 }) });
      expect(store.busy).toBe(false);
    });

    it('is not busy when the command could not be sent (socket down), keeps the selection and says so', () => {
      const store = turn();
      store.toggleCard('HEART_KING');
      publish.mockImplementation(() => { throw new TypeError('no underlying STOMP connection'); });
      expect(store.play()).toBe(false);
      expect(store.busy).toBe(false);
      expect(store.notice).toBe('connection');
      expect(store.selectedCards).toHaveLength(1);
    });

    it('forfeit and the lobby commands go through the session', () => {
      const store = turn();
      store.forfeit();
      store.setOption('scoring', 'titles');
      expect(sent().map(m => m.dest)).toEqual(['/app/forfeit', '/app/lobby/setOptions']);
    });
  });

  describe('hydration', () => {
    it('hydrates the state from the REST endpoint on restore + hydrate and applies it', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: true, status: 200, json: async () => game(['PLAY'], { version: 7 }),
      }));
      const store = boot();
      expect(await store.hydrate()).toBe('ok');
      expect(store.game?.version).toBe(7);
      vi.unstubAllGlobals();
    });
  });

  describe('leaving', () => {
    it('forgets the game when removed by the host', () => {
      const store = boot();
      store.applyEvent({ type: 'state-update', state: game(['PLAY']) });
      store.toggleCard('HEART_9');
      store.applyEvent({ type: 'removed' });
      expect(store.state).toBeNull();
      expect(store.selectedCards).toEqual([]);
      expect(store.removedByHost).toBe(true);
    });
  });
});
