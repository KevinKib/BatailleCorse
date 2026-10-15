import type { MangolaSide, MangolaState } from './MangolaState';
import { COLUMNS, ROWS, nextPit, pitName, sideOfPit } from './geometry';

/** Up to this many seeds a pit shows them as dots; above, the written number. */
export const MAX_DOTS = 8;

export type Arrow = 'left' | 'right' | 'up' | 'down';

export interface MangolaCell {
  pit: number;
  name: string;
  /** Seeds in the pit; null while the pit is hidden from the viewer. */
  count: number | null;
  /** The pit is on the viewer's side. */
  mine: boolean;
  /** Rows 2 and 3 of the board, the ones that face each other. */
  inner: boolean;
  /** The viewer may sow it now. */
  legal: boolean;
  /** The direction a seed leaves this pit on screen. */
  arrow: Arrow;
}

/** The side of a seat: `seatA` owns side A (rows 1 and 2). */
export function sideOfSeat(state: Pick<MangolaState, 'seatA'>, seat: number): MangolaSide {
  return seat === state.seatA ? 'A' : 'B';
}

/** The board row (0 to 3) and column of a pit. */
const rowOf = (pit: number) => Math.floor(pit / COLUMNS);
const columnOf = (pit: number) => pit % COLUMNS;

/**
 * Where a pit is on the viewer's screen, as [displayRow, displayColumn] with the viewer's side at the bottom.
 * Side A is seen as it is written (row 4 on top, row 1 at the bottom, columns a to h); side B is seen turned by
 * half a turn (row 1 on top, columns h to a).
 */
function screenPosition(pit: number, viewer: MangolaSide): [number, number] {
  return viewer === 'A'
    ? [ROWS - 1 - rowOf(pit), columnOf(pit)]
    : [rowOf(pit), COLUMNS - 1 - columnOf(pit)];
}

/** The direction a seed travels on screen when it leaves `pit`, for a viewer sitting on side `viewer`. */
export function arrowOf(pit: number, viewer: MangolaSide): Arrow {
  const [r0, c0] = screenPosition(pit, viewer);
  const [r1, c1] = screenPosition(nextPit(pit), viewer);
  if (r1 > r0) return 'down';
  if (r1 < r0) return 'up';
  return c1 > c0 ? 'right' : 'left';
}

/** The 4 x 8 grid as the viewer sees it, top to bottom, with the viewer's half at the bottom. */
export function viewerRows(state: MangolaState, seat: number): MangolaCell[][] {
  const viewer = sideOfSeat(state, seat);
  const legal = new Set(state.legalPits);
  const rows: MangolaCell[][] = Array.from({ length: ROWS }, () => Array<MangolaCell>(COLUMNS));
  for (let pit = 0; pit < state.board.length; pit++) {
    const [r, c] = screenPosition(pit, viewer);
    const boardRow = rowOf(pit);
    rows[r][c] = {
      pit,
      name: pitName(pit),
      count: state.board[pit],
      mine: sideOfPit(pit) === viewer,
      inner: boardRow === 1 || boardRow === 2,
      legal: legal.has(pit),
      arrow: arrowOf(pit, viewer),
    };
  }
  return rows;
}

export type SeedDisplay =
  | { kind: 'dots'; count: number }
  | { kind: 'number'; count: number }
  | { kind: 'hidden' };

/** How a pit shows its seeds: exact dots up to eight, the number above, nothing when it is hidden. */
export function seedDisplay(count: number | null): SeedDisplay {
  if (count === null) return { kind: 'hidden' };
  return count <= MAX_DOTS ? { kind: 'dots', count } : { kind: 'number', count };
}

/** The same grid showing the counts of `board` (a step of the sowing animation); the rows themselves without one. */
export function rowsWithBoard(rows: MangolaCell[][], board: readonly (number | null)[] | null): MangolaCell[][] {
  if (board === null) return rows;
  return rows.map(row => row.map(cell => ({ ...cell, count: board[cell.pit] })));
}
