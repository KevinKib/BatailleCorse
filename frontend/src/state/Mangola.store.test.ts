import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { ARRANGE_DEBOUNCE_MS, useMangolaStore } from './Mangola.store';
import { mangolaState, placementState } from '../model/mangola/mangolaFixtures';
import type { LobbyView } from '../model/lobby/LobbyView';
import webSocketService from '../service/WebSocketService';

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: null, joined: false }],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 2, canStart: false,
    ...overrides,
  };
}

const bodyOf = (extra: Record<string, unknown> = {}) => JSON.stringify({ gameId: 'g1', token: 'tok-0', ...extra });

describe('Mangola store', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

  function seated(seat = 0) {
    const store = useMangolaStore();
    store.applyEvent({ type: 'seat-change', seat });
    store.restore('g1', seat, 'tok-0');
    return store;
  }
  const publishSpy = () => vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});

  describe('phases', () => {
    it('is connecting until a view arrives, then lobby, placement, playing and finished', () => {
      const store = seated();
      expect(store.phase).toBe('connecting');
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.phase).toBe('lobby');
      expect(store.lobby).not.toBeNull();
      expect(store.game).toBeNull();
      store.applyEvent({ type: 'state-update', state: placementState() });
      expect(store.phase).toBe('placement');
      store.applyEvent({ type: 'state-update', state: mangolaState() });
      expect(store.phase).toBe('playing');
      store.applyEvent({
        type: 'state-update',
        state: mangolaState({ phase: 'FINISHED', availableActions: [], outcome: { status: 'FINISHED', winner: 0, reason: 'NO_MOVE' } }),
      });
      expect(store.phase).toBe('finished');
    });

    it('isMyTurn follows the current seat in the play phase, and is false over', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: mangolaState() });
      expect(store.isMyTurn).toBe(true);
      store.applyEvent({ type: 'seat-change', seat: 1 });
      expect(store.isMyTurn).toBe(false);
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({
        type: 'state-update',
        state: mangolaState({ phase: 'FINISHED', availableActions: [], outcome: { status: 'FINISHED', winner: 1, reason: 'NO_MOVE' } }),
      });
      expect(store.isMyTurn).toBe(false);
    });

    it('iWon and isDraw read the outcome (seat 0 does not read as falsy)', () => {
      const store = seated(0);
      const finished = (winner: number | null, reason: 'NO_MOVE' | 'REPETITION' = 'NO_MOVE') =>
        mangolaState({ phase: 'FINISHED', availableActions: [], outcome: { status: 'FINISHED', winner, reason } });
      store.applyEvent({ type: 'state-update', state: finished(0) });
      expect(store.iWon).toBe(true);
      expect(store.isDraw).toBe(false);
      store.applyEvent({ type: 'state-update', state: finished(1) });
      expect(store.iWon).toBe(false);
      store.applyEvent({ type: 'state-update', state: finished(null, 'REPETITION') });
      expect(store.isDraw).toBe(true);
      expect(store.iWon).toBe(false);
    });

    it('repeatedTwice only when the position occurred twice and the game goes on', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: mangolaState() });
      expect(store.repeatedTwice).toBe(false);
      store.applyEvent({ type: 'state-update', state: mangolaState({ repetitions: 2 }) });
      expect(store.repeatedTwice).toBe(true);
    });
  });

  describe('board', () => {
    it('shows my side at the bottom whichever seat I hold (clockwise)', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: mangolaState({ direction: 'clockwise' }) });
      expect(store.rows[3].map(c => c.pit)).toEqual([0, 1, 2, 3, 4, 5, 6, 7]);
      expect(store.mySide).toBe('A');
      store.applyEvent({ type: 'seat-change', seat: 1 });
      expect(store.mySide).toBe('B');
      expect(store.rows[3].map(c => c.pit)).toEqual([31, 30, 29, 28, 27, 26, 25, 24]);
    });

    it('arrows follow the side of the viewer (clockwise)', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: mangolaState({ direction: 'clockwise' }) });
      expect(store.arrowOf(8)).toBe('right');
      store.applyEvent({ type: 'seat-change', seat: 1 });
      expect(store.arrowOf(24)).toBe('left');
    });

    it('draws anticlockwise by default: columns and arrows mirrored, my side still at the bottom', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: mangolaState() });
      expect(store.rows[3].map(c => c.pit)).toEqual([7, 6, 5, 4, 3, 2, 1, 0]);
      expect(store.arrowOf(8)).toBe('left');
      store.applyEvent({ type: 'seat-change', seat: 1 });
      expect(store.rows[3].map(c => c.pit)).toEqual([24, 25, 26, 27, 28, 29, 30, 31]);
      expect(store.arrowOf(24)).toBe('right');
    });

    it('rows and arrows follow game.direction, and a new state with the other direction redraws', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: mangolaState({ direction: 'anticlockwise' }) });
      expect(store.rows[3].map(c => c.pit)).toEqual([7, 6, 5, 4, 3, 2, 1, 0]);
      expect(store.arrowOf(15)).toBe('down');
      store.applyEvent({ type: 'state-update', state: mangolaState({ direction: 'clockwise', version: 1 }) });
      expect(store.rows[3].map(c => c.pit)).toEqual([0, 1, 2, 3, 4, 5, 6, 7]);
      expect(store.arrowOf(15)).toBe('down');
      expect(store.arrowOf(8)).toBe('right');
    });

    it('only the legal pits can be sown', () => {
      const publish = publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: mangolaState({ legalPits: [8, 1] }) });
      expect(store.canSow(8)).toBe(true);
      expect(store.canSow(9)).toBe(false);
      store.sow(9);
      expect(publish).not.toHaveBeenCalled();
      store.sow(8);
      expect(publish).toHaveBeenCalledWith('/app/mangola/sow', bodyOf({ pit: 8 }));
    });

    it('nothing is sown out of turn or once the game is over', () => {
      const publish = publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: mangolaState({ currentSeat: 1, legalPits: [8] }) });
      store.sow(8);
      expect(publish).not.toHaveBeenCalled();
    });

    it('does not throw when the move cannot be sent (socket down)', () => {
      vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new Error('down'); });
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: mangolaState({ legalPits: [8] }) });
      expect(() => store.sow(8)).not.toThrow();
    });

    it('marks the pits of the opponent last move only', () => {
      const store = seated(0);
      const trace = [
        { type: 'PICK' as const, pit: 22, count: 3 },
        { type: 'CAPTURE' as const, pit: 21, count: 6, facing: 13, behind: 5 },
      ];
      store.applyEvent({ type: 'state-update', state: mangolaState({ lastMove: { seat: 1, pit: 22, trace } }) });
      expect(store.opponentLastMarks).toEqual({ start: 22, captured: [13, 5] });
      store.applyEvent({ type: 'state-update', state: mangolaState({ lastMove: { seat: 0, pit: 8, trace: [] } }) });
      expect(store.opponentLastMarks).toEqual({ start: null, captured: [] });
      store.applyEvent({ type: 'state-update', state: mangolaState() });
      expect(store.opponentLastMarks).toEqual({ start: null, captured: [] });
    });
  });

  describe('placement draft', () => {
    it('starts from the draft of the server', () => {
      const store = seated(0);
      const draft = [4, 4, 4, 4, 4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0];
      store.applyEvent({ type: 'state-update', state: placementState(draft) });
      expect(store.draft).toEqual(draft);
      expect(store.seedsLeft).toBe(0);
    });

    it('counts the seeds left while the player adds and removes seeds', () => {
      publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState() });
      expect(store.seedsLeft).toBe(32);
      store.addSeed(2);
      store.addSeed(2);
      store.addSeed(5);
      expect(store.draft[2]).toBe(2);
      expect(store.seedsLeft).toBe(29);
      store.removeSeed(2);
      expect(store.draft[2]).toBe(1);
      store.removeSeed(0);
      expect(store.draft[0]).toBe(0);
      expect(store.seedsLeft).toBe(30);
    });

    it('never places more than 32 seeds', () => {
      publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState(Array(16).fill(2)) });
      store.addSeed(0);
      expect(store.draft[0]).toBe(2);
      expect(store.seedsLeft).toBe(0);
    });

    it('sends nothing while the draft is not complete: the server only accepts 32 seeds', () => {
      const publish = publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState() });
      store.addSeed(0);
      vi.advanceTimersByTime(ARRANGE_DEBOUNCE_MS * 3);
      expect(publish).not.toHaveBeenCalled();
    });

    it('sends the whole layout once, debounced, when the draft reaches 32', () => {
      const publish = publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState([2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 0]) });
      store.addSeed(15);
      store.removeSeed(14);
      store.addSeed(14);
      store.addSeed(15);
      expect(publish).not.toHaveBeenCalled();
      vi.advanceTimersByTime(ARRANGE_DEBOUNCE_MS - 1);
      expect(publish).not.toHaveBeenCalled();
      vi.advanceTimersByTime(1);
      expect(publish).toHaveBeenCalledTimes(1);
      expect(publish).toHaveBeenCalledWith('/app/mangola/arrange',
        bodyOf({ counts: [2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2] }));
    });

    it('presets replace the draft and are sent like any edit', () => {
      const publish = publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState() });
      store.applyPreset('two-per-pit');
      expect(store.draft).toEqual(Array(16).fill(2));
      vi.advanceTimersByTime(ARRANGE_DEBOUNCE_MS);
      expect(publish).toHaveBeenCalledTimes(1);
      store.applyPreset('clear');
      expect(store.draft).toEqual(Array(16).fill(0));
      store.applyPreset('checkerboard');
      expect(store.draft.reduce((a, b) => a + b, 0)).toBe(32);
      store.applyPreset('random');
      expect(store.seedsLeft).toBe(0);
    });

    it('confirm sends the pending layout first, then the confirmation', () => {
      const publish = publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState(Array(16).fill(0)) });
      store.applyPreset('two-per-pit');
      store.confirmLayout();
      expect(publish.mock.calls.map(c => c[0])).toEqual(['/app/mangola/arrange', '/app/mangola/confirmLayout']);
      // the debounced send is gone with the flush
      vi.advanceTimersByTime(ARRANGE_DEBOUNCE_MS * 2);
      expect(publish).toHaveBeenCalledTimes(2);
    });

    it('confirm is refused while seeds are left', () => {
      const publish = publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState() });
      expect(store.canConfirm).toBe(false);
      store.confirmLayout();
      expect(publish).not.toHaveBeenCalled();
    });

    it('confirm is possible with the layout the server holds', () => {
      const publish = publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState(Array(16).fill(2)) });
      expect(store.canConfirm).toBe(true);
      store.confirmLayout();
      expect(publish.mock.calls.map(c => c[0])).toEqual(['/app/mangola/confirmLayout']);
    });

    it('editLayout publishes the withdrawal', () => {
      const publish = publishSpy();
      const store = seated(0);
      store.applyEvent({
        type: 'state-update',
        state: placementState(Array(16).fill(2), {
          availableActions: ['EDIT_LAYOUT'],
          players: [
            { seat: 0, name: 'Alice', current: false, side: 'A', seeds: 32, ready: true },
            { seat: 1, name: 'Bob', current: false, side: 'B', seeds: null, ready: false },
          ],
        }),
      });
      expect(store.myReady).toBe(true);
      store.editLayout();
      expect(publish).toHaveBeenCalledWith('/app/mangola/editLayout', bodyOf());
    });

    it('cannot edit the draft once confirmed', () => {
      publishSpy();
      const store = seated(0);
      store.applyEvent({
        type: 'state-update',
        state: placementState(Array(16).fill(2), {
          availableActions: ['EDIT_LAYOUT'],
          players: [
            { seat: 0, name: 'Alice', current: false, side: 'A', seeds: 32, ready: true },
            { seat: 1, name: 'Bob', current: false, side: 'B', seeds: null, ready: false },
          ],
        }),
      });
      store.removeSeed(0);
      expect(store.draft).toEqual(Array(16).fill(2));
    });

    it('tells whether the opponent is ready, without knowing their layout', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState() });
      expect(store.opponentReady).toBe(false);
      store.applyEvent({
        type: 'state-update',
        state: placementState(Array(16).fill(0), {
          players: [
            { seat: 0, name: 'Alice', current: false, side: 'A', seeds: 0, ready: false },
            { seat: 1, name: 'Bob', current: false, side: 'B', seeds: null, ready: true },
          ],
        }),
      });
      expect(store.opponentReady).toBe(true);
    });

    it('rehydrates the draft from the server after a reload (a fresh store)', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState([1, 2, 3, 4, 5, 6, 7, 4, 0, 0, 0, 0, 0, 0, 0, 0]) });
      expect(store.draft).toEqual([1, 2, 3, 4, 5, 6, 7, 4, 0, 0, 0, 0, 0, 0, 0, 0]);
    });

    it('a state that arrives while the player is editing does not erase the edit', () => {
      publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState() });
      store.addSeed(3);
      // the opponent confirmed meanwhile: a fresh state with the old (empty) draft
      store.applyEvent({ type: 'state-update', state: placementState() });
      expect(store.draft[3]).toBe(1);
    });

    it('follows the server again once the edit is sent and the echo arrives', () => {
      publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState(Array(16).fill(2)) });
      store.removeSeed(0);
      store.addSeed(1);
      vi.advanceTimersByTime(ARRANGE_DEBOUNCE_MS);
      const sent = [1, 3, ...Array(14).fill(2)];
      store.applyEvent({ type: 'state-update', state: placementState(sent) });
      expect(store.draft).toEqual(sent);
      // later, another device changes my layout: the server wins
      store.applyEvent({ type: 'state-update', state: placementState(Array(16).fill(2)) });
      expect(store.draft).toEqual(Array(16).fill(2));
    });

    it('drops the pending send when the reveal moves the game to play', () => {
      const publish = publishSpy();
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState(Array(16).fill(2)) });
      store.removeSeed(0);
      store.addSeed(0);
      store.applyEvent({ type: 'state-update', state: mangolaState({ phase: 'PLAY' }) });
      vi.advanceTimersByTime(ARRANGE_DEBOUNCE_MS * 2);
      expect(publish).not.toHaveBeenCalled();
    });

    it('exposes the endless warning of the server', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState(Array(16).fill(2), { draftOnlyEndless: true }) });
      expect(store.draftOnlyEndless).toBe(true);
    });

    it('does not throw when the layout cannot be sent (socket down)', () => {
      vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new Error('down'); });
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState(Array(16).fill(0)) });
      store.applyPreset('two-per-pit');
      expect(() => vi.advanceTimersByTime(ARRANGE_DEBOUNCE_MS)).not.toThrow();
      expect(() => store.confirmLayout()).not.toThrow();
    });
  });

  describe('lobby and room', () => {
    it('a lobby error from a refused command shows in the lobby, not at the table', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      store.applyEvent({ type: 'error', eventType: 'START', message: 'Not enough players' });
      expect(store.lobbyError).toBe('Not enough players');
      store.applyEvent({ type: 'state-update', state: mangolaState() });
      store.applyEvent({ type: 'error', eventType: 'SOWN', message: 'Not your pit' });
      expect(store.lobbyError).toBeNull();
    });

    describe('refused confirmation (mangola.errors.stuckStart)', () => {
      const KEY = 'mangola.errors.stuckStart';
      function placing() {
        const store = seated(0);
        store.applyEvent({ type: 'state-update', state: placementState(Array(16).fill(2)) });
        return store;
      }

      it('keeps the key for the editor and leaves the lobby error alone', () => {
        const store = placing();
        store.applyEvent({ type: 'error', eventType: 'CONFIRM_LAYOUT', message: KEY });
        expect(store.placementError).toBe(KEY);
        expect(store.lobbyError).toBeNull();
        expect(store.phase).toBe('placement');
      });

      it('goes away when the layout changes, is taken back, or the game moves on', () => {
        const store = placing();
        store.applyEvent({ type: 'error', eventType: 'CONFIRM_LAYOUT', message: KEY });
        store.removeSeed(0);
        expect(store.placementError).toBeNull();

        store.applyEvent({ type: 'error', eventType: 'CONFIRM_LAYOUT', message: KEY });
        store.applyEvent({ type: 'state-update', state: mangolaState() });
        expect(store.placementError).toBeNull();
      });

      it('goes away when I try to confirm again', () => {
        publishSpy();
        const store = placing();
        store.applyEvent({ type: 'error', eventType: 'CONFIRM_LAYOUT', message: KEY });
        store.confirmLayout();
        expect(store.placementError).toBeNull();
      });

      it('ignores other in-game errors', () => {
        const store = placing();
        store.applyEvent({ type: 'error', eventType: 'ARRANGE', message: 'Something else' });
        expect(store.placementError).toBeNull();
      });
    });

    it('a FORFEIT event keeps its reason', () => {
      const store = seated(0);
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '', eventData: { loserSeat: 1, reason: 'DISCONNECTED' } });
      expect(store.lastForfeit).toEqual({ seat: 1, reason: 'DISCONNECTED' });
    });

    it('removed by the host clears the room', () => {
      const store = seated(0);
      store.applyEvent({ type: 'game-id-change', gameId: 'g1' });
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      store.applyEvent({ type: 'removed' });
      expect(store.removedByHost).toBe(true);
      expect(store.state).toBeNull();
      expect(store.gameId).toBeNull();
    });

    it('removed and leave drop the draft and its pending send', () => {
      const publish = publishSpy();
      vi.spyOn(webSocketService, 'unsubscribeFromSeat').mockImplementation(() => {});
      vi.spyOn(webSocketService, 'unsubscribeFromGame').mockImplementation(() => {});
      vi.spyOn(webSocketService, 'clearPresence').mockImplementation(() => {});
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState(Array(16).fill(2)) });
      store.removeSeed(0);
      store.addSeed(0);
      store.applyEvent({ type: 'removed' });
      vi.advanceTimersByTime(ARRANGE_DEBOUNCE_MS * 2);
      expect(publish).not.toHaveBeenCalled();
      expect(store.draft).toEqual(Array(16).fill(0));
    });

    it('the lobby after a rematch forgets the draft', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: placementState(Array(16).fill(2)) });
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.draft).toEqual(Array(16).fill(0));
    });

    it('forfeit and start delegate to the session', () => {
      const publish = publishSpy();
      const store = seated(0);
      store.forfeit();
      expect(publish).toHaveBeenCalledWith('/app/forfeit', expect.stringContaining('"gameId":"g1"'));
      expect(store.startGame()).toBe(true);
      expect(publish).toHaveBeenCalledWith('/app/mangola/start', expect.any(String));
    });

    it('leave sends the command and forgets the room', () => {
      publishSpy();
      vi.spyOn(webSocketService, 'unsubscribeFromSeat').mockImplementation(() => {});
      vi.spyOn(webSocketService, 'unsubscribeFromGame').mockImplementation(() => {});
      vi.spyOn(webSocketService, 'clearPresence').mockImplementation(() => {});
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.leave()).toBe(true);
      expect(store.state).toBeNull();
      expect(store.gameId).toBeNull();
    });
  });

  describe('computer seats', () => {
    it('remembers the computer seats of the lobby once the game has started (the game view does not say)', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: lobbyView({
        players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: 'Bot 1', joined: true, bot: true }],
      }) });
      expect(store.botSeats).toEqual([1]);
      store.applyEvent({ type: 'state-update', state: mangolaState() });
      expect(store.botSeats).toEqual([1]);
    });

    it('remembers the level of each computer seat from the lobby, and forgets it when removed', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: lobbyView({
        players: [{ seat: 0, name: 'Alice', joined: true },
          { seat: 1, name: 'Bot 1', joined: true, bot: true, seatOptions: { level: 'expert' } }],
      }) });
      store.applyEvent({ type: 'state-update', state: mangolaState() });
      expect(store.botLevels).toEqual({ 1: 'expert' });
      store.applyEvent({ type: 'removed' });
      expect(store.botLevels).toEqual({});
    });

    it('knows none when the game was joined after the lobby (a reload), or against a human', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: mangolaState() });
      expect(store.botSeats).toEqual([]);
      store.applyEvent({ type: 'state-update', state: lobbyView({
        players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: 'Bob', joined: true }],
      }) });
      expect(store.botSeats).toEqual([]);
    });
  });
});
