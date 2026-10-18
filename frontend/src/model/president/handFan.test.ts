import { describe, expect, it } from 'vitest';
import { fanLayout, FAN_MAX_BACKS } from './handFan';

describe('fanLayout', () => {
  it('givenNoCard_thenNoFan', () => {
    expect(fanLayout(0, false).backs).toBe(0);
  });

  it.each([1, 3, 6])('givenFewCards (%i)_thenOneBackPerCard', (n) => {
    expect(fanLayout(n, false).backs).toBe(n);
  });

  it.each([[false, FAN_MAX_BACKS.wide], [true, FAN_MAX_BACKS.narrow]] as const)('givenAFullHand (narrow: %s)_thenTheBacksAreCapped', (narrow, cap) => {
    expect(fanLayout(18, narrow).backs).toBe(cap);
  });

  it('givenMoreBacks_thenTheFanTightensAndNeverGrowsPastItsWidth', () => {
    const few = fanLayout(3, false);
    const many = fanLayout(18, false);
    expect(many.stepPx).toBeLessThan(few.stepPx);
    const width = (l: ReturnType<typeof fanLayout>) => l.cardW + (l.backs - 1) * l.stepPx;
    expect(width(many)).toBeLessThanOrEqual(fanLayout(18, false).maxWidth);
    expect(width(fanLayout(18, true))).toBeLessThanOrEqual(fanLayout(18, true).maxWidth);
  });

  it('givenAPhone_thenSmallerCards', () => {
    expect(fanLayout(5, true).cardW).toBeLessThan(fanLayout(5, false).cardW);
  });
});
