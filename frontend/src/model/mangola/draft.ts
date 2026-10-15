import type { MangolaSide, MangolaState } from './MangolaState';
import { LOOP_LENGTH, loopOf } from './geometry';
import { sideOfSeat } from './board';

/** The seeds each player places on their side. */
export const DRAFT_SEEDS = 32;

/** The layout: 16 counts, in the order of the loop of the player's side (the `arrange` payload). */
export type Draft = number[];

const sum = (draft: readonly number[]) => draft.reduce((a, b) => a + b, 0);

export const seedsLeft = (draft: readonly number[]): number => DRAFT_SEEDS - sum(draft);

/** One more seed in a pit; unchanged when all 32 are placed. */
export function addSeed(draft: readonly number[], index: number): Draft {
  if (seedsLeft(draft) <= 0) return [...draft];
  return draft.map((n, i) => (i === index ? n + 1 : n));
}

/** One seed less in a pit, never below zero. */
export function removeSeed(draft: readonly number[], index: number): Draft {
  return draft.map((n, i) => (i === index ? Math.max(0, n - 1) : n));
}

export const clearLayout = (): Draft => Array(LOOP_LENGTH).fill(0);

/** The Mangola start: two seeds in every pit. */
export const twoPerPit = (): Draft => Array(LOOP_LENGTH).fill(2);

/** The classic opening: 4 seeds where the column index (a = 0) plus the row number (1 to 4) is even. */
export function checkerboardLayout(side: MangolaSide): Draft {
  return loopOf(side).map(pit => (((pit % 8) + Math.floor(pit / 8) + 1) % 2 === 0 ? 4 : 0));
}

/** 32 seeds dropped one by one on a pit drawn uniformly among the 16 (the last one included). */
export function randomLayout(random: () => number = Math.random): Draft {
  const draft = clearLayout();
  for (let seed = 0; seed < DRAFT_SEEDS; seed++) {
    draft[Math.min(LOOP_LENGTH - 1, Math.floor(random() * LOOP_LENGTH))]++;
  }
  return draft;
}

/**
 * The layout the server holds for a seat, read from its state: my 16 pits in loop order, zeros when the pits are
 * unknown or the game is no longer in the placement phase.
 */
export function draftOf(state: MangolaState, seat: number): Draft {
  if (state.phase !== 'PLACEMENT') return clearLayout();
  return loopOf(sideOfSeat(state, seat)).map(pit => state.board[pit] ?? 0);
}
