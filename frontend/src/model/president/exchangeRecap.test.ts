import { describe, expect, it } from 'vitest';
import { exchangeRecap } from './exchangeRecap';
import { buildPresidentCard as card, buildPresidentState } from '../fixtures/president';
import type { PresidentExchangePair } from './PresidentState';

const best = [card('2', 'HEART'), card('ACE', 'CLUB')];
const back = [card('3', 'CLUB'), card('4', 'SPADE')];
const pairs = (done: boolean): PresidentExchangePair[] => [{ high: 0, low: 2, count: 2, done }, { high: 1, low: 3, count: 1, done }];
const played = (over = {}) => buildPresidentState({ phase: 'PLAYING', round: 2, exchange: pairs(true), ...over });

describe('exchangeRecap', () => {
  it('givenTheLowTitle_thenItsPairShowsWhatItGaveAndWhatCameBack_theOthersOnlyTheirCounts', () => {
    const lines = exchangeRecap(played({ given: best, received: back }), 2);
    expect(lines).toEqual([
      { high: 0, low: 2, count: 2, best, back, mine: true },
      { high: 1, low: 3, count: 1, best: null, back: null, mine: false },
    ]);
  });

  it('givenTheHighTitle_thenItReadsTheSameCardsTheOtherWay', () => {
    const [mine] = exchangeRecap(played({ received: best, given: back }), 0);
    expect(mine).toMatchObject({ best, back, mine: true });
  });

  it('givenANeutral_thenNoPairHasCards', () => {
    expect(exchangeRecap(played({}), 4).every(l => l.best === null && !l.mine)).toBe(true);
  });

  it('givenTheExchangeView_thenTheSummaryShowsAlreadyWithTheSameLines', () => {
    const view = exchangeRecap(played({ phase: 'EXCHANGE_VIEW', given: best, received: back }), 2);
    expect(view).toEqual(exchangeRecap(played({ given: best, received: back }), 2));
    expect(view).toHaveLength(2);
  });

  it('givenAReturnStillToCome_thenThereIsNoRecapYet', () => {
    const pending = [{ high: 0, low: 2, count: 2, done: true }, { high: 1, low: 3, count: 1, done: false }];
    expect(exchangeRecap(played({ phase: 'EXCHANGE', exchange: pending }), 2)).toEqual([]);
  });

  it('givenNoExchangeOrTheRoundOver_thenNoRecap', () => {
    expect(exchangeRecap(played({ exchange: [] }), 2)).toEqual([]);
    expect(exchangeRecap(played({ phase: 'ROUND_OVER' }), 2)).toEqual([]);
  });
});
