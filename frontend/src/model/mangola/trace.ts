import type { MangolaLastMove, MangolaStep } from './MangolaState';

/** How a speed times a sowing: one constant tick per seed, whatever the length of the move. */
export interface SpeedProfile {
  /** The time of one dropped seed, from the first seed of a move to the last. */
  tickMs: number;
  /** The hand slides from a pit to the next in this time: half a tick, so it always rests on a pit. */
  handSlideMs: number;
  /** An opponent's move shows its chosen pit, the hand resting on it, this long before the seeds leave. */
  leadInMs: number;
}

export type TraceSpeed = 'normal' | 'fast' | 'instant';

/** Owner decision: a constant time per seed. */
export const NORMAL_TICK_MS = 250;
export const FAST_TICK_MS = 125;

/** Owner decision (findings spec): the pause on an opponent's chosen pit, before the seeds leave. */
export const NORMAL_LEAD_IN_MS = 700;
export const FAST_LEAD_IN_MS = 350;

export const SPEED_PROFILES: Record<TraceSpeed, SpeedProfile> = {
  normal: { tickMs: NORMAL_TICK_MS, handSlideMs: NORMAL_TICK_MS / 2, leadInMs: NORMAL_LEAD_IN_MS },
  fast: { tickMs: FAST_TICK_MS, handSlideMs: FAST_TICK_MS / 2, leadInMs: FAST_LEAD_IN_MS },
  instant: { tickMs: 0, handSlideMs: 0, leadInMs: 0 },
};

/** A step that is not a drop pauses for this many ticks. */
export const PAUSE_TICKS = 1.5;

export const dropCount = (trace: readonly MangolaStep[]): number => trace.filter(s => s.type === 'DROP').length;

/** The seeds taken by the captures of a move. */
export function capturedCount(trace: readonly MangolaStep[]): number {
  return trace.reduce((sum, s) => (s.type === 'CAPTURE' ? sum + (s.count ?? 0) : sum), 0);
}

/** The time of one dropped seed: a constant of the speed. */
export function tickMs(speed: TraceSpeed = 'fast'): number {
  return SPEED_PROFILES[speed].tickMs;
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
 * Times a trace for the travelling hand: a drop lasts one tick, the same from the first seed to the last, and every
 * other step is a pause of 1.5 ticks. The move has no cap; in Instant every step takes no time.
 */
export function planSowing(trace: readonly MangolaStep[], speed: TraceSpeed = 'fast'): SowingPlan {
  const tick = tickMs(speed);
  let at = 0;
  const steps = trace.map((step): PlannedStep => {
    const durationMs = step.type === 'DROP' ? tick : PAUSE_TICKS * tick;
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
