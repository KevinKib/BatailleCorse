import type { DraughtsColour, DraughtsLegalMove, DraughtsState } from './DraughtsState';

/**
 * Pure selection logic of the draughts board. The server is the only source of the rules: what may be played
 * comes from `availableActions` and `legalMoves`, and no move is computed here. Squares are numbered 1 to 50 the
 * FMJD way; the drawing is the components' business.
 */

/** The colour shown at the bottom of the board. */
export type DraughtsOrientation = DraughtsColour;

export interface SquareCoords {
  row: number;
  col: number;
}

const SQUARES = 50;
const PER_ROW = 5;

/** The index of a square in reading order of the visible board (0 is the top-left dark square). */
function visibleIndex(square: number, orientation: DraughtsOrientation): number {
  return orientation === 'WHITE' ? square - 1 : SQUARES - square;
}

function squareOfVisibleIndex(index: number, orientation: DraughtsOrientation): number {
  return orientation === 'WHITE' ? index + 1 : SQUARES - index;
}

/** The cell of a square on the 10 x 10 visible board; Black at the bottom turns the board by 180 degrees. */
export function squareCoords(square: number, orientation: DraughtsOrientation): SquareCoords {
  const index = visibleIndex(square, orientation);
  const row = Math.floor(index / PER_ROW);
  const slot = index % PER_ROW;
  return { row, col: row % 2 === 0 ? 2 * slot + 1 : 2 * slot };
}

/** The square on a cell of the visible board, null on a light cell. */
export function squareAt(row: number, col: number, orientation: DraughtsOrientation): number | null {
  if (row < 0 || row > 9 || col < 0 || col > 9 || (row + col) % 2 === 0) return null;
  const slot = row % 2 === 0 ? (col - 1) / 2 : col / 2;
  return squareOfVisibleIndex(row * PER_ROW + slot, orientation);
}

/** The side at the bottom for a seat: its own colour; White for a seat the game does not know. */
export function orientationOf(state: Pick<DraughtsState, 'players'>, seat: number): DraughtsOrientation {
  return state.players.find(p => p.seat === seat)?.colour ?? 'WHITE';
}

/** Whether the viewer has something to move now: the server offered MOVE, and it is the viewer's seat. */
function canMove(state: DraughtsState, mySeat: number): boolean {
  return state.outcome.status !== 'FINISHED' && state.currentSeat === mySeat && state.availableActions.includes('MOVE');
}

/** The moves the viewer may pick from: only the capturing ones while a capture is compulsory. */
function playable(state: DraughtsState, mySeat: number): DraughtsLegalMove[] {
  if (!canMove(state, mySeat)) return [];
  // The server's legal moves already hold only the compulsory (majority) captures when a capture is due.
  return state.legalMoves;
}

const unique = (squares: number[]): number[] => [...new Set(squares)];

/** The pieces the viewer may pick now. */
export function selectable(state: DraughtsState, mySeat: number): number[] {
  return unique(playable(state, mySeat).map(m => m.from));
}

/** The destinations of the selected piece (each once), as far as the viewer may move. */
export function targets(state: DraughtsState, selected: number | null, mySeat?: number): number[] {
  if (selected === null) return [];
  const moves = mySeat === undefined ? state.legalMoves : playable(state, mySeat);
  return unique(moves.filter(m => m.from === selected).map(m => m.to));
}

/** The legal moves of a piece that end on a square: one, or several when different captured sets end there. */
export function candidatesFor(state: DraughtsState, from: number, to: number): DraughtsLegalMove[] {
  return state.legalMoves.filter(m => m.from === from && m.to === to);
}

/**
 * The pieces that tell candidates apart: each piece taken by exactly one candidate, mapped to that candidate's
 * index. A piece taken by all of them, or by several but not all, cannot separate them and is left out.
 */
export function separatingSquares(candidates: readonly DraughtsLegalMove[]): Map<number, number> {
  const owners = new Map<number, number[]>();
  candidates.forEach((candidate, index) => {
    for (const square of candidate.captures) owners.set(square, [...(owners.get(square) ?? []), index]);
  });
  const separating = new Map<number, number>();
  for (const [square, indexes] of owners) {
    if (indexes.length === 1) separating.set(square, indexes[0]);
  }
  return separating;
}

export type SquareIntent =
  | { kind: 'select'; square: number }
  | { kind: 'deselect' }
  | { kind: 'play'; from: number; to: number; captures: number[] }
  | { kind: 'choose'; from: number; to: number; candidates: DraughtsLegalMove[] }
  | { kind: 'none' };

const playOf = (move: DraughtsLegalMove): SquareIntent =>
  ({ kind: 'play', from: move.from, to: move.to, captures: [...move.captures] });

/** What a tap (or Enter) on a square means; nothing is applied locally, the server answers with the state. */
export function onSquare(state: DraughtsState, mySeat: number, selected: number | null, square: number): SquareIntent {
  const pieces = selectable(state, mySeat);
  if (pieces.length === 0) return { kind: 'none' };
  if (selected !== null && targets(state, selected, mySeat).includes(square)) {
    const candidates = candidatesFor(state, selected, square);
    return candidates.length === 1
      ? playOf(candidates[0])
      : { kind: 'choose', from: selected, to: square, candidates };
  }
  if (selected === square) return { kind: 'deselect' };
  if (pieces.includes(square)) return { kind: 'select', square };
  return selected !== null ? { kind: 'deselect' } : { kind: 'none' };
}

/** A tap on a piece while the candidates of an ambiguous destination are shown: it picks the one that takes it. */
export function onCandidatePiece(candidates: readonly DraughtsLegalMove[], square: number): SquareIntent {
  const index = separatingSquares(candidates).get(square);
  return index === undefined ? { kind: 'none' } : playOf(candidates[index]);
}

export type FocusKey = 'ArrowUp' | 'ArrowDown' | 'ArrowLeft' | 'ArrowRight';

/**
 * Arrow-key focus on the 10 x 5 grid of dark squares of the visible board: left and right go to the next dark
 * square of the row, up and down to the same place in the row above or below (a diagonal step on the board).
 * The focus stays put at an edge.
 */
export function moveFocus(square: number, key: FocusKey, orientation: DraughtsOrientation): number {
  const index = visibleIndex(square, orientation);
  const row = Math.floor(index / PER_ROW);
  const slot = index % PER_ROW;
  let next = index;
  if (key === 'ArrowLeft' && slot > 0) next = index - 1;
  else if (key === 'ArrowRight' && slot < PER_ROW - 1) next = index + 1;
  else if (key === 'ArrowUp' && row > 0) next = index - PER_ROW;
  else if (key === 'ArrowDown' && row < 9) next = index + PER_ROW;
  return squareOfVisibleIndex(next, orientation);
}
