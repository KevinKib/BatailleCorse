import { describe, it, expect } from 'vitest';
import { format, plural } from './format';

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
