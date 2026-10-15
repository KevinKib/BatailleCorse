import type { MangolaState } from './MangolaState';

/** The classic checkerboard board: 4 seeds where (column + row number) is even, 32 per side. */
export function checkerboardBoard(): number[] {
  return Array.from({ length: 32 }, (_, pit) => ((pit % 8) + Math.floor(pit / 8) + 1) % 2 === 0 ? 4 : 0);
}

/**
 * A Mangola state for tests, seen by seat 0 (side A): the defaults describe the first turn of a played game on the
 * checkerboard, seat 0 to move.
 */
export function mangolaState(overrides: Partial<MangolaState> = {}): MangolaState {
  return {
    started: true,
    id: 'g1',
    gameType: 'mangola',
    version: 0,
    phase: 'PLAY',
    currentSeat: 0,
    seatA: 0,
    board: checkerboardBoard(),
    players: [
      { seat: 0, name: 'Alice', current: true, side: 'A', seeds: 32, ready: true },
      { seat: 1, name: 'Bob', current: false, side: 'B', seeds: 32, ready: true },
    ],
    legalPits: [8, 10, 12, 14, 1, 3, 5, 7],
    availableActions: ['SOW'],
    outcome: { status: 'ONGOING' },
    draftOnlyEndless: false,
    ...overrides,
  };
}

/** A hidden placement seen by seat 0: its draft in the loop order of side A on the board, the other half null. */
export function placementState(draft: number[] = Array(16).fill(0), overrides: Partial<MangolaState> = {}): MangolaState {
  const board: (number | null)[] = Array(32).fill(null);
  // Side A loop: inner row (pits 8..15) left to right, then the outer row (pits 7..0) right to left.
  const loop = [8, 9, 10, 11, 12, 13, 14, 15, 7, 6, 5, 4, 3, 2, 1, 0];
  loop.forEach((pit, i) => { board[pit] = draft[i]; });
  const total = draft.reduce((a, b) => a + b, 0);
  return mangolaState({
    phase: 'PLACEMENT',
    board,
    players: [
      { seat: 0, name: 'Alice', current: false, side: 'A', seeds: total, ready: false },
      { seat: 1, name: 'Bob', current: false, side: 'B', seeds: null, ready: false },
    ],
    legalPits: [],
    availableActions: total === 32 ? ['ARRANGE', 'CONFIRM_LAYOUT'] : ['ARRANGE'],
    ...overrides,
  });
}
