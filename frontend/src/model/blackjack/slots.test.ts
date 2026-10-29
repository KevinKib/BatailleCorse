import { describe, it, expect } from 'vitest';
import { overlapOf, slotRect } from './slots';
import { CARD_RATIO } from '../../animation';

describe('slotRect', () => {
  const box = { left: 100, top: 40, width: 80, height: 100 };

  it('puts the first card in the middle of the row, a card wide', () => {
    const r = slotRect(box, 50, 0);
    expect(r.width).toBe(50);
    expect(r.height).toBeCloseTo(50 * CARD_RATIO);
    expect(r.left).toBe(140 - 25);
  });

  it('puts the n-th card where it lands once the row holds n + 1 cards, centred on the same middle', () => {
    const w = 50;
    const step = w - overlapOf(w);
    const r1 = slotRect(box, w, 1);
    const rowWidth = w + step;
    expect(r1.left).toBeCloseTo(140 - rowWidth / 2 + step);
    const r2 = slotRect(box, w, 2);
    expect(r2.left).toBeCloseTo(140 - (w + 2 * step) / 2 + 2 * step);
    expect(r2.left).toBeGreaterThan(r1.left);
  });

  it('is centred vertically on the row', () => {
    const r = slotRect(box, 50, 0);
    expect(r.top + r.height / 2).toBeCloseTo(40 + 50);
  });
});

describe('overlapOf', () => {
  it('overlaps a card by about a third of its width', () => {
    expect(overlapOf(60)).toBe(18);
    expect(overlapOf(34)).toBe(10);
  });
});
