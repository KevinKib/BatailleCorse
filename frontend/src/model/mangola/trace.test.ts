import { describe, it, expect } from 'vitest';
import {
  MAX_TOTAL_MS, MIN_PAUSE_MS, TICK_MAX_MS, TICK_MIN_MS,
  capturedCount, dropCount, lastMoveMarks, planSowing, tickMs,
} from './trace';
import type { MangolaStep } from './MangolaState';

const drops = (n: number, from = 8): MangolaStep[] =>
  Array.from({ length: n }, (_, i) => ({ type: 'DROP' as const, pit: from + i }));

describe('tickMs (base time of one dropped seed)', () => {
  it('is 2400 ms shared among the dropped seeds', () => {
    expect(tickMs(10)).toBe(240);
    expect(tickMs(12)).toBe(200);
  });

  it('is clamped between 60 and 280 ms', () => {
    expect(TICK_MIN_MS).toBe(60);
    expect(TICK_MAX_MS).toBe(280);
    expect(tickMs(2)).toBe(280);
    expect(tickMs(0)).toBe(280);
    expect(tickMs(200)).toBe(60);
  });
});

describe('planSowing', () => {
  it('plans nothing for an empty trace', () => {
    expect(planSowing([])).toEqual({ steps: [], totalMs: 0 });
  });

  it('keeps the steps in order with a start time and a duration each', () => {
    const trace: MangolaStep[] = [{ type: 'PICK', pit: 8, count: 3 }, ...drops(3, 9)];
    const plan = planSowing(trace);
    expect(plan.steps.map(s => s.step)).toEqual(trace);
    expect(plan.steps[0].atMs).toBe(0);
    for (let i = 1; i < plan.steps.length; i++) {
      expect(plan.steps[i].atMs).toBeCloseTo(plan.steps[i - 1].atMs + plan.steps[i - 1].durationMs, 6);
    }
    const last = plan.steps[plan.steps.length - 1];
    expect(plan.totalMs).toBeCloseTo(last.atMs + last.durationMs, 6);
  });

  it('a drop lasts the tick, halving linearly toward the last drop of the trace', () => {
    const plan = planSowing(drops(10));
    const durations = plan.steps.map(s => s.durationMs);
    expect(durations[0]).toBeCloseTo(240, 6);
    expect(durations[9]).toBeCloseTo(120, 6);
    for (let i = 1; i < durations.length; i++) expect(durations[i]).toBeLessThan(durations[i - 1]);
  });

  it('a lone drop lasts the full tick', () => {
    expect(planSowing(drops(1)).steps[0].durationMs).toBe(280);
  });

  it('every other step is a pause of 1.5 ticks, never under 200 ms', () => {
    expect(MIN_PAUSE_MS).toBe(200);
    // 10 drops: tick 240, so a pause before the first drop is 360 ms.
    const plan = planSowing([{ type: 'PICK', pit: 8, count: 10 }, ...drops(10, 9)]);
    expect(plan.steps[0].durationMs).toBeCloseTo(360, 6);
    // 120 drops: tick 60, 1.5 ticks is 90 ms, lifted to 200 ms (before the cap scales anything).
    const small = planSowing([{ type: 'RELAY', pit: 8, count: 3 }, ...drops(3, 9)]);
    expect(small.steps[0].durationMs).toBeGreaterThanOrEqual(MIN_PAUSE_MS);
  });

  it('lifts a short pause to 200 ms', () => {
    const plan = planSowing([{ type: 'PICK', pit: 8, count: 40 }, ...drops(40, 9)].slice(0, 41));
    // 40 drops: tick 60, 1.5 ticks = 90 ms, so the pause is 200 ms; the cap (4.5 s) is not reached.
    expect(plan.totalMs).toBeLessThanOrEqual(MAX_TOTAL_MS);
    expect(plan.steps[0].durationMs).toBe(200);
  });

  it('caps the whole animation at 4.5 s, scaling every step', () => {
    const trace: MangolaStep[] = [{ type: 'PICK', pit: 8, count: 600 }, ...drops(600, 9)];
    const plan = planSowing(trace);
    expect(MAX_TOTAL_MS).toBe(4500);
    expect(plan.totalMs).toBeCloseTo(4500, 6);
    const last = plan.steps[plan.steps.length - 1];
    expect(last.atMs + last.durationMs).toBeCloseTo(4500, 6);
  });

  it('does not stretch a short trace', () => {
    expect(planSowing(drops(4)).totalMs).toBeLessThan(MAX_TOTAL_MS);
  });
});

describe('trace summaries', () => {
  const trace: MangolaStep[] = [
    { type: 'PICK', pit: 12, count: 5 },
    ...drops(5, 13),
    { type: 'CAPTURE', pit: 12, count: 7, facing: 20, behind: 28 },
    { type: 'RESUME_AFTER', pit: 12 },
    { type: 'DROP', pit: 13 },
    { type: 'SKIP_EMPTY', pit: 14 },
    { type: 'CAPTURE', pit: 11, count: 3, facing: 21, behind: 29 },
  ];

  it('counts the seeds dropped', () => {
    expect(dropCount(trace)).toBe(6);
  });

  it('adds up the seeds captured', () => {
    expect(capturedCount(trace)).toBe(10);
    expect(capturedCount([])).toBe(0);
  });
});

describe('lastMoveMarks (the pit marks of the opponent last move)', () => {
  it('rings the pit played and marks the pits captured, in trace order', () => {
    const lastMove = {
      seat: 1,
      pit: 22,
      trace: [
        { type: 'PICK' as const, pit: 22, count: 3 },
        { type: 'DROP' as const, pit: 21 },
        { type: 'CAPTURE' as const, pit: 21, count: 6, facing: 13, behind: 5 },
      ],
    };
    expect(lastMoveMarks(lastMove)).toEqual({ start: 22, captured: [13, 5] });
  });

  it('has no captured pit for a move without capture', () => {
    const lastMove = { seat: 1, pit: 22, trace: [{ type: 'PICK' as const, pit: 22, count: 2 }] };
    expect(lastMoveMarks(lastMove)).toEqual({ start: 22, captured: [] });
  });

  it('is empty without a last move', () => {
    expect(lastMoveMarks(undefined)).toEqual({ start: null, captured: [] });
  });

  it('lists a pit once when two captures take it', () => {
    const lastMove = {
      seat: 0, pit: 8,
      trace: [
        { type: 'CAPTURE' as const, pit: 9, count: 2, facing: 17, behind: 25 },
        { type: 'CAPTURE' as const, pit: 10, count: 2, facing: 17, behind: 26 },
      ],
    };
    expect(lastMoveMarks(lastMove).captured).toEqual([17, 25, 26]);
  });
});
