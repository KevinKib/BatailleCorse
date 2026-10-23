import { describe, expect, it } from 'vitest';
import { ANIMATION_SPEEDS, nextAnimationSpeed, speedFactor } from './speed';

describe('nextAnimationSpeed', () => {
  it('cycles Normal, Fast, Instant, Normal', () => {
    expect(nextAnimationSpeed('normal')).toBe('fast');
    expect(nextAnimationSpeed('fast')).toBe('instant');
    expect(nextAnimationSpeed('instant')).toBe('normal');
  });
});

describe('speedFactor', () => {
  it.each([['normal', 1], ['fast', 0.5], ['instant', 0]] as const)('%s', (speed, factor) => {
    expect(speedFactor(speed)).toBe(factor);
  });
});

describe('ANIMATION_SPEEDS', () => {
  it('lists the three speeds in the order of the cycle', () => {
    expect(ANIMATION_SPEEDS).toEqual(['normal', 'fast', 'instant']);
  });
});
