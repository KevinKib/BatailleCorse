import { describe, it, expect } from 'vitest';
import { computeHandLayout, maxCardForViewport } from './useHandLayout';

const opts = { maxCard: 60, minCard: 44 };
const rowWidth = (n: number, l: { cardWidth: number; step: number }) => (n - 1) * l.step + l.cardWidth;

describe('computeHandLayout', () => {
  it('spaces a small hand without overlap, on one row', () => {
    const l = computeHandLayout(5, 359, opts);
    expect(l).toEqual({ cardWidth: 60, step: 64, rows: 1, perRow: 5 });
  });

  it('overlaps a larger hand on one row while each card keeps a comfortable strip', () => {
    const l = computeHandLayout(13, 359, opts);
    expect(l.rows).toBe(1);
    expect(l.cardWidth).toBe(60);
    expect(l.step).toBeGreaterThanOrEqual(22);
    expect(l.step).toBeLessThan(60);
    expect(rowWidth(13, l)).toBeLessThanOrEqual(359);
  });

  it('splits into two rows when one row would overlap too much', () => {
    const l = computeHandLayout(26, 359, opts);
    expect(l.rows).toBe(2);
    expect(l.perRow).toBe(13);
    expect(l.step).toBeGreaterThanOrEqual(22);
    expect(rowWidth(13, l)).toBeLessThanOrEqual(359.5);
  });

  it('keeps 52 cards inside the width on two rows, at every phone and desktop width', () => {
    for (const w of [343, 374, 984]) {
      const l = computeHandLayout(52, w, { maxCard: 76, minCard: 44 });
      expect(l.rows).toBe(2);
      expect(l.perRow).toBe(26);
      expect(rowWidth(26, l)).toBeLessThanOrEqual(w + 0.5);
      expect(l.step).toBeGreaterThan(0);
      expect(l.cardWidth).toBeGreaterThanOrEqual(44);
    }
  });

  it('puts the extra card of an odd hand on the first row', () => {
    const l = computeHandLayout(27, 359, opts);
    expect(l.rows).toBe(2);
    expect(l.perRow).toBe(14);
  });

  it('uses the full desktop width with a single row for a 26-card hand', () => {
    const l = computeHandLayout(26, 984, { maxCard: 65, minCard: 44 });
    expect(l.rows).toBe(1);
    expect(l.step).toBeGreaterThanOrEqual(22);
  });

  it('falls back to spaced cards while the width is still unknown', () => {
    expect(computeHandLayout(26, 0, opts)).toEqual({ cardWidth: 60, step: 64, rows: 1, perRow: 26 });
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
