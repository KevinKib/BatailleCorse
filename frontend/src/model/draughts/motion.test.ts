import { describe, it, expect } from 'vitest';
import { planMove } from './motion';
import { NORMAL_TEMPO, FAST_TEMPO, INSTANT_TEMPO, tempoFor } from './tempo';

const T = { legMs: 100, removalMs: 50 };

describe('planMove', () => {
  it('glides a plain move in one leg and removes nothing', () => {
    const plan = planMove({ from: 32, path: [28], captures: [] }, T);
    expect(plan.glides).toEqual([{ from: 32, to: 28, atMs: 0, durationMs: 100 }]);
    expect(plan.removals).toEqual([]);
    expect(plan.totalMs).toBe(100);
  });

  it('glides a capture square to square along the path', () => {
    const plan = planMove({ from: 46, path: [32, 19], captures: [37, 28] }, T);
    expect(plan.glides).toEqual([
      { from: 46, to: 32, atMs: 0, durationMs: 100 },
      { from: 32, to: 19, atMs: 100, durationMs: 100 },
    ]);
  });

  it('removes the captured pieces in capture order once the glide is over', () => {
    const plan = planMove({ from: 46, path: [32, 19], captures: [37, 28] }, T);
    expect(plan.removals).toEqual([
      { square: 37, atMs: 200, durationMs: 50 },
      { square: 28, atMs: 250, durationMs: 50 },
    ]);
    expect(plan.totalMs).toBe(300);
  });

  it('takes no time at all at the instant tempo', () => {
    const plan = planMove({ from: 46, path: [32, 19], captures: [37, 28] }, INSTANT_TEMPO);
    expect(plan.totalMs).toBe(0);
    expect(plan.glides.every(g => g.durationMs === 0 && g.atMs === 0)).toBe(true);
    expect(plan.removals.map(r => r.square)).toEqual([37, 28]);
  });
});

describe('tempoFor', () => {
  it('maps a speed to its tempo', () => {
    expect(tempoFor('normal')).toBe(NORMAL_TEMPO);
    expect(tempoFor('fast')).toBe(FAST_TEMPO);
    expect(tempoFor('instant')).toBe(INSTANT_TEMPO);
  });

  it('gets faster from normal to instant', () => {
    expect(FAST_TEMPO.legMs).toBeLessThan(NORMAL_TEMPO.legMs);
    expect(FAST_TEMPO.removalMs).toBeLessThan(NORMAL_TEMPO.removalMs);
    expect(INSTANT_TEMPO).toEqual({ legMs: 0, removalMs: 0 });
  });
});
