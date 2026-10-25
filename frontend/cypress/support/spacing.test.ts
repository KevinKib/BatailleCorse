import { describe, expect, it } from 'vitest';
import { gapBetween, overlaps, type Rect } from './spacing';

const rect = (left: number, top: number, width: number, height: number): Rect =>
  ({ left, top, right: left + width, bottom: top + height });

describe('gapBetween', () => {
  it('measures the vertical gap between stacked boxes, in either order', () => {
    const control = rect(0, 0, 300, 44);
    const hint = rect(0, 52, 300, 20);
    expect(gapBetween(control, hint)).toBe(8);
    expect(gapBetween(hint, control)).toBe(8);
  });

  it('measures the horizontal gap between boxes side by side', () => {
    expect(gapBetween(rect(0, 0, 100, 44), rect(112, 0, 100, 44))).toBe(12);
  });

  it('is zero for boxes that touch', () => {
    expect(gapBetween(rect(0, 0, 100, 44), rect(0, 44, 100, 44))).toBe(0);
  });

  it('is negative by the overlap depth when the boxes overlap', () => {
    expect(gapBetween(rect(0, 0, 100, 44), rect(0, 40, 100, 44))).toBe(-4);
  });

  it('takes the larger axis distance for boxes apart on both axes', () => {
    expect(gapBetween(rect(0, 0, 100, 40), rect(110, 60, 100, 40))).toBe(20);
  });
});

describe('overlaps', () => {
  it('is false for separate or touching boxes', () => {
    expect(overlaps(rect(0, 0, 100, 44), rect(0, 52, 100, 20))).toBe(false);
    expect(overlaps(rect(0, 0, 100, 44), rect(0, 44, 100, 44))).toBe(false);
  });

  it('is true when the boxes share an area deeper than the tolerance on both axes', () => {
    expect(overlaps(rect(0, 0, 100, 44), rect(50, 30, 100, 44))).toBe(true);
  });

  it('ignores a sub-pixel overlap from rounding (1 px tolerance by default)', () => {
    expect(overlaps(rect(0, 0, 100, 44), rect(0, 43.5, 100, 44))).toBe(false);
    expect(overlaps(rect(0, 0, 100, 44), rect(0, 43.5, 100, 44), 0)).toBe(true);
  });

  it('is false for boxes on the same row that do not meet horizontally', () => {
    expect(overlaps(rect(0, 0, 100, 44), rect(120, 0, 100, 44))).toBe(false);
  });
});
