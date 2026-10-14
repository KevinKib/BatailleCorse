import type { MorrisState, MorrisVariantKey } from './MorrisState';

/**
 * How each variant's board is drawn: where its nine points sit, which segments join them, how many pieces each
 * player holds. This is display only. The server alone knows the rules (adjacency, legal moves, placements), and
 * the client never derives a move from these segments.
 */

export interface MorrisPointPosition {
  /** Position in a 0-100 box, x to the right and y down. */
  x: number;
  y: number;
}

export interface MorrisGeometry {
  key: MorrisVariantKey;
  /** The nine points, row by row (0 1 2 / 3 4 5 / 6 7 8, the centre is 4), as the server numbers them. */
  points: readonly MorrisPointPosition[];
  /** What is drawn: the three points of each winning line, then Picaria's two-point short diagonals. */
  segments: readonly (readonly number[])[];
  /** Pieces each player holds. */
  pieces: number;
}

export const VARIANT_KEYS: readonly MorrisVariantKey[] = ['tapatan', 'achi', 'picaria'];

const coordinate = (n: number): number => Math.round(((n * 2 + 1) * 100 / 6) * 100) / 100;
const POINTS: readonly MorrisPointPosition[] = Array.from({ length: 9 }, (_, i) => ({
  x: coordinate(i % 3),
  y: coordinate(Math.floor(i / 3)),
}));

/** The eight lines of the board: three rows, three columns and the two diagonals through the centre. */
const LINES: readonly (readonly number[])[] = [
  [0, 1, 2], [3, 4, 5], [6, 7, 8],
  [0, 3, 6], [1, 4, 7], [2, 5, 8],
  [0, 4, 8], [2, 4, 6],
];

/** Picaria's four short diagonals, joining the middles of neighbouring sides: they never make a line. */
const SHORT_DIAGONALS: readonly (readonly number[])[] = [[1, 3], [3, 7], [7, 5], [5, 1]];

export const GEOMETRIES: Record<MorrisVariantKey, MorrisGeometry> = {
  tapatan: { key: 'tapatan', points: POINTS, segments: LINES, pieces: 3 },
  achi: { key: 'achi', points: POINTS, segments: LINES, pieces: 4 },
  picaria: { key: 'picaria', points: POINTS, segments: [...LINES, ...SHORT_DIAGONALS], pieces: 3 },
};

/** The variant of a state; an absent or unknown one is Tapatan, which the server leaves out. */
export function variantKeyOf(state: Pick<MorrisState, 'variant'> | null | undefined): MorrisVariantKey {
  const key = state?.variant;
  return key !== undefined && VARIANT_KEYS.includes(key) ? key : 'tapatan';
}

export function geometryOf(state: Pick<MorrisState, 'variant'> | null | undefined): MorrisGeometry {
  return GEOMETRIES[variantKeyOf(state)];
}

const ARROWS: Record<string, MorrisPointPosition> = {
  ArrowUp: { x: 0, y: -1 },
  ArrowDown: { x: 0, y: 1 },
  ArrowLeft: { x: -1, y: 0 },
  ArrowRight: { x: 1, y: 0 },
};

/**
 * The point an arrow key leads to: the nearest one in that direction, within a quarter-turn cone (no wrap),
 * or null at an edge or for another key.
 */
export function neighbourByArrow(geometry: MorrisGeometry, point: number, key: string): number | null {
  const step = ARROWS[key];
  if (!step) return null;
  const from = geometry.points[point];
  let best: number | null = null;
  let bestScore = Infinity;
  geometry.points.forEach((to, candidate) => {
    if (candidate === point) return;
    const along = (to.x - from.x) * step.x + (to.y - from.y) * step.y;
    const across = Math.abs((to.x - from.x) * step.y - (to.y - from.y) * step.x);
    if (along <= 0.5 || across > along + 0.5) return;
    const score = along + 2 * across;
    if (score < bestScore) { bestScore = score; best = candidate; }
  });
  return best;
}
