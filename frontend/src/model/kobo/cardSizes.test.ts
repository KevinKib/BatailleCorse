import { describe, it, expect } from 'vitest';
import { koboCardWidths } from './cardSizes';

describe('koboCardWidths', () => {
  it('givenAPhoneAndOneOpponent_thenTheCardsAreLarger', () => {
    expect(koboCardWidths(1, true)).toEqual({ opponent: 44, mine: 54 });
  });

  it('givenAWideScreenAndOneOpponent_thenTheCardsAreLarger', () => {
    expect(koboCardWidths(1, false)).toEqual({ opponent: 52, mine: 68 });
  });

  it.each([2, 3, 4, 5, 7])('given%iOpponents_thenTheDefaultSizesStay', (count) => {
    expect(koboCardWidths(count, true)).toEqual({ opponent: null, mine: null });
    expect(koboCardWidths(count, false)).toEqual({ opponent: null, mine: null });
  });
});
