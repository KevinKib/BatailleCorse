import type { DraughtsTempo } from './tempo';

/** What a plan needs of a move: where it starts, the landing squares and the captured pieces in order. */
export interface MovePath {
  from: number;
  path: readonly number[];
  captures: readonly number[];
}

export interface PlannedGlide {
  from: number;
  to: number;
  atMs: number;
  durationMs: number;
}

export interface PlannedRemoval {
  square: number;
  atMs: number;
  durationMs: number;
}

export interface MovePlan {
  glides: PlannedGlide[];
  removals: PlannedRemoval[];
  totalMs: number;
}

/**
 * Times a move for the board: the piece glides along `path`, one leg after the other, then the captured pieces
 * disappear in capture order (FMJD 4.11: they leave the board once the move is over). Pure: the component plays it.
 */
export function planMove(move: MovePath, tempo: DraughtsTempo): MovePlan {
  let at = 0;
  let previous = move.from;
  const glides = move.path.map((to): PlannedGlide => {
    const glide = { from: previous, to, atMs: at, durationMs: tempo.legMs };
    previous = to;
    at += tempo.legMs;
    return glide;
  });
  const removals = move.captures.map((square): PlannedRemoval => {
    const removal = { square, atMs: at, durationMs: tempo.removalMs };
    at += tempo.removalMs;
    return removal;
  });
  return { glides, removals, totalMs: at };
}
