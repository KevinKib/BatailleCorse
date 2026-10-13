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
});
