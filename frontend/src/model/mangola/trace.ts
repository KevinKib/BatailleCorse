import type { MangolaLastMove, MangolaStep } from './MangolaState';

/** Time shared among the seeds of a sowing, and the bounds of one seed's tick. */
export const TOTAL_TICK_BUDGET_MS = 2400;
export const TICK_MIN_MS = 60;
export const TICK_MAX_MS = 280;
/** A step that is not a drop pauses for 1.5 ticks, never under this. */
export const PAUSE_TICKS = 1.5;
export const MIN_PAUSE_MS = 200;
/** The whole animation never lasts longer than this: every step is scaled down to fit. */
export const MAX_TOTAL_MS = 4500;

export const dropCount = (trace: readonly MangolaStep[]): number => trace.filter(s => s.type === 'DROP').length;

/** The seeds taken by the captures of a move. */
export function capturedCount(trace: readonly MangolaStep[]): number {
  return trace.reduce((sum, s) => (s.type === 'CAPTURE' ? sum + (s.count ?? 0) : sum), 0);
}

/** The time of one dropped seed at the start of a sowing of `drops` seeds. */
export function tickMs(drops: number): number {
  if (drops <= 0) return TICK_MAX_MS;
  return Math.min(TICK_MAX_MS, Math.max(TICK_MIN_MS, TOTAL_TICK_BUDGET_MS / drops));
}

export interface PlannedStep {
  step: MangolaStep;
  /** Start time from the beginning of the animation. */
  atMs: number;
  durationMs: number;
}

export interface SowingPlan {
  steps: PlannedStep[];
  totalMs: number;
}

/**
 * Times a trace for the travelling hand: a drop lasts one tick, the tick halving linearly from the first to the
 * last drop; every other step is a pause of 1.5 ticks (at least 200 ms). A plan longer than 4.5 s is scaled down.
 */
export function planSowing(trace: readonly MangolaStep[]): SowingPlan {
  const drops = dropCount(trace);
  const base = tickMs(drops);
  const tickAt = (k: number) => (drops > 1 ? base * (1 - 0.5 * Math.min(k, drops - 1) / (drops - 1)) : base);

  let seen = 0;
  const durations = trace.map((step) => {
    if (step.type === 'DROP') return tickAt(seen++);
    return Math.max(MIN_PAUSE_MS, PAUSE_TICKS * tickAt(seen));
  });
  const raw = durations.reduce((a, b) => a + b, 0);
  const scale = raw > MAX_TOTAL_MS ? MAX_TOTAL_MS / raw : 1;

  let at = 0;
  const steps = trace.map((step, i): PlannedStep => {
    const durationMs = durations[i] * scale;
    const planned = { step, atMs: at, durationMs };
    at += durationMs;
    return planned;
  });
  return { steps, totalMs: at };
}

export interface LastMoveMarks {
  /** The pit that was played, null without a last move. */
  start: number | null;
  /** The pits emptied by captures, in trace order, each once. */
  captured: number[];
}

/** The pit marks that show a move without replaying it: the pit played and the pits captured. */
export function lastMoveMarks(lastMove: MangolaLastMove | undefined): LastMoveMarks {
  if (!lastMove) return { start: null, captured: [] };
  const captured: number[] = [];
  for (const step of lastMove.trace) {
    if (step.type !== 'CAPTURE') continue;
    for (const pit of [step.facing, step.behind]) {
      if (pit !== undefined && !captured.includes(pit)) captured.push(pit);
    }
  }
  return { start: lastMove.pit, captured };
}
