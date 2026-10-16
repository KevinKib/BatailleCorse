import { describe, it, expect } from 'vitest';
import { exchangeView, suggestGiveBack } from './exchange';
import { buildPresidentHand } from '../fixtures/president';
import type { PresidentExchangePair } from './PresidentState';

const names = (cards: { name: string }[]) => cards.map(c => c.name);

describe('suggestGiveBack', () => {
  it('takesTheLowestCards', () => {
    const hand = buildPresidentHand(['3', 'CLUB'], ['4', 'CLUB'], ['9', 'HEART'], ['KING', 'SPADE']);
    expect(names(suggestGiveBack(hand, 2))).toEqual(['CLUB_3', 'CLUB_4']);
  });

  it('takesAWholePairWhenItFits', () => {
    const hand = buildPresidentHand(['3', 'CLUB'], ['3', 'DIAMOND'], ['4', 'CLUB'], ['9', 'HEART']);
    expect(names(suggestGiveBack(hand, 2))).toEqual(['CLUB_3', 'DIAMOND_3']);
  });

  it('skipsALowPairThatOneCardWouldBreak', () => {
    const hand = buildPresidentHand(['3', 'CLUB'], ['3', 'DIAMOND'], ['4', 'CLUB'], ['5', 'CLUB'], ['5', 'DIAMOND']);
    expect(names(suggestGiveBack(hand, 1))).toEqual(['CLUB_4']);
  });

  it('skipsPairsToFillTheCountWithSingles', () => {
    const hand = buildPresidentHand(['3', 'CLUB'], ['4', 'CLUB'], ['4', 'DIAMOND'], ['5', 'CLUB'], ['5', 'DIAMOND'], ['8', 'HEART']);
    expect(names(suggestGiveBack(hand, 2))).toEqual(['CLUB_3', 'HEART_8']);
  });

  it('breaksTheSmallestSetWhenEveryRankIsInASet', () => {
    const hand = buildPresidentHand(['3', 'CLUB'], ['3', 'DIAMOND'], ['3', 'HEART'], ['4', 'CLUB'], ['4', 'DIAMOND']);
    expect(names(suggestGiveBack(hand, 1))).toEqual(['CLUB_4']);
  });

  it('returnsTheCardsInHandOrder', () => {
    const hand = buildPresidentHand(['3', 'CLUB'], ['4', 'CLUB'], ['4', 'DIAMOND'], ['8', 'HEART']);
    expect(names(suggestGiveBack(hand, 3))).toEqual(['CLUB_3', 'CLUB_4', 'DIAMOND_4']);
  });

  it('givesWhatThereIsWhenTheHandIsShort', () => {
    expect(suggestGiveBack(buildPresidentHand(['3', 'CLUB']), 2)).toHaveLength(1);
    expect(suggestGiveBack(buildPresidentHand(['3', 'CLUB']), 0)).toEqual([]);
  });

  it('readsTenAsOneRank', () => {
    const hand = buildPresidentHand(['10', 'CLUB'], ['TEN', 'DIAMOND'], ['JACK', 'CLUB']);
    expect(names(suggestGiveBack(hand, 1))).toEqual(['CLUB_JACK']);
  });
});

describe('exchangeView', () => {
  const pairs: PresidentExchangePair[] = [
    { high: 0, low: 4, count: 2, done: false },
    { high: 1, low: 3, count: 1, done: true },
  ];

  it('aHighRankWhoStillOwesCardsIsGiving', () => {
    expect(exchangeView(pairs, 0)).toEqual({ role: 'giving', partner: 4, count: 2 });
  });

  it('aHighRankWhoHasGivenIsDone', () => {
    expect(exchangeView(pairs, 1)).toEqual({ role: 'gave', partner: 3, count: 1 });
  });

  it('aLowRankHasHadItsBestCardsTaken', () => {
    expect(exchangeView(pairs, 4)).toEqual({ role: 'taken', partner: 0, count: 2 });
    expect(exchangeView(pairs, 3)).toEqual({ role: 'taken', partner: 1, count: 1 });
  });

  it('aNeutralIsNotInAnyPair', () => {
    expect(exchangeView(pairs, 2)).toEqual({ role: 'idle', partner: null, count: 0 });
  });
});
