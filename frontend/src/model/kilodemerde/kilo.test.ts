import { describe, it, expect } from 'vitest';
import { kiloLabel, kiloValue, kiloTotal } from './kilo';
import { kiloCard } from './fixtures';

describe('kiloValue', () => {
  it.each([
    ['ACE', 100], ['KING', 13], ['QUEEN', 12], ['JACK', 11], ['10', 10], ['9', 9], ['2', 2], ['7', 0],
  ])('%s weighs %i kg', (rank, kg) => {
    expect(kiloValue(kiloCard(rank))).toBe(kg);
  });
});

describe('kiloTotal', () => {
  it('adds the kilo cards', () => {
    expect(kiloTotal([kiloCard('KING'), kiloCard('9'), kiloCard('ACE')])).toBe(122);
    expect(kiloTotal([])).toBe(0);
  });
});

describe('kiloLabel', () => {
  it('writes the rank letter or number and the suit symbol', () => {
    expect(kiloLabel(kiloCard('KING', 'SPADE'))).toBe('K♠');
    expect(kiloLabel(kiloCard('9', 'CLUB'))).toBe('9♣');
    expect(kiloLabel(kiloCard('10', 'HEART'))).toBe('10♥');
    expect(kiloLabel(kiloCard('ACE', 'DIAMOND'))).toBe('A♦');
    expect(kiloLabel(kiloCard('QUEEN', 'HEART'))).toBe('Q♥');
    expect(kiloLabel(kiloCard('JACK', 'CLUB'))).toBe('J♣');
  });

  it('takes the face letters of the language', () => {
    const fr = { ACE: 'A', KING: 'R', QUEEN: 'D', JACK: 'V' };
    expect(kiloLabel(kiloCard('KING', 'SPADE'), fr)).toBe('R♠');
    expect(kiloLabel(kiloCard('JACK', 'SPADE'), fr)).toBe('V♠');
    expect(kiloLabel(kiloCard('8', 'SPADE'), fr)).toBe('8♠');
  });

  it('keeps an unknown suit as given', () => {
    expect(kiloLabel({ rank: '4', suit: 'STAR', name: 'STAR_4' })).toBe('4');
  });
});
