import { describe, it, expect } from 'vitest';
import { format, ordinal, plural } from './format';

describe('format', () => {
  it('givenTemplateWithPlaceholders_thenReplacesEachWithItsValue', () => {
    expect(format('{joined} / {max} players', { joined: 2, max: 4 })).toBe('2 / 4 players');
  });

  it('givenRepeatedPlaceholder_thenReplacesAllOccurrences', () => {
    expect(format('{n} and {n}', { n: 3 })).toBe('3 and 3');
  });

  it('givenTemplateWithoutPlaceholders_thenReturnsItUnchanged', () => {
    expect(format('Lobby', {})).toBe('Lobby');
  });

  it('givenUnknownPlaceholder_thenLeavesItVisibleRatherThanDroppingIt', () => {
    expect(format('Hello {who}', {})).toBe('Hello {who}');
  });
});

describe('plural', () => {
  const forms = { one: '{n} card', other: '{n} cards' };

  it('givenOne_thenUsesTheSingularForm', () => {
    expect(plural(forms, 1)).toBe('1 card');
  });

  it('givenZeroOrMany_thenUsesTheOtherForm', () => {
    expect(plural(forms, 0)).toBe('0 cards');
    expect(plural(forms, 5)).toBe('5 cards');
  });
});

describe('ordinal', () => {
  const forms = { one: '{n}st', two: '{n}nd', few: '{n}rd', other: '{n}th' };

  it('givenTheFirstNumbers_thenUsesTheEnglishOrdinalForms', () => {
    expect([1, 2, 3, 4].map(n => ordinal(n, forms))).toEqual(['1st', '2nd', '3rd', '4th']);
  });

  it('givenTheTeens_thenTheyAreAllTh', () => {
    expect([11, 12, 13].map(n => ordinal(n, forms))).toEqual(['11th', '12th', '13th']);
  });

  it('givenNumbersPastTwenty_thenTheLastDigitDecides', () => {
    expect([21, 22, 23, 24, 101].map(n => ordinal(n, forms))).toEqual(['21st', '22nd', '23rd', '24th', '101st']);
  });
});
