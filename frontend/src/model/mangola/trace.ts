import type { MangolaLastMove, MangolaStep } from './MangolaState';

/** How a speed times a sowing. Normal is exactly twice as slow as Fast (bounds, pauses, budget), capped at 7 s. */
export interface SpeedProfile {
  /** Time shared among the seeds of a sowing. */
  tickBudgetMs: number;
  /** Bounds of one seed's tick. */
  tickMinMs: number;
  tickMaxMs: number;
  /** A step that is not a drop pauses for 1.5 ticks, never under this. */
  minPauseMs: number;
  /** The whole animation never lasts longer than this: every step is scaled down to fit. */
  maxTotalMs: number;
  /** The hand slides from a pit to the next in this time. */
  handSlideMs: number;
  /** An opponent's move shows its chosen pit, the hand resting on it, this long before the seeds leave. */
  leadInMs: number;
}

export type TraceSpeed = 'normal' | 'fast' | 'instant';

export const SPEED_PROFILES: Record<TraceSpeed, SpeedProfile> = {
  normal: { tickBudgetMs: 4800, tickMinMs: 120, tickMaxMs: 560, minPauseMs: 400, maxTotalMs: 7000, handSlideMs: 180, leadInMs: 500 },
  fast: { tickBudgetMs: 2400, tickMinMs: 60, tickMaxMs: 280, minPauseMs: 200, maxTotalMs: 4500, handSlideMs: 90, leadInMs: 250 },
  instant: { tickBudgetMs: 0, tickMinMs: 0, tickMaxMs: 0, minPauseMs: 0, maxTotalMs: 0, handSlideMs: 0, leadInMs: 0 },
};

/** The rhythm of Fast, which was the only one before the speed could be chosen. */
export const TOTAL_TICK_BUDGET_MS = SPEED_PROFILES.fast.tickBudgetMs;
export const TICK_MIN_MS = SPEED_PROFILES.fast.tickMinMs;
export const TICK_MAX_MS = SPEED_PROFILES.fast.tickMaxMs;
export const PAUSE_TICKS = 1.5;
export const MIN_PAUSE_MS = SPEED_PROFILES.fast.minPauseMs;
export const MAX_TOTAL_MS = SPEED_PROFILES.fast.maxTotalMs;

export const dropCount = (trace: readonly MangolaStep[]): number => trace.filter(s => s.type === 'DROP').length;

/** The seeds taken by the captures of a move. */
export function capturedCount(trace: readonly MangolaStep[]): number {
  return trace.reduce((sum, s) => (s.type === 'CAPTURE' ? sum + (s.count ?? 0) : sum), 0);
}

/** The time of one dropped seed at the start of a sowing of `drops` seeds. */
export function tickMs(drops: number, speed: TraceSpeed = 'fast'): number {
  const { tickBudgetMs, tickMinMs, tickMaxMs } = SPEED_PROFILES[speed];
  if (drops <= 0) return tickMaxMs;
  return Math.min(tickMaxMs, Math.max(tickMinMs, tickBudgetMs / drops));
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
 * last drop; every other step is a pause of 1.5 ticks (at least `minPauseMs`). A plan longer than the speed's cap
 * (4.5 s in Fast, 7 s in Normal) is scaled down; in Instant every step takes no time.
 */
export function planSowing(trace: readonly MangolaStep[], speed: TraceSpeed = 'fast'): SowingPlan {
  const { minPauseMs, maxTotalMs } = SPEED_PROFILES[speed];
  const drops = dropCount(trace);
  const base = tickMs(drops, speed);
  const tickAt = (k: number) => (drops > 1 ? base * (1 - 0.5 * Math.min(k, drops - 1) / (drops - 1)) : base);

  let seen = 0;
  const durations = trace.map((step) => {
    if (step.type === 'DROP') return tickAt(seen++);
    return Math.max(minPauseMs, PAUSE_TICKS * tickAt(seen));
  });
  const raw = durations.reduce((a, b) => a + b, 0);
  const scale = raw > maxTotalMs ? maxTotalMs / raw : 1;

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
