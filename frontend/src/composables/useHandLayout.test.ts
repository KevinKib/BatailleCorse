import { describe, it, expect } from 'vitest';
import { computeHandLayout, maxCardForViewport } from './useHandLayout';

const opts = { maxCard: 60, minCard: 44 };
const rowWidth = (n: number, l: { cardWidth: number; step: number }) => (n - 1) * l.step + l.cardWidth;

describe('computeHandLayout', () => {
  it('spaces a small hand without overlap', () => {
    const l = computeHandLayout(5, 359, opts);
    expect(l.cardWidth).toBe(60);
    expect(l.step).toBe(64);
  });

  it('overlaps a larger hand so it still fits on one row', () => {
    const l = computeHandLayout(13, 359, opts);
    expect(l.cardWidth).toBe(60);
    expect(l.step).toBeLessThan(60);
    expect(rowWidth(13, l)).toBeLessThanOrEqual(359);
  });

  it('shrinks cards, bounded by the minimum, for a very large hand', () => {
    const l = computeHandLayout(26, 359, opts);
    expect(l.cardWidth).toBe(44);
    expect(rowWidth(26, l)).toBeLessThanOrEqual(359.5);
  });

  it('keeps a 26-card hand on one row at every phone and desktop width', () => {
    for (const w of [343, 374, 860]) {
      const l = computeHandLayout(26, w, { maxCard: 76, minCard: 44 });
      expect(rowWidth(26, l)).toBeLessThanOrEqual(w + 0.5);
      expect(l.step).toBeGreaterThan(0);
    }
  });

  it('falls back to spaced cards while the width is still unknown', () => {
    expect(computeHandLayout(26, 0, opts)).toEqual({ cardWidth: 60, step: 64 });
    expect(computeHandLayout(0, 300, opts).cardWidth).toBe(60);
  });
});

describe('maxCardForViewport', () => {
  it('scales with the height within bounds', () => {
    expect(maxCardForViewport(300)).toBe(44);
    expect(maxCardForViewport(667)).toBe(60);
    expect(maxCardForViewport(2000)).toBe(76);
  });
});
