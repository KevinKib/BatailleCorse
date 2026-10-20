import { describe, it, expect } from 'vitest';
import { bindingOf } from './binding';
import { buildPresidentCard, buildPresidentTrick } from '../fixtures/president';

const nine = (suit: string) => buildPresidentCard('9', suit);

describe('bindingOf (the "N or nothing" state of equal play)', () => {
  it('givenAFreeOrBeatTrick_thenNoBinding', () => {
    expect(bindingOf(buildPresidentTrick())).toBeNull();
    expect(bindingOf(buildPresidentTrick({ constraint: 'BEAT', top: { kind: 'SET', rank: '9', count: 1 } }))).toBeNull();
  });

  it('givenEqualPlay_thenTheRankIsBoundAndTheCardsOfTheChainAreCountedOutOfFour', () => {
    const trick = buildPresidentTrick({
      constraint: 'MATCH_OR_PASS', requiredRank: '9', requiredCount: 1,
      chain: { rank: '9', plays: 2, cards: [nine('HEART'), nine('DIAMOND')] },
    });
    expect(bindingOf(trick)).toEqual({ rank: '9', played: 2, total: 4, left: 2 });
  });

  it('givenAChainOfPairs_thenEachCardCountsNotEachPlay', () => {
    const trick = buildPresidentTrick({
      constraint: 'MATCH_OR_PASS', requiredRank: '9', requiredCount: 2,
      chain: { rank: '9', plays: 1, cards: [nine('HEART'), nine('DIAMOND')] },
    });
    expect(bindingOf(trick)).toEqual({ rank: '9', played: 2, total: 4, left: 2 });
  });

  it('givenABindingWithoutAChainYet_thenItCountsTheCardsRequired', () => {
    const trick = buildPresidentTrick({ constraint: 'MATCH_OR_PASS', requiredRank: '9', requiredCount: 1, chain: null });
    expect(bindingOf(trick)).toEqual({ rank: '9', played: 1, total: 4, left: 3 });
  });

  it('givenMoreThanFourCards_thenThePipsStopAtFour', () => {
    const trick = buildPresidentTrick({
      constraint: 'MATCH_OR_PASS', requiredRank: '9', requiredCount: 1,
      chain: { rank: '9', plays: 5, cards: [1, 2, 3, 4, 5].map(() => nine('HEART')) },
    });
    expect(bindingOf(trick)).toEqual({ rank: '9', played: 4, total: 4, left: 0 });
  });
});
