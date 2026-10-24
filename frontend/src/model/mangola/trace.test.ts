import { describe, it, expect } from 'vitest';
import {
  capturedCount, dropCount, lastMoveMarks, planSowing, tickMs,
} from './trace';
import type { MangolaStep } from './MangolaState';

const drops = (n: number, from = 8): MangolaStep[] =>
  Array.from({ length: n }, (_, i) => ({ type: 'DROP' as const, pit: from + i }));

describe('tickMs (time of one dropped seed)', () => {
  it('is a constant of the speed: 125 ms in Fast, 250 ms in Normal, 0 in Instant', () => {
    expect(tickMs('fast')).toBe(125);
    expect(tickMs('normal')).toBe(250);
    expect(tickMs('instant')).toBe(0);
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

  it('every drop lasts the same tick from the first seed to the last, whatever the length of the move', () => {
    for (const n of [1, 2, 10, 40, 150, 600]) {
      for (const speed of ['fast', 'normal'] as const) {
        const durations = planSowing(drops(n), speed).steps.map(s => s.durationMs);
        expect(new Set(durations)).toEqual(new Set([tickMs(speed)]));
      }
    }
  });

  it('every other step is a pause of 1.5 ticks, with no minimum', () => {
    const trace: MangolaStep[] = [{ type: 'PICK', pit: 8, count: 40 }, ...drops(40, 9)];
    expect(planSowing(trace, 'fast').steps[0].durationMs).toBe(187.5);
    expect(planSowing(trace, 'normal').steps[0].durationMs).toBe(375);
  });

  it('has no cap on the whole move: it lasts what its steps last', () => {
    const trace: MangolaStep[] = [{ type: 'PICK', pit: 8, count: 600 }, ...drops(600, 9)];
    expect(planSowing(trace, 'fast').totalMs).toBe(187.5 + 600 * 125);
    expect(planSowing(trace, 'normal').totalMs).toBe(375 + 600 * 250);
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
