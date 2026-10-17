import type { MangolaStep } from './MangolaState';
import { planSowing, type TraceSpeed } from './trace';

/** The seeds the travelling hand holds, and the pit it is over. */
export interface HandPosition {
  pit: number;
  seeds: number;
}

/** What the board shows during a sowing, from the moment `atMs` until the next frame. */
export interface SowingFrame {
  step: MangolaStep;
  atMs: number;
  durationMs: number;
  /** The 32 counts after this step. */
  board: number[];
  hand: HandPosition;
  /** The pits a capture just emptied (this step only). */
  captured: number[];
}

export interface SowingFrames {
  frames: SowingFrame[];
  totalMs: number;
}

const isFull = (board: readonly (number | null)[]): board is readonly number[] => board.every(c => c !== null);

/**
 * Plays one step on `board` (modified in place) and returns the hand after it; null when the step cannot happen
 * on this board. `hand` is what the hand holds before the step.
 *
 * Mirrors the backend's `SowingStep`: PICK and RELAY take `count` seeds out of a pit (and the hand holds them), DROP
 * adds one, CAPTURE empties the facing and the behind pit into the hand, SKIP_EMPTY and RESUME_AFTER change nothing.
 */
function applyStep(board: number[], hand: HandPosition | null, step: MangolaStep): HandPosition | null {
  const pit = step.pit;
  if (pit === undefined || pit < 0 || pit >= board.length) return null;
  const held = hand?.seeds ?? 0;
  switch (step.type) {
    case 'PICK':
    case 'RELAY': {
      const count = step.count ?? 0;
      if (board[pit] < count) return null;
      board[pit] -= count;
      return { pit, seeds: count };
    }
    case 'DROP':
      if (held < 1) return null;
      board[pit] += 1;
      return { pit, seeds: held - 1 };
    case 'CAPTURE': {
      const { facing, behind } = step;
      if (facing === undefined || behind === undefined) return null;
      const taken = board[facing] + board[behind];
      if (taken !== (step.count ?? taken)) return null;
      board[facing] = 0;
      board[behind] = 0;
      return { pit, seeds: held + taken };
    }
    case 'SKIP_EMPTY':
    case 'RESUME_AFTER':
      return { pit, seeds: held };
    default:
      return null;
  }
}

/** The board a trace leaves from `previous`; null when `previous` has a hidden pit or the trace does not fit it. */
export function replayTrace(previous: readonly (number | null)[], trace: readonly MangolaStep[]): number[] | null {
  if (!isFull(previous)) return null;
  const board = [...previous];
  let hand: HandPosition | null = null;
  for (const step of trace) {
    hand = applyStep(board, hand, step);
    if (hand === null) return null;
  }
  return board;
}

/**
 * The frames of a sowing: one per step, timed by `planSowing`, each with the board, the hand and the pits just
 * captured. The last frame's board is exactly `replayTrace(previous, trace)`. Null when the trace does not fit
 * `previous` (the caller then shows the final board without a sowing).
 */
export function sowingFrames(
  previous: readonly (number | null)[],
  trace: readonly MangolaStep[],
  speed: TraceSpeed = 'fast',
): SowingFrames | null {
  if (!isFull(previous)) return null;
  const plan = planSowing(trace, speed);
  const board = [...previous];
  let hand: HandPosition | null = null;
  const frames: SowingFrame[] = [];
  for (const planned of plan.steps) {
    hand = applyStep(board, hand, planned.step);
    if (hand === null) return null;
    const { step } = planned;
    frames.push({
      step,
      atMs: planned.atMs,
      durationMs: planned.durationMs,
      board: [...board],
      hand,
      captured: step.type === 'CAPTURE' ? [step.facing!, step.behind!] : [],
    });
  }
  return { frames, totalMs: plan.totalMs };
}
