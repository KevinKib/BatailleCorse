import { describe, it, expect } from 'vitest';
import { claimSuit } from './claimSuit';

describe('claimSuit', () => {
  it.each([
    ['SPADE', '♠', false],
    ['spade', '♠', false],
    ['HEART', '♥', true],
    ['DIAMOND', '♦', true],
    ['CLUB', '♣', false],
  ])('maps %s to %s', (label, glyph, red) => {
    expect(claimSuit(label)).toEqual({ glyph, red });
  });

  it('returns null for a rank claim or an unknown label', () => {
    expect(claimSuit('ACE')).toBeNull();
    expect(claimSuit('')).toBeNull();
    expect(claimSuit(undefined)).toBeNull();
  });
});
