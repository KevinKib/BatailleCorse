import type { MorrisState } from './MorrisState';

/** A Three Men's Morris state for tests; the defaults describe the start of a game, White to place. */
export function morrisState(overrides: Partial<MorrisState> = {}): MorrisState {
  return {
    started: true,
    id: 'g1',
    gameType: 'three-men-morris',
    version: 0,
    phase: 'PLACEMENT',
    currentSeat: 0,
    board: [null, null, null, null, null, null, null, null, null],
    players: [
      { seat: 0, name: 'Alice', current: true, piecesToPlace: 3 },
      { seat: 1, name: 'Bob', current: false, piecesToPlace: 3 },
    ],
    legalMoves: [],
    availableActions: ['PLACE'],
    winningLine: [],
    outcome: { status: 'ONGOING' },
    ...overrides,
  };
}
