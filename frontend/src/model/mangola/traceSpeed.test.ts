import { describe, expect, it } from 'vitest';
import { MAX_TOTAL_MS, SPEED_PROFILES, planSowing, tickMs } from './trace';
import type { MangolaStep } from './MangolaState';

const drops = (n: number, from = 0): MangolaStep[] =>
  Array.from({ length: n }, (_, i) => ({ type: 'DROP' as const, pit: (from + i) % 32 }));

/** A pick, `n` drops with a capture in the middle: the pauses scale too. */
const sowing = (n: number): MangolaStep[] => [
  { type: 'PICK', pit: 0, count: n },
  ...drops(Math.floor(n / 2), 1),
  { type: 'CAPTURE', pit: 5, count: 3, facing: 6, behind: 7 },
  { type: 'RESUME_AFTER', pit: 5 },
  ...drops(n - Math.floor(n / 2), 9),
];

describe('speed profiles', () => {
  it('Fast is the rhythm of before: 2400 ms shared, ticks 60..280, pause 200, cap 4.5 s, slide 90 ms', () => {
    expect(SPEED_PROFILES.fast).toMatchObject({
      tickBudgetMs: 2400, tickMinMs: 60, tickMaxMs: 280, minPauseMs: 200, maxTotalMs: 4500, handSlideMs: 90,
    });
    expect(MAX_TOTAL_MS).toBe(4500);
  });

  it('Normal doubles the budget, the bounds, the pause and the slide, and caps a sowing at 7 s', () => {
    expect(SPEED_PROFILES.normal).toMatchObject({
      tickBudgetMs: 4800, tickMinMs: 120, tickMaxMs: 560, minPauseMs: 400, maxTotalMs: 7000, handSlideMs: 180,
    });
  });

  it('Instant takes no time at all', () => {
    expect(SPEED_PROFILES.instant).toMatchObject({ tickBudgetMs: 0, minPauseMs: 0, maxTotalMs: 0, handSlideMs: 0, leadInMs: 0 });
  });

  it('the pause before the seeds leave is 500 ms in Normal, half of that in Fast, none in Instant', () => {
    expect(SPEED_PROFILES.normal.leadInMs).toBe(500);
    expect(SPEED_PROFILES.fast.leadInMs).toBe(250);
    expect(SPEED_PROFILES.instant.leadInMs).toBe(0);
  });
});

describe('tickMs with a speed', () => {
  it('doubles in Normal, whatever the number of seeds', () => {
    for (const n of [0, 2, 10, 33, 100, 400]) {
      expect(tickMs(n, 'normal')).toBeCloseTo(2 * tickMs(n, 'fast'), 9);
    }
  });
});

describe('planSowing with a speed', () => {
  // 5 seeds, 33 seeds (the longest that is still just twice as slow), 43 seeds (over the old rate bounds), and one
  // long enough to hit the cap in every speed.
  const CASES: { drops: number; capped: { normal: boolean; fast: boolean } }[] = [
    { drops: 5, capped: { normal: false, fast: false } },
    { drops: 33, capped: { normal: false, fast: false } },
    { drops: 43, capped: { normal: false, fast: false } },
    { drops: 150, capped: { normal: true, fast: true } },
  ];

  for (const { drops: n, capped } of CASES) {
    describe(`${n} seeds`, () => {
      const trace = sowing(n);
      const fast = planSowing(trace, 'fast');
      const normal = planSowing(trace, 'normal');

      it('Fast keeps the old plan', () => {
        expect(fast).toEqual(planSowing(trace));
        expect(fast.totalMs).toBeLessThanOrEqual(4500 + 1e-6);
      });

      it('Normal never lasts more than 7 s', () => {
        expect(normal.totalMs).toBeLessThanOrEqual(7000 + 1e-6);
      });

      if (!capped.normal) {
        it('Normal is exactly twice as slow, step by step', () => {
          expect(normal.totalMs).toBeCloseTo(2 * fast.totalMs, 6);
          normal.steps.forEach((s, i) => expect(s.durationMs).toBeCloseTo(2 * fast.steps[i].durationMs, 6));
        });
      } else {
        it('Normal is capped at 7 s, Fast at 4.5 s', () => {
          expect(normal.totalMs).toBeCloseTo(7000, 6);
          expect(fast.totalMs).toBeCloseTo(4500, 6);
        });
      }

      it('Instant plans zero time for every step', () => {
        const instant = planSowing(trace, 'instant');
        expect(instant.totalMs).toBe(0);
        expect(instant.steps).toHaveLength(trace.length);
        expect(instant.steps.every(s => s.durationMs === 0 && s.atMs === 0)).toBe(true);
      });
    });
  }

  it('a sowing too long for Fast is also capped in Normal, which stays the slower of the two', () => {
    const trace = sowing(120);
    const normal = planSowing(trace, 'normal');
    const fast = planSowing(trace, 'fast');
    expect(fast.totalMs).toBeCloseTo(4500, 6);
    expect(normal.totalMs).toBeCloseTo(7000, 6);
  });
});
