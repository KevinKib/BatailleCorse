import { describe, it, expect } from 'vitest';
import { cardLabel, rankLabel } from './cardLabel';
import { buildPresidentCard } from '../fixtures/president';

const texts = {
  cardLabel: '{rank} of {suit}',
  cardSelected: '{card}, selected',
  ranks: { JACK: 'Jack', QUEEN: 'Queen', KING: 'King', ACE: 'Ace' },
  suits: { HEART: 'hearts', DIAMOND: 'diamonds', CLUB: 'clubs', SPADE: 'spades' },
};

describe('cardLabel', () => {
  it('givenAFaceCard_thenItIsNamedWithItsRankAndSuit', () => {
    expect(cardLabel(buildPresidentCard('QUEEN', 'HEART'), texts, false)).toBe('Queen of hearts');
  });

  it('givenASelectedCard_thenTheNameSaysSo', () => {
    expect(cardLabel(buildPresidentCard('QUEEN', 'HEART'), texts, true)).toBe('Queen of hearts, selected');
  });

  it('givenANumberCard_thenTheRankIsTheNumber', () => {
    expect(cardLabel(buildPresidentCard('10', 'SPADE'), texts, false)).toBe('10 of spades');
    expect(rankLabel('3', texts)).toBe('3');
  });
});
