import { describe, expect, it } from 'vitest';
import { SPEED_PROFILES, planSowing, tickMs } from './trace';
import type { MangolaStep } from './MangolaState';

const drops = (n: number, from = 0): MangolaStep[] =>
  Array.from({ length: n }, (_, i) => ({ type: 'DROP' as const, pit: (from + i) % 32 }));

/** A pick, then `n` drops split by `captures` captures, each followed by a resume. */
const sowing = (n: number, captures = 0): MangolaStep[] => {
  const trace: MangolaStep[] = [{ type: 'PICK', pit: 0, count: n }];
  const per = Math.floor(n / (captures + 1));
  let left = n;
  for (let c = 0; c < captures; c++) {
    trace.push(...drops(per, 1), { type: 'CAPTURE', pit: 5, count: 3, facing: 6, behind: 7 }, { type: 'RESUME_AFTER', pit: 5 });
    left -= per;
  }
  trace.push(...drops(left, 9));
  return trace;
};

describe('speed profiles', () => {
  it('Normal is 250 ms a seed, Fast 125 ms a seed, the hand slides in half a tick', () => {
    expect(SPEED_PROFILES.normal).toMatchObject({ tickMs: 250, handSlideMs: 125 });
    expect(SPEED_PROFILES.fast).toMatchObject({ tickMs: 125, handSlideMs: 62.5 });
  });

  it('Instant takes no time at all', () => {
    expect(SPEED_PROFILES.instant).toMatchObject({ tickMs: 0, handSlideMs: 0, leadInMs: 0 });
  });

  it('the pause before the seeds leave is 500 ms in Normal, half of that in Fast, none in Instant', () => {
    expect(SPEED_PROFILES.normal.leadInMs).toBe(500);
    expect(SPEED_PROFILES.fast.leadInMs).toBe(250);
    expect(SPEED_PROFILES.instant.leadInMs).toBe(0);
  });
});

describe('the cadence is constant', () => {
  const MOVES = [
    { seeds: 6, captures: 0 }, { seeds: 20, captures: 1 }, { seeds: 36, captures: 2 }, { seeds: 60, captures: 3 }, { seeds: 150, captures: 0 },
  ];

  for (const speed of ['normal', 'fast'] as const) {
    for (const { seeds, captures } of MOVES) {
      it(`${speed}: ${seeds} seeds and ${captures} capture(s) drop one seed per tick, every pause 1.5 ticks`, () => {
        const plan = planSowing(sowing(seeds, captures), speed);
        const tick = tickMs(speed);
        for (const s of plan.steps) {
          expect(s.durationMs).toBe(s.step.type === 'DROP' ? tick : 1.5 * tick);
        }
      });
    }
  }

  it('Normal is exactly twice as slow as Fast, step by step', () => {
    const trace = sowing(36, 2);
    const fast = planSowing(trace, 'fast');
    const normal = planSowing(trace, 'normal');
    normal.steps.forEach((s, i) => expect(s.durationMs).toBe(2 * fast.steps[i].durationMs));
  });

  it('a move with captures drops its seeds as slowly as one without', () => {
    const dropMs = (trace: MangolaStep[]) =>
      planSowing(trace, 'normal').steps.filter(s => s.step.type === 'DROP').map(s => s.durationMs);
    expect(Math.min(...dropMs(sowing(60, 3)))).toBe(Math.min(...dropMs(sowing(6))));
  });

  it('total duration of real moves', () => {
    // 6 seeds, a relay and a skip: pick, relay and skip are 375 ms each, 6 drops are 1500 ms.
    const quiet: MangolaStep[] = [
      { type: 'PICK', pit: 0, count: 6 }, ...drops(3, 1), { type: 'RELAY', pit: 3, count: 2 }, ...drops(2, 4), { type: 'SKIP_EMPTY', pit: 6 }, ...drops(1, 7),
    ];
    expect(planSowing(quiet, 'normal').totalMs).toBe(3 * 375 + 6 * 250);
    // 36 seeds, two captures: the pick and four pauses around the captures, 36 drops.
    expect(planSowing(sowing(36, 2), 'normal').totalMs).toBe(5 * 375 + 36 * 250);
    expect(planSowing(sowing(36, 2), 'fast').totalMs).toBe(5 * 187.5 + 36 * 125);
  });

  it('Instant plans zero time for every step', () => {
    const trace = sowing(36, 2);
    const instant = planSowing(trace, 'instant');
    expect(instant.totalMs).toBe(0);
    expect(instant.steps).toHaveLength(trace.length);
    expect(instant.steps.every(s => s.durationMs === 0 && s.atMs === 0)).toBe(true);
  });
});
