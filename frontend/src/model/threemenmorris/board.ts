import type { MorrisState } from './MorrisState';

/**
 * Pure geometry and selection of the Three Men's Morris board. The server is the only source of the rules:
 * what may be played comes from `availableActions`, `board` and `legalMoves`, and no adjacency is known here.
 */

export interface MorrisPoint {
  row: number;
  col: number;
}

/** The nine points, row by row, as the server numbers them (index = point). */
export const POINTS: readonly MorrisPoint[] = Array.from({ length: 9 }, (_, i) => ({
  row: Math.floor(i / 3),
  col: i % 3,
}));

/** The eight segments to draw: three rows, three columns and the two diagonals through the centre. */
export const LINES: readonly (readonly [number, number])[] = [
  [0, 2], [3, 5], [6, 8],
  [0, 6], [1, 7], [2, 8],
  [0, 8], [2, 6],
];

const ARROWS: Record<string, MorrisPoint> = {
  ArrowUp: { row: -1, col: 0 },
  ArrowDown: { row: 1, col: 0 },
  ArrowLeft: { row: 0, col: -1 },
  ArrowRight: { row: 0, col: 1 },
};

/** The point an arrow key leads to on the grid (no wrap), or null at an edge or for another key. */
export function neighbourByArrow(point: number, key: string): number | null {
  const step = ARROWS[key];
  if (!step) return null;
  const row = POINTS[point].row + step.row;
  const col = POINTS[point].col + step.col;
  if (row < 0 || row > 2 || col < 0 || col > 2) return null;
  return row * 3 + col;
}

/** The screen-reader name of a point, from nine texts given in point order. */
export function pointName(point: number, texts: readonly string[]): string {
  return texts[point];
}

/** Whether the viewer has something to do now: the server gave an action, and it is the viewer's seat. */
function myTurn(state: MorrisState, mySeat: number): boolean {
  return state.outcome.status !== 'FINISHED' && state.currentSeat === mySeat && state.availableActions.length > 0;
}

/** The points the viewer may click now: free points to place on, or the pieces that have a legal move. */
export function selectable(state: MorrisState, mySeat: number): number[] {
  if (!myTurn(state, mySeat)) return [];
  if (state.availableActions.includes('PLACE')) {
    return state.board.flatMap((owner, point) => (owner === null ? [point] : []));
  }
  if (state.availableActions.includes('MOVE')) {
    return [...new Set(state.legalMoves.map(m => m.from))].sort((a, b) => a - b);
  }
  return [];
}

/** The legal destinations of the selected piece. */
export function targets(state: MorrisState, selected: number | null): number[] {
  if (selected === null) return [];
  return state.legalMoves.filter(m => m.from === selected).map(m => m.to);
}

export type PointIntent =
  | { kind: 'place'; point: number }
  | { kind: 'select'; point: number }
  | { kind: 'move'; from: number; to: number }
  | { kind: 'clear' }
  | { kind: 'none' };

/** What a click (or Enter) on a point means; nothing is applied locally, the server answers with the state. */
export function onPoint(state: MorrisState, mySeat: number, selected: number | null, point: number): PointIntent {
  const clickable = selectable(state, mySeat);
  if (clickable.length === 0) return { kind: 'none' };
  if (state.availableActions.includes('PLACE')) {
    return clickable.includes(point) ? { kind: 'place', point } : { kind: 'none' };
  }
  if (selected !== null && targets(state, selected).includes(point)) {
    return { kind: 'move', from: selected, to: point };
  }
  if (selected === point) return { kind: 'clear' };
  if (clickable.includes(point)) return { kind: 'select', point };
  return selected !== null ? { kind: 'clear' } : { kind: 'none' };
}

/** How long the winning line stays on the board before the end screen takes over (the end screen adds its own beat). */
export const WINNING_LINE_HOLD_MS = 600;
