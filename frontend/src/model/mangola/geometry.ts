import type { MangolaSide } from './MangolaState';

export const COLUMNS = 8;
export const ROWS = 4;
export const PITS = COLUMNS * ROWS;
export const LOOP_LENGTH = 16;

/** The name of a pit, `a1..h4`: the column letter from `a`, the row number from 1. */
export function pitName(pit: number): string {
  return `${String.fromCharCode(97 + (pit % COLUMNS))}${Math.floor(pit / COLUMNS) + 1}`;
}

/** The pit of a name `a1..h4`, null when it is not one. */
export function pitFromName(name: string): number | null {
  if (!/^[a-h][1-4]$/.test(name)) return null;
  return (name.charCodeAt(1) - 49) * COLUMNS + (name.charCodeAt(0) - 97);
}

/** Rows 1 and 2 belong to side A, rows 3 and 4 to side B. */
export function sideOfPit(pit: number): MangolaSide {
  return pit < PITS / 2 ? 'A' : 'B';
}

const row = (r: number, ascending: boolean): number[] =>
  Array.from({ length: COLUMNS }, (_, i) => r * COLUMNS + (ascending ? i : COLUMNS - 1 - i));

const LOOPS: Record<MangolaSide, number[]> = {
  // Inner row left to right, then the outer row right to left.
  A: [...row(1, true), ...row(0, false)],
  // The same path turned by half a turn: row 4 left to right, then row 3 right to left.
  B: [...row(3, true), ...row(2, false)],
};

/** The 16 pits of a side in sowing order, which is also the order of a layout (`arrange`). */
export function loopOf(side: MangolaSide): number[] {
  return [...LOOPS[side]];
}

/** The pit after `pit` along the loop of its owner. */
export function nextPit(pit: number): number {
  const loop = LOOPS[sideOfPit(pit)];
  return loop[(loop.indexOf(pit) + 1) % loop.length];
}
