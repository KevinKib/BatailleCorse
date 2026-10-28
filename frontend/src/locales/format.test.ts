import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { format, formatList, ordinal, plural } from './format';
import { setCurrentLocaleForTest } from './locale';

describe('formatList', () => {
  it('givenThreeItems_thenConjunctionJoinsWithCommasAndAnd', () => {
    expect(formatList(['a', 'b', 'c'], 'conjunction')).toBe('a, b, and c');
  });

  it('givenThreeItems_thenDisjunctionJoinsWithCommasAndOr', () => {
    expect(formatList(['1', '2', '3'], 'disjunction')).toBe('1, 2, or 3');
  });

  it('givenTwoItemsOrOneOrNone_thenNoComma', () => {
    expect(formatList(['a', 'b'], 'conjunction')).toBe('a and b');
    expect(formatList(['a'], 'disjunction')).toBe('a');
    expect(formatList([], 'conjunction')).toBe('');
  });
});

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

describe('locale-aware formatting in French', () => {
  beforeEach(() => setCurrentLocaleForTest('fr'));
  afterEach(() => setCurrentLocaleForTest('en'));

  it('givenFrench_thenZeroIsSingular', () => {
    const forms = { one: '{n} carte', other: '{n} cartes' };
    expect(plural(forms, 0)).toBe('0 carte');
    expect(plural(forms, 1)).toBe('1 carte');
    expect(plural(forms, 2)).toBe('2 cartes');
  });

  it('givenFrench_thenOrdinalsAreErThenE', () => {
    const forms = { one: '{n}er', two: '{n}e', few: '{n}e', other: '{n}e' };
    expect([1, 2, 3, 11].map(n => ordinal(n, forms))).toEqual(['1er', '2e', '3e', '11e']);
  });

  it('givenFrench_thenListsUseEtAndOu', () => {
    expect(formatList(['a', 'b', 'c'], 'conjunction')).toBe('a, b et c');
    expect(formatList(['1', '2', '3'], 'disjunction')).toBe('1, 2 ou 3');
  });

  it('givenEnglishAgain_thenEnglishRulesApply', () => {
    setCurrentLocaleForTest('en');
    expect(plural({ one: '{n} card', other: '{n} cards' }, 0)).toBe('0 cards');
  });
});
