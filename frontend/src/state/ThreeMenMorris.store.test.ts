import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useThreeMenMorrisStore } from './ThreeMenMorris.store';
import { morrisState } from '../model/threemenmorris/morrisFixtures';
import type { MorrisState } from '../model/threemenmorris/MorrisState';
import type { LobbyView } from '../model/lobby/LobbyView';
import webSocketService from '../service/WebSocketService';

const N = null;

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: null, joined: false }],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 2, canStart: false,
    ...overrides,
  };
}

const movementState = (overrides: Partial<MorrisState> = {}) => morrisState({
  phase: 'MOVEMENT',
  board: [0, 0, N, 1, 1, 1, N, N, 0],
  players: [
    { seat: 0, name: 'Alice', current: true, piecesToPlace: 0 },
    { seat: 1, name: 'Bob', current: false, piecesToPlace: 0 },
  ],
  availableActions: ['MOVE'],
  legalMoves: [{ from: 1, to: 2 }, { from: 0, to: 6 }, { from: 8, to: 7 }],
  ...overrides,
});

describe('ThreeMenMorris store', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  function seated(seat = 0) {
    const store = useThreeMenMorrisStore();
    store.applyEvent({ type: 'seat-change', seat });
    return store;
  }

  it('is connecting until a view arrives, then lobby, playing and finished', () => {
    const store = seated();
    expect(store.phase).toBe('connecting');
    store.applyEvent({ type: 'state-update', state: lobbyView() });
    expect(store.phase).toBe('lobby');
    expect(store.lobby).not.toBeNull();
    expect(store.game).toBeNull();
    store.applyEvent({ type: 'state-update', state: morrisState() });
    expect(store.phase).toBe('playing');
    expect(store.game).not.toBeNull();
    store.applyEvent({
      type: 'state-update',
      state: morrisState({ phase: 'FINISHED', availableActions: [], outcome: { status: 'FINISHED', winner: 0, reason: 'LINE' } }),
    });
    expect(store.phase).toBe('finished');
  });

  it('reads the game phase (placement, movement) from the state', () => {
    const store = seated();
    expect(store.gamePhase).toBeNull();
    store.applyEvent({ type: 'state-update', state: morrisState() });
    expect(store.gamePhase).toBe('PLACEMENT');
    store.applyEvent({ type: 'state-update', state: movementState() });
    expect(store.gamePhase).toBe('MOVEMENT');
  });

  it('isMyTurn follows the current seat, and myColour follows my seat (seat 0 is White)', () => {
    const store = seated(0);
    store.applyEvent({ type: 'state-update', state: morrisState() });
    expect(store.isMyTurn).toBe(true);
    expect(store.myColour).toBe('WHITE');
    store.applyEvent({ type: 'seat-change', seat: 1 });
    expect(store.isMyTurn).toBe(false);
    expect(store.myColour).toBe('BLACK');
  });

  describe('colours and variant', () => {
    const hostBlack = (overrides: Partial<MorrisState> = {}) => morrisState({
      whiteSeat: 1,
      currentSeat: 1,
      players: [
        { seat: 0, name: 'Alice', current: false, piecesToPlace: 3, colour: 'BLACK' },
        { seat: 1, name: 'Bob', current: true, piecesToPlace: 3, colour: 'WHITE' },
      ],
      ...overrides,
    });

    it('myColour is Black for the host when the host plays Black, and White for the guest', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: hostBlack() });
      expect(store.myColour).toBe('BLACK');
      expect(store.colourOf(0)).toBe('BLACK');
      expect(store.colourOf(1)).toBe('WHITE');
      store.applyEvent({ type: 'seat-change', seat: 1 });
      expect(store.myColour).toBe('WHITE');
      expect(store.isMyTurn).toBe(true);
    });

    it('keeps seat 0 White when the server leaves the colour fields out', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: morrisState() });
      expect(store.colourOf(0)).toBe('WHITE');
      expect(store.colourOf(1)).toBe('BLACK');
    });

    it('reads an absent variant as Tapatan and exposes the variant of the game', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: morrisState() });
      expect(store.variant).toBe('tapatan');
      store.applyEvent({ type: 'state-update', state: morrisState({ variant: 'achi' }) });
      expect(store.variant).toBe('achi');
    });

    it('offers the points of legalPlacements: a point the server leaves out is never proposed while placing', () => {
      const store = seated(0);
      store.applyEvent({
        type: 'state-update',
        state: morrisState({ variant: 'achi', legalPlacements: [0, 1, 2, 3, 5, 6, 7, 8] }),
      });
      expect(store.selectablePoints).not.toContain(4);
      expect(store.selectablePoints).toHaveLength(8);
    });
  });

  it('setOption sends the lobby option to the server, nothing is changed locally', () => {
    const store = seated(0);
    store.restore('g1', 0, 'tok-0');
    store.applyEvent({ type: 'state-update', state: lobbyView({ options: { variant: 'tapatan' } }) });
    const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
    store.setOption('variant', 'achi');
    expect(publish).toHaveBeenCalledWith('/app/lobby/setOptions',
      JSON.stringify({ gameId: 'g1', token: 'tok-0', values: { variant: 'achi' } }));
    expect(store.lobby!.options).toEqual({ variant: 'tapatan' });
  });

  describe('last opponent move', () => {
    it('marks the opponent placement, then drops it when I play', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: morrisState() });
      expect(store.opponentLastMove).toEqual([]);
      store.applyEvent({ type: 'event', eventType: 'PLACE', eventData: { seat: 1, action: 'PLACE', point: 3, from: null, to: null } });
      expect(store.opponentLastMove).toEqual([3]);
      store.applyEvent({ type: 'event', eventType: 'PLACE', eventData: { seat: 0, action: 'PLACE', point: 4, from: null, to: null } });
      expect(store.opponentLastMove).toEqual([]);
    });

    it('marks both ends of an opponent move', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: movementState() });
      store.applyEvent({ type: 'event', eventType: 'MOVE', eventData: { seat: 1, action: 'MOVE', point: null, from: 5, to: 2 } });
      expect(store.opponentLastMove).toEqual([5, 2]);
    });
  });

  describe('draws', () => {
    it('reads an absent drawActions as OFFER_DRAW in the movement phase and nothing otherwise', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: morrisState() });
      expect(store.drawActions).toEqual([]);
      expect(store.canOfferDraw).toBe(false);
      store.applyEvent({ type: 'state-update', state: movementState() });
      expect(store.drawActions).toEqual(['OFFER_DRAW']);
      expect(store.canOfferDraw).toBe(true);
    });

    it('reads a written drawActions as it is, even empty', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: movementState({ drawActions: [] }) });
      expect(store.drawActions).toEqual([]);
      expect(store.canOfferDraw).toBe(false);
      store.applyEvent({ type: 'state-update', state: movementState({ drawActions: ['ACCEPT_DRAW', 'DECLINE_DRAW'] }) });
      expect(store.canOfferDraw).toBe(false);
      expect(store.canAnswerDraw).toBe(true);
    });

    it('offers nothing once the game is over', () => {
      const store = seated(0);
      store.applyEvent({
        type: 'state-update',
        state: movementState({ phase: 'FINISHED', outcome: { status: 'FINISHED', winner: null, reason: 'DRAW_AGREED' } }),
      });
      expect(store.drawActions).toEqual([]);
    });

    it('tells whose offer is pending: mine, or the opponent one I may answer', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: movementState() });
      expect(store.drawOfferedBy).toBeNull();
      store.applyEvent({ type: 'state-update', state: movementState({ drawOffer: { seat: 0 }, drawActions: [] }) });
      expect(store.drawOfferedBy).toBe(0);
      expect(store.myDrawOfferPending).toBe(true);
      expect(store.incomingDrawOffer).toBe(false);
      store.applyEvent({ type: 'state-update', state: movementState({ drawOffer: { seat: 1 }, drawActions: ['ACCEPT_DRAW', 'DECLINE_DRAW'] }) });
      expect(store.myDrawOfferPending).toBe(false);
      expect(store.incomingDrawOffer).toBe(true);
    });

    it('a move clears the banner: the next state has no offer', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: movementState({ drawOffer: { seat: 1 }, drawActions: ['ACCEPT_DRAW', 'DECLINE_DRAW'] }) });
      expect(store.incomingDrawOffer).toBe(true);
      store.applyEvent({ type: 'state-update', state: movementState({ drawOffer: null }) });
      expect(store.incomingDrawOffer).toBe(false);
    });

    it('shows the repetition hint only when the position occurred twice', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: movementState() });
      expect(store.repeatedTwice).toBe(false);
      store.applyEvent({ type: 'state-update', state: movementState({ repetitions: 2 }) });
      expect(store.repeatedTwice).toBe(true);
    });

    it('is a draw when the outcome has no winner', () => {
      const store = seated(0);
      store.applyEvent({
        type: 'state-update',
        state: movementState({ phase: 'FINISHED', outcome: { status: 'FINISHED', winner: null, reason: 'DRAW_REPETITION' } }),
      });
      expect(store.isDraw).toBe(true);
      expect(store.iWon).toBe(false);
      store.applyEvent({ type: 'state-update', state: movementState() });
      expect(store.isDraw).toBe(false);
    });

    it('publishes the three draw commands', () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const store = seated(0);
      store.restore('g1', 0, 'tok-0');
      store.applyEvent({ type: 'state-update', state: movementState() });
      store.offerDraw();
      store.acceptDraw();
      store.declineDraw();
      const body = JSON.stringify({ gameId: 'g1', token: 'tok-0' });
      expect(publish.mock.calls).toEqual([
        ['/app/three-men-morris/offerDraw', body],
        ['/app/three-men-morris/acceptDraw', body],
        ['/app/three-men-morris/declineDraw', body],
      ]);
    });

    it('does not throw when a draw command cannot be sent (socket down)', () => {
      vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new Error('down'); });
      const store = seated(0);
      store.restore('g1', 0, 'tok-0');
      expect(() => store.offerDraw()).not.toThrow();
    });

    it('keeps DRAW_OFFERED and DRAW_DECLINED for the live region, and drops them on the next move', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: movementState() });
      expect(store.drawNotice).toBeNull();
      store.applyEvent({ type: 'event', eventType: 'DRAW_OFFERED', message: '', eventData: { seat: 1, action: 'OFFER_DRAW', point: null, from: null, to: null } });
      expect(store.drawNotice).toEqual({ kind: 'offered', seat: 1 });
      expect(store.opponentLastMove).toEqual([]);
      store.applyEvent({ type: 'event', eventType: 'DRAW_DECLINED', message: '', eventData: { seat: 1, action: 'DECLINE_DRAW', point: null, from: null, to: null } });
      expect(store.drawNotice).toEqual({ kind: 'declined', seat: 1 });
      store.applyEvent({ type: 'event', eventType: 'MOVE', message: '', eventData: { seat: 1, action: 'MOVE', point: null, from: 5, to: 2 } });
      expect(store.drawNotice).toBeNull();
    });

    it('does not take a GAME_OVER with action ACCEPT_DRAW for a move', () => {
      const store = seated(0);
      store.applyEvent({ type: 'event', eventType: 'PLACE', message: '', eventData: { seat: 0, action: 'PLACE', point: 4, from: null, to: null } });
      store.applyEvent({ type: 'event', eventType: 'GAME_OVER', message: '', eventData: { seat: 1, action: 'ACCEPT_DRAW', point: null, from: null, to: null } });
      expect(store.lastMove).toEqual({ seat: 0, action: 'PLACE', point: 4, from: null, to: null });
      expect(store.drawNotice).toBeNull();
    });

    it('forgets the draw notice when the room goes back to a lobby', () => {
      const store = seated(0);
      store.applyEvent({ type: 'event', eventType: 'DRAW_OFFERED', message: '', eventData: { seat: 1, action: 'OFFER_DRAW', point: null, from: null, to: null } });
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.drawNotice).toBeNull();
    });
  });

  it('is not my turn once the game is over', () => {
    const store = seated(0);
    store.applyEvent({
      type: 'state-update',
      state: morrisState({ phase: 'FINISHED', availableActions: [], outcome: { status: 'FINISHED', winner: 0, reason: 'LINE' } }),
    });
    expect(store.isMyTurn).toBe(false);
  });

  it('iWon compares the numeric outcome winner with my seat (seat 0 does not read as falsy)', () => {
    const store = seated(0);
    const finished = (winner: number) => morrisState({
      phase: 'FINISHED', availableActions: [], outcome: { status: 'FINISHED', winner, reason: 'LINE' },
    });
    store.applyEvent({ type: 'state-update', state: finished(0) });
    expect(store.iWon).toBe(true);
    store.applyEvent({ type: 'state-update', state: finished(1) });
    expect(store.iWon).toBe(false);
    store.applyEvent({ type: 'state-update', state: morrisState() });
    expect(store.iWon).toBe(false);
  });

  it('exposes the points I may click and the targets of the selection', () => {
    const store = seated(0);
    store.applyEvent({ type: 'state-update', state: movementState() });
    expect(store.selectablePoints).toEqual([0, 1, 8]);
    expect(store.targetPoints).toEqual([]);
    store.pointClicked(1);
    expect(store.selected).toBe(1);
    expect(store.targetPoints).toEqual([2]);
  });

  it('placement: a click on a free point publishes the place command, nothing is changed locally', () => {
    const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
    const store = seated(0);
    store.restore('g1', 0, 'tok-0');
    store.applyEvent({ type: 'state-update', state: morrisState() });
    store.pointClicked(4);
    expect(publish).toHaveBeenCalledWith('/app/three-men-morris/place', JSON.stringify({ gameId: 'g1', token: 'tok-0', point: 4 }));
    expect(store.game!.board[4]).toBeNull();
  });

  it('movement: select, then a click on a destination publishes the move and drops the selection', () => {
    const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
    const store = seated(0);
    store.restore('g1', 0, 'tok-0');
    store.applyEvent({ type: 'state-update', state: movementState() });
    store.pointClicked(1);
    expect(publish).not.toHaveBeenCalled();
    store.pointClicked(2);
    expect(publish).toHaveBeenCalledWith('/app/three-men-morris/move', JSON.stringify({ gameId: 'g1', token: 'tok-0', from: 1, to: 2 }));
    expect(store.selected).toBeNull();
  });

  describe('removal after a mill (Six Men\'s Morris)', () => {
    // White (seat 0) closed a mill on 0-1-2 and must take a Black piece: 8 9 10 form a mill, only 12 can be taken.
    const removalState = (overrides: Partial<MorrisState> = {}) => morrisState({
      variant: 'six',
      phase: 'MOVEMENT',
      board: [0, 0, 0, N, N, N, N, N, 1, 1, 1, N, 1, N, N, N],
      players: [
        { seat: 0, name: 'Alice', current: true, piecesToPlace: 0, lost: 0 },
        { seat: 1, name: 'Bob', current: false, piecesToPlace: 0, lost: 2 },
      ],
      availableActions: ['REMOVE'],
      removal: { seat: 0, mill: [0, 1, 2] },
      legalRemovals: [12],
      ...overrides,
    });

    it('offers the removable pieces only, and no destination', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: removalState() });
      expect(store.selectablePoints).toEqual([12]);
      expect(store.targetPoints).toEqual([]);
    });

    it('a click on a removable piece publishes the remove command, nothing is changed locally', () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const store = seated(0);
      store.restore('g1', 0, 'tok-0');
      store.applyEvent({ type: 'state-update', state: removalState() });
      store.pointClicked(12);
      expect(publish).toHaveBeenCalledWith('/app/three-men-morris/remove', JSON.stringify({ gameId: 'g1', token: 'tok-0', point: 12 }));
      expect(store.game!.board[12]).toBe(1);
    });

    it('sends nothing for a click on a protected piece', () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const store = seated(0);
      store.restore('g1', 0, 'tok-0');
      store.applyEvent({ type: 'state-update', state: removalState() });
      store.pointClicked(9);
      expect(publish).not.toHaveBeenCalled();
    });

    it('does not throw when the remove command cannot be sent (socket down)', () => {
      vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new Error('down'); });
      const store = seated(0);
      store.restore('g1', 0, 'tok-0');
      store.applyEvent({ type: 'state-update', state: removalState() });
      expect(() => store.pointClicked(12)).not.toThrow();
    });

    it('keeps the last removal for the captured marker, from REMOVE and from the GAME_OVER it caused', () => {
      const store = seated(0);
      expect(store.capturedPoint).toBeNull();
      store.applyEvent({ type: 'event', eventType: 'REMOVE', message: '', eventData: { seat: 1, action: 'REMOVE', point: 6 } });
      expect(store.lastRemoval).toEqual({ seat: 1, action: 'REMOVE', point: 6 });
      expect(store.capturedPoint).toBe(6);
      store.applyEvent({ type: 'event', eventType: 'GAME_OVER', message: '', eventData: { seat: 0, action: 'REMOVE', point: 13 } });
      expect(store.capturedPoint).toBe(13);
    });

    it('does not take a removal for a move, so the last-move ring stays on the mill', () => {
      const store = seated(0);
      store.applyEvent({ type: 'event', eventType: 'MOVE', message: '', eventData: { seat: 1, action: 'MOVE', point: null, from: 3, to: 4, mill: [4, 5, 6] } });
      store.applyEvent({ type: 'event', eventType: 'REMOVE', message: '', eventData: { seat: 1, action: 'REMOVE', point: 6 } });
      expect(store.lastMove).toEqual({ seat: 1, action: 'MOVE', point: null, from: 3, to: 4, mill: [4, 5, 6] });
      expect(store.opponentLastMove).toEqual([3, 4]);
    });

    it('drops the captured marker at the next placement or move, and when the room goes back to a lobby', () => {
      const store = seated(0);
      store.applyEvent({ type: 'event', eventType: 'REMOVE', message: '', eventData: { seat: 1, action: 'REMOVE', point: 6 } });
      store.applyEvent({ type: 'state-update', state: removalState() });
      expect(store.capturedPoint).toBe(6);
      store.applyEvent({ type: 'event', eventType: 'MOVE', message: '', eventData: { seat: 0, action: 'MOVE', point: null, from: 1, to: 2 } });
      expect(store.capturedPoint).toBeNull();
      store.applyEvent({ type: 'event', eventType: 'REMOVE', message: '', eventData: { seat: 1, action: 'REMOVE', point: 6 } });
      store.applyEvent({ type: 'state-update', state: lobbyView() });
      expect(store.capturedPoint).toBeNull();
    });

    it('tells whose removal is pending: mine, or the one I am waiting for', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: removalState() });
      expect(store.removalPending).toBe(true);
      expect(store.iMustRemove).toBe(true);
      store.applyEvent({ type: 'seat-change', seat: 1 });
      store.applyEvent({ type: 'state-update', state: removalState({ availableActions: [], legalRemovals: undefined }) });
      expect(store.removalPending).toBe(true);
      expect(store.iMustRemove).toBe(false);
      store.applyEvent({ type: 'state-update', state: morrisState() });
      expect(store.removalPending).toBe(false);
    });
  });

  it('changes and clears the selection on clicks and with clearSelection', () => {
    const store = seated(0);
    store.applyEvent({ type: 'state-update', state: movementState() });
    store.pointClicked(1);
    store.pointClicked(0);
    expect(store.selected).toBe(0);
    store.pointClicked(7);
    expect(store.selected).toBeNull();
    store.pointClicked(8);
    store.clearSelection();
    expect(store.selected).toBeNull();
  });

  it('keeps the selection when the command cannot be sent (socket down)', () => {
    vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new Error('down'); });
    const store = seated(0);
    store.restore('g1', 0, 'tok-0');
    store.applyEvent({ type: 'state-update', state: movementState() });
    store.pointClicked(1);
    expect(() => store.pointClicked(2)).not.toThrow();
    expect(store.selected).toBe(1);
  });

  it('drops the selection whenever a new state arrives, even an identical one', () => {
    const store = seated(0);
    store.applyEvent({ type: 'state-update', state: movementState() });
    store.pointClicked(1);
    store.applyEvent({ type: 'state-update', state: movementState() });
    expect(store.selected).toBeNull();
  });

  it('keeps the last move from PLACE, MOVE and GAME_OVER data', () => {
    const store = seated(0);
    store.applyEvent({ type: 'event', eventType: 'PLACE', message: '', eventData: { seat: 1, action: 'PLACE', point: 4, from: null, to: null } });
    expect(store.lastMove).toEqual({ seat: 1, action: 'PLACE', point: 4, from: null, to: null });
    store.applyEvent({ type: 'event', eventType: 'MOVE', message: '', eventData: { seat: 0, action: 'MOVE', point: null, from: 1, to: 2 } });
    expect(store.lastMove).toEqual({ seat: 0, action: 'MOVE', point: null, from: 1, to: 2 });
    store.applyEvent({ type: 'event', eventType: 'GAME_OVER', message: '', eventData: { seat: 1, action: 'PLACE', point: 8, from: null, to: null } });
    expect(store.lastMove).toEqual({ seat: 1, action: 'PLACE', point: 8, from: null, to: null });
  });

  it('forgets the last move when the room goes back to a lobby', () => {
    const store = seated(0);
    store.applyEvent({ type: 'event', eventType: 'PLACE', message: '', eventData: { seat: 1, action: 'PLACE', point: 4, from: null, to: null } });
    store.applyEvent({ type: 'state-update', state: lobbyView() });
    expect(store.lastMove).toBeNull();
  });

  it('H2: a FORFEIT event keeps its reason, apart from the last move', () => {
    const store = seated(0);
    store.applyEvent({ type: 'event', eventType: 'PLACE', message: '', eventData: { seat: 0, action: 'PLACE', point: 4, from: null, to: null } });
    store.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '', eventData: { loserSeat: 1, reason: 'DISCONNECTED' } });
    expect(store.lastForfeit).toEqual({ seat: 1, reason: 'DISCONNECTED' });
    expect(store.lastMove).toEqual({ seat: 0, action: 'PLACE', point: 4, from: null, to: null });
    store.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '', eventData: { loserSeat: 0, reason: 'RESIGNED' } });
    expect(store.lastForfeit).toEqual({ seat: 0, reason: 'RESIGNED' });
  });

  it('keeps the forfeit outcome reason of the finished state', () => {
    const store = seated(0);
    store.applyEvent({
      type: 'state-update',
      state: morrisState({ phase: 'FINISHED', availableActions: [], outcome: { status: 'FINISHED', winner: 0, reason: 'FORFEIT' } }),
    });
    expect(store.game!.outcome).toEqual({ status: 'FINISHED', winner: 0, reason: 'FORFEIT' });
    expect(store.iWon).toBe(true);
  });

  it('a lobby error from a refused command shows in the lobby, not at the table', () => {
    const store = seated(0);
    store.applyEvent({ type: 'state-update', state: lobbyView() });
    store.applyEvent({ type: 'error', eventType: 'START', message: 'Not enough players' });
    expect(store.lobbyError).toBe('Not enough players');
    store.applyEvent({ type: 'state-update', state: morrisState() });
    store.applyEvent({ type: 'error', eventType: 'PLACE', message: 'Point occupied' });
    expect(store.lobbyError).toBeNull();
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

  it('forfeit and start delegate to the session', () => {
    const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
    const store = seated(0);
    store.restore('g1', 0, 'tok-0');
    store.forfeit();
    expect(publish).toHaveBeenCalledWith('/app/forfeit', expect.stringContaining('"gameId":"g1"'));
    expect(store.startGame()).toBe(true);
    expect(publish).toHaveBeenCalledWith('/app/three-men-morris/start', expect.any(String));
    expect(store.starting).toBe(true);
  });

  it('leave sends the command and forgets the room', () => {
    vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
    vi.spyOn(webSocketService, 'unsubscribeFromSeat').mockImplementation(() => {});
    vi.spyOn(webSocketService, 'unsubscribeFromGame').mockImplementation(() => {});
    vi.spyOn(webSocketService, 'clearPresence').mockImplementation(() => {});
    const store = seated(0);
    store.restore('g1', 0, 'tok-0');
    store.applyEvent({ type: 'state-update', state: lobbyView() });
    expect(store.leave()).toBe(true);
    expect(store.state).toBeNull();
    expect(store.gameId).toBeNull();
  });

  describe('computer levels', () => {
    it('reads the level of each computer seat from the game view, so a reload mid-game knows it', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: morrisState({
        players: [
          { seat: 0, name: 'Alice', current: true, piecesToPlace: 3 },
          { seat: 1, name: 'Bot 1', current: false, piecesToPlace: 3, bot: true, level: 'perfect' },
        ],
      }) });
      expect(store.botLevels).toEqual({ 1: 'perfect' });
    });

    it('prefers the level of the game view and falls back to the one recorded from the lobby', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: lobbyView({
        players: [{ seat: 0, name: 'Alice', joined: true },
          { seat: 1, name: 'Bot 1', joined: true, bot: true, seatOptions: { level: 'greedy' } }],
      }) });
      const withLevel = (level?: string) => morrisState({
        players: [
          { seat: 0, name: 'Alice', current: true, piecesToPlace: 3 },
          { seat: 1, name: 'Bot 1', current: false, piecesToPlace: 3, bot: true, level },
        ],
      });
      store.applyEvent({ type: 'state-update', state: withLevel() });
      expect(store.botLevels).toEqual({ 1: 'greedy' });
      store.applyEvent({ type: 'state-update', state: withLevel('perfect') });
      expect(store.botLevels).toEqual({ 1: 'perfect' });
    });

    it('remembers the level of each computer seat from the lobby, and forgets it on leaving', () => {
      const store = seated(0);
      store.applyEvent({ type: 'state-update', state: lobbyView({
        players: [{ seat: 0, name: 'Alice', joined: true },
          { seat: 1, name: 'Bot 1', joined: true, bot: true, seatOptions: { level: 'perfect' } }],
      }) });
      store.applyEvent({ type: 'state-update', state: morrisState() });
      expect(store.botLevels).toEqual({ 1: 'perfect' });
      expect(store.leave()).toBe(true);
      expect(store.botLevels).toEqual({});
    });
  });
});
