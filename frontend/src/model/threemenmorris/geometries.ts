import type { MorrisState, MorrisVariantKey } from './MorrisState';

/**
 * How each variant's board is drawn: where its points sit, which segments join them,
 * how many pieces each player holds. This is display only. The server alone knows the rules (adjacency, legal
 * moves, placements), and the client never derives a move from these segments.
 */

export interface MorrisPointPosition {
  /** Position in a 0-100 box, x to the right and y down. */
  x: number;
  y: number;
}

export interface MorrisGeometry {
  key: MorrisVariantKey;
  /**
   * The points as the server numbers them: the nine of the grid row by row (0 1 2 / 3 4 5 / 6 7 8, the centre is 4).
   */
  points: readonly MorrisPointPosition[];
  /**
   * What is drawn: the three points of each line.
   */
  segments: readonly (readonly number[])[];
  /** Pieces each player holds. */
  pieces: number;
  /** The refined table: a plaque under the lines, lit paths, and a mark on the pieces besides colour. */
  refined: boolean;
}

export const VARIANT_KEYS: readonly MorrisVariantKey[] = ['tapatan', 'achi'];

const coordinate = (n: number): number => Math.round(((n * 2 + 1) * 100 / 6) * 100) / 100;
const GRID: readonly MorrisPointPosition[] = Array.from({ length: 9 }, (_, i) => ({
  x: coordinate(i % 3),
  y: coordinate(Math.floor(i / 3)),
}));

/** The eight lines of the board: three rows, three columns and the two diagonals through the centre. */
const LINES: readonly (readonly number[])[] = [
  [0, 1, 2], [3, 4, 5], [6, 7, 8],
  [0, 3, 6], [1, 4, 7], [2, 5, 8],
  [0, 4, 8], [2, 4, 6],
];

export const GEOMETRIES: Record<MorrisVariantKey, MorrisGeometry> = {
  tapatan: { key: 'tapatan', points: GRID, segments: LINES, pieces: 3, refined: false },
  achi: { key: 'achi', points: GRID, segments: LINES, pieces: 4, refined: false },
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

/**
 * The point Page Down or Page Up leads to: the next or the previous one in the server's numbering, or null at an end
 * or for another key.
 */
export function neighbourByPage(geometry: MorrisGeometry, point: number, key: string): number | null {
  const next = key === 'PageDown' ? point + 1 : key === 'PageUp' ? point - 1 : null;
  return next !== null && next >= 0 && next < geometry.points.length ? next : null;
}

/** Whether a point sits on the straight segment from `a` to `b` (ends included). */
function onSpan(p: MorrisPointPosition, a: MorrisPointPosition, b: MorrisPointPosition): boolean {
  const length = Math.hypot(b.x - a.x, b.y - a.y);
  if (length === 0) return Math.hypot(p.x - a.x, p.y - a.y) < 0.5;
  const cross = Math.abs((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)) / length;
  const along = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / length;
  return cross < 0.5 && along > -0.5 && along < length + 0.5;
}

/**
 * The drawn segments that lie on a winning line, whatever the order the server lists its points in: the segment of a
 * three-point line on Tapatan and Achi.
 */
export function segmentsOnLine(geometry: MorrisGeometry, line: readonly number[]): (readonly number[])[] {
  if (line.length < 2) return [];
  const positions = line.map((n) => geometry.points[n]);
  // The ends of the line are its two points furthest apart.
  let ends: [MorrisPointPosition, MorrisPointPosition] = [positions[0], positions[0]];
  let widest = -1;
  positions.forEach((a) => positions.forEach((b) => {
    const d = Math.hypot(a.x - b.x, a.y - b.y);
    if (d > widest) { widest = d; ends = [a, b]; }
  }));
  return geometry.segments.filter((segment) =>
    segment.every((n) => onSpan(geometry.points[n], ends[0], ends[1])));
}
