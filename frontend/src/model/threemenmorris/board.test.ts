import { describe, it, expect } from 'vitest';
import { morrisState } from './morrisFixtures';
import { LINES, POINTS, neighbourByArrow, onPoint, pointName, selectable, targets } from './board';

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

describe('geometry', () => {
  it('has nine points on a 3x3 grid, row by row', () => {
    expect(POINTS).toHaveLength(9);
    expect(POINTS[0]).toEqual({ row: 0, col: 0 });
    expect(POINTS[4]).toEqual({ row: 1, col: 1 });
    expect(POINTS[8]).toEqual({ row: 2, col: 2 });
  });

  it('draws the eight lines: three rows, three columns, two diagonals', () => {
    expect(LINES).toHaveLength(8);
    expect(LINES).toContainEqual([0, 2]);
    expect(LINES).toContainEqual([0, 6]);
    expect(LINES).toContainEqual([0, 8]);
    expect(LINES).toContainEqual([2, 6]);
    expect(LINES).toContainEqual([3, 5]);
  });

  it('moves between points with the arrows and stops at the edges', () => {
    expect(neighbourByArrow(4, 'ArrowUp')).toBe(1);
    expect(neighbourByArrow(4, 'ArrowDown')).toBe(7);
    expect(neighbourByArrow(4, 'ArrowLeft')).toBe(3);
    expect(neighbourByArrow(4, 'ArrowRight')).toBe(5);
    expect(neighbourByArrow(0, 'ArrowUp')).toBeNull();
    expect(neighbourByArrow(0, 'ArrowLeft')).toBeNull();
    expect(neighbourByArrow(8, 'ArrowDown')).toBeNull();
    expect(neighbourByArrow(2, 'ArrowRight')).toBeNull();
    expect(neighbourByArrow(4, 'Enter')).toBeNull();
  });

  it('names a point from the given texts', () => {
    const texts = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i'];
    expect(pointName(4, texts)).toBe('e');
  });
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
