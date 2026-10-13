import { describe, it, expect } from 'vitest';
import { seatAngles, seatPositions } from './seatRing';

describe('seatAngles', () => {
  it('has no angle for nobody', () => { expect(seatAngles(0)).toEqual([]); });
  it('puts a single opponent at the top', () => { expect(seatAngles(1)).toEqual([90]); });
  it('puts a pair on the top corners, left first', () => { expect(seatAngles(2)).toEqual([135, 45]); });
  it('spreads three or more evenly from left, over the top, to right', () => {
    expect(seatAngles(3)).toEqual([180, 90, 0]);
    expect(seatAngles(5)).toEqual([180, 135, 90, 45, 0]);
  });
});

describe('seatPositions', () => {
  it('maps each angle onto the table ellipse, in percent of the frame', () => {
    const [left, top, right] = seatPositions(3);
    expect(left.left).toBeCloseTo(6); expect(left.top).toBeCloseTo(50);
    expect(top.left).toBeCloseTo(50); expect(top.top).toBeCloseTo(14);
    expect(right.left).toBeCloseTo(94); expect(right.top).toBeCloseTo(50);
  });
  it('is empty for nobody', () => { expect(seatPositions(0)).toEqual([]); });
});
