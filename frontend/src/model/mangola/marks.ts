import type { MangolaLastMove, MangolaStep } from './MangolaState';
import { lastMoveMarks } from './trace';

/** A pit emptied by a capture; `seeds` is what it held, null when the board before the move is not known. */
export interface CapturedMark {
  pit: number;
  seeds: number | null;
}

/** What the board draws to show a move without replaying it: a ring on the start pit, on the end pit, and on each captured pit. */
export interface BoardMarks {
  start: number | null;
  end: number | null;
  captured: CapturedMark[];
}

export const NO_BOARD_MARKS: BoardMarks = { start: null, end: null, captured: [] };

/** The pit that received the last seed of a move, null when the trace drops nothing. */
export function endPit(trace: readonly MangolaStep[]): number | null {
  for (let i = trace.length - 1; i >= 0; i--) {
    const step = trace[i];
    if (step.type === 'DROP' && step.pit !== undefined) return step.pit;
  }
  return null;
}

/** The marks of a last move; `previousBoard` (the board before the move) gives the seeds taken from each captured pit. */
export function boardMarks(lastMove: MangolaLastMove | undefined, previousBoard?: readonly (number | null)[]): BoardMarks {
  if (!lastMove) return NO_BOARD_MARKS;
  const { start, captured } = lastMoveMarks(lastMove);
  return {
    start,
    end: endPit(lastMove.trace),
    captured: captured.map(pit => ({ pit, seeds: previousBoard?.[pit] ?? null })),
  };
}
