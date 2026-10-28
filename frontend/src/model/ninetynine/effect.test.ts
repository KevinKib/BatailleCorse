import { describe, it, expect } from 'vitest';
import { effectCode } from './effect';

describe('effectCode (the key of the log line, never a rule)', () => {
  it.each([
    ['2', 'face'], ['3', 'face'], ['5', 'face'], ['6', 'face'], ['7', 'face'], ['8', 'face'],
    ['4', 'reverse'], ['9', 'pass'], ['10', 'minusTen'],
    ['JACK', 'plusTen'], ['QUEEN', 'plusTen'], ['KING', 'ninetyNine'], ['ACE', 'ace'],
  ])('rank %s reads as %s', (rank, code) => {
    expect(effectCode(rank)).toBe(code);
  });

  it('keeps an unknown rank as a plain face value', () => {
    expect(effectCode('JOKER')).toBe('face');
  });
});
