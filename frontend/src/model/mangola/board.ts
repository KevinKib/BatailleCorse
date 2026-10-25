import type { MangolaDirection } from './direction';
import type { MangolaSide, MangolaState } from './MangolaState';
import { COLUMNS, ROWS, nextPit, pitName, sideOfPit } from './geometry';

/** Up to this many seeds a pit lays them out like dice; above, they form a pile. */
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

/**
 * The direction a seed travels on screen when it leaves `pit`, for a viewer sitting on side `viewer`. The game's
 * `direction` is accepted but not used yet: the screen mapping still draws clockwise.
 */
export function arrowOf(pit: number, viewer: MangolaSide, _direction: MangolaDirection = 'clockwise'): Arrow {
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

/** A seed in a bowl: its centre and its diameter, as shares of the bowl's width. */
export interface SeedPlace {
  x: number;
  y: number;
  size: number;
}

/** Diameter of a seed in the dice layout, as a share of the bowl. */
export const DOT_SIZE = 0.177;
/** Distance between two neighbouring places of the dice layout. */
const DICE_STEP = 0.227;
/** Radius of the area a pile may fill (the bowl is 0.5). */
const PILE_RADIUS = 0.4;
/** Angle between two successive seeds of the pile, in radians (the golden angle). */
const GOLDEN_ANGLE = 2.39996;

// Dice-like places on a 3 x 3 grid (0..8, row by row): the same count always draws the same, and 7 and 8 differ.
const DOT_PLACES: number[][] = [
  [], [4], [0, 8], [0, 4, 8], [0, 2, 6, 8], [0, 2, 4, 6, 8], [0, 2, 3, 5, 6, 8], [0, 2, 3, 4, 5, 6, 8], [0, 1, 2, 3, 5, 6, 7, 8],
];

/** The pile fills the bowl at 16, 32 and 64 seeds: the size depends only on which of these a count falls under. */
function pileCapacity(count: number): number {
  return count <= 16 ? 16 : count <= 32 ? 32 : 64;
}

/**
 * Where the seeds of a pit lie in its bowl: dice up to eight, then a tight spiral pile that grows from the centre.
 * A seed's place depends only on its index and on the size band, so a landing seed moves no other, except once when
 * the pile passes 16 and 32 (the seeds then shrink). Nothing for an empty or a hidden pit.
 */
export function seedLayout(count: number | null): SeedPlace[] {
  if (count === null || count <= 0) return [];
  if (count <= MAX_DOTS) {
    return DOT_PLACES[count].map(place => ({
      x: 0.5 + ((place % 3) - 1) * DICE_STEP,
      y: 0.5 + (Math.floor(place / 3) - 1) * DICE_STEP,
      size: DOT_SIZE,
    }));
  }
  const capacity = pileCapacity(count);
  const size = Math.min(DOT_SIZE, (1.6 * PILE_RADIUS) / Math.sqrt(capacity));
  return Array.from({ length: count }, (_, i) => {
    const radius = PILE_RADIUS * Math.sqrt((i + 0.5) / capacity);
    const angle = i * GOLDEN_ANGLE;
    return { x: 0.5 + radius * Math.cos(angle), y: 0.5 + radius * Math.sin(angle), size };
  });
}

/** Every non-empty pit writes its exact count in a badge; an empty or a hidden pit writes nothing. */
export function showsCount(count: number | null): boolean {
  return count !== null && count > 0;
}
/** The same grid showing the counts of `board` (a step of the sowing animation); the rows themselves without one. */
export function rowsWithBoard(rows: MangolaCell[][], board: readonly (number | null)[] | null): MangolaCell[][] {
  if (board === null) return rows;
  return rows.map(row => row.map(cell => ({ ...cell, count: board[cell.pit] })));
}
