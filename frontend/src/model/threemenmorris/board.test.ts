import { describe, it, expect } from 'vitest';
import { morrisState } from './morrisFixtures';
import { colourOf, onPoint, selectable, targets } from './board';

const N = null;

// White (seat 0) on 0, 1 and 8; Black (seat 1) on 3, 4, 5: the movement phase, White to move.
const movement = (overrides = {}) => morrisState({
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

describe('placement', () => {
  it('lets me place on the free points on my turn only', () => {
    const s = morrisState({ board: [0, N, N, 1, N, N, N, N, N] });
    expect(selectable(s, 0)).toEqual([1, 2, 4, 5, 6, 7, 8]);
    expect(onPoint(s, 0, null, 4)).toEqual({ kind: 'place', point: 4 });
    expect(onPoint(s, 0, null, 0)).toEqual({ kind: 'none' });
  });

  it('offers nothing when the server gives me no action (not my turn)', () => {
    const s = morrisState({ availableActions: [] });
    expect(selectable(s, 1)).toEqual([]);
    expect(onPoint(s, 1, null, 4)).toEqual({ kind: 'none' });
  });

  it('offers nothing when the current seat is not mine, whatever the actions say', () => {
    expect(selectable(morrisState({ currentSeat: 1 }), 0)).toEqual([]);
  });
});

describe('placement with legalPlacements', () => {
  it('offers exactly the points the server lists, the centre being closed in Picaria', () => {
    const s = morrisState({
      variant: 'picaria',
      legalPlacements: [0, 1, 2, 3, 5, 6, 7, 8],
    });
    expect(selectable(s, 0)).toEqual([0, 1, 2, 3, 5, 6, 7, 8]);
    expect(onPoint(s, 0, null, 4)).toEqual({ kind: 'none' });
    expect(onPoint(s, 0, null, 3)).toEqual({ kind: 'place', point: 3 });
  });

  it('trusts an empty list from the server over the free points', () => {
    const s = morrisState({ variant: 'achi', legalPlacements: [] });
    expect(selectable(s, 0)).toEqual([]);
  });

  it('falls back to the free points when the server leaves legalPlacements out (Tapatan)', () => {
    const s = morrisState({ board: [0, N, N, 1, N, N, N, N, N] });
    expect(s.legalPlacements).toBeUndefined();
    expect(selectable(s, 0)).toEqual([1, 2, 4, 5, 6, 7, 8]);
  });
});

describe('colours', () => {
  it('gives White to seat 0 and Black to seat 1 when the server leaves the colours out', () => {
    const s = morrisState();
    expect(s.whiteSeat).toBeUndefined();
    expect(colourOf(s, 0)).toBe('WHITE');
    expect(colourOf(s, 1)).toBe('BLACK');
  });

  it('reads players[].colour when the host plays Black', () => {
    const s = morrisState({
      whiteSeat: 1,
      players: [
        { seat: 0, name: 'Alice', current: false, piecesToPlace: 3, colour: 'BLACK' },
        { seat: 1, name: 'Bob', current: true, piecesToPlace: 3, colour: 'WHITE' },
      ],
      currentSeat: 1,
    });
    expect(colourOf(s, 0)).toBe('BLACK');
    expect(colourOf(s, 1)).toBe('WHITE');
  });

  it('falls back to whiteSeat when a player carries no colour', () => {
    expect(colourOf(morrisState({ whiteSeat: 1 }), 1)).toBe('WHITE');
    expect(colourOf(morrisState({ whiteSeat: 1 }), 0)).toBe('BLACK');
  });
});

describe('movement', () => {
  const s = movement();

  it('makes movable the pieces that have a legal move, once each', () => {
    expect(selectable(s, 0)).toEqual([0, 1, 8]);
  });

  it('selects a movable piece, then moves it to a legal destination', () => {
    expect(onPoint(s, 0, null, 1)).toEqual({ kind: 'select', point: 1 });
    expect(targets(s, 1)).toEqual([2]);
    expect(onPoint(s, 0, 1, 2)).toEqual({ kind: 'move', from: 1, to: 2 });
  });

  it('changes the selection on another movable piece and clears on the selected one', () => {
    expect(onPoint(s, 0, 1, 0)).toEqual({ kind: 'select', point: 0 });
    expect(onPoint(s, 0, 1, 1)).toEqual({ kind: 'clear' });
  });

  it('clears on any other point and ignores them with no selection', () => {
    expect(onPoint(s, 0, 1, 7)).toEqual({ kind: 'clear' });
    expect(onPoint(s, 0, 1, 3)).toEqual({ kind: 'clear' });
    expect(onPoint(s, 0, null, 7)).toEqual({ kind: 'none' });
    expect(onPoint(s, 0, null, 3)).toEqual({ kind: 'none' });
  });

  it('has no targets without a selection or for a piece that cannot move', () => {
    expect(targets(s, null)).toEqual([]);
    expect(targets(s, 3)).toEqual([]);
  });

  it('does nothing when it is not my turn', () => {
    const waiting = movement({ availableActions: [], legalMoves: [] });
    expect(selectable(waiting, 0)).toEqual([]);
    expect(onPoint(waiting, 0, null, 1)).toEqual({ kind: 'none' });
  });

  it('does nothing once the game is over', () => {
    const over = movement({
      phase: 'FINISHED', availableActions: [], legalMoves: [],
      outcome: { status: 'FINISHED', winner: 0, reason: 'BLOCKED' },
    });
    expect(onPoint(over, 0, 1, 2)).toEqual({ kind: 'none' });
  });
});
