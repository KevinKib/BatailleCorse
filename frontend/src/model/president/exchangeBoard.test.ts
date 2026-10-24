import { describe, expect, it } from 'vitest';
import { exchangeBoard, exchangeLegKey } from './exchangeBoard';
import { buildPresidentCard as card, buildPresidentState } from '../fixtures/president';
import type { PresidentExchangePair, PresidentState } from './PresidentState';

describe('exchangeLegKey', () => {
  it('givenAPairAndALeg_thenTheKeyIsHighDashLowColonLeg', () => {
    expect(exchangeLegKey({ high: 0, low: 2 }, 'best')).toBe('0-2:best');
    expect(exchangeLegKey({ high: 3, low: 1 }, 'back')).toBe('3-1:back');
  });

  it('givenTwoPairsOrLegs_thenTheKeysDiffer', () => {
    const keys = [
      exchangeLegKey({ high: 0, low: 3 }, 'best'), exchangeLegKey({ high: 0, low: 3 }, 'back'),
      exchangeLegKey({ high: 1, low: 2 }, 'best'), exchangeLegKey({ high: 1, low: 2 }, 'back'),
    ];
    expect(new Set(keys).size).toBe(4);
  });
});

const best = [card('KING', 'HEART'), card('ACE', 'CLUB')];
const back = [card('3', 'CLUB'), card('9', 'HEART')];
const pairs = (...done: boolean[]): PresidentExchangePair[] =>
  [{ high: 0, low: 3, count: 2, done: done[0] ?? false }, { high: 1, low: 2, count: 1, done: done[1] ?? false }];
const exchanging = (over: Partial<PresidentState> = {}) =>
  buildPresidentState({ round: 2, phase: 'EXCHANGE', exchange: pairs(), ...over });
const nothing: ReadonlySet<string> = new Set();

describe('exchangeBoard', () => {
  it.each(['PLAYING', 'ROUND_OVER', 'FINISHED'] as const)('givenThe%sPhase_thenThereIsNoBoard', (phase) => {
    expect(exchangeBoard(exchanging({ phase }), 0, nothing)).toBeNull();
  });

  it('givenNoPair_thenThereIsNoBoard', () => {
    expect(exchangeBoard(exchanging({ exchange: [] }), 0, nothing)).toBeNull();
    expect(exchangeBoard(exchanging({ phase: 'EXCHANGE_VIEW', exchange: [] }), 0, nothing)).toBeNull();
  });

  it('givenTheServersPairs_thenOneRowPerPairInTheServersOrder', () => {
    const board = exchangeBoard(exchanging(), 4, nothing)!;
    expect(board.rows.map(r => [r.key, r.high, r.low, r.count])).toEqual([['0-3', 0, 3, 2], ['1-2', 1, 2, 1]]);
  });

  it('givenAReturnNotGivenYetAndNothingInFlight_thenTheRowIsChoosing', () => {
    expect(exchangeBoard(exchanging(), 4, nothing)!.rows.map(r => r.state)).toEqual(['choosing', 'choosing']);
  });

  it('givenTheBestCardsInFlight_thenTheRowIsGiving_whateverTheServerSays', () => {
    const flying = new Set([exchangeLegKey({ high: 0, low: 3 }, 'best')]);
    expect(exchangeBoard(exchanging(), 4, flying)!.rows.map(r => r.state)).toEqual(['giving', 'choosing']);
    expect(exchangeBoard(exchanging({ exchange: pairs(true) }), 4, flying)!.rows[0].state).toBe('giving');
  });

  it('givenTheReturnInFlight_thenTheRowIsReturning_andOnceItHasLandedDone', () => {
    const flying = new Set([exchangeLegKey({ high: 0, low: 3 }, 'back')]);
    const state = exchanging({ exchange: pairs(true) });
    expect(exchangeBoard(state, 4, flying)!.rows.map(r => r.state)).toEqual(['returning', 'choosing']);
    expect(exchangeBoard(state, 4, nothing)!.rows.map(r => r.state)).toEqual(['done', 'choosing']);
  });

  it('givenTheBestCardsStillFlyingAndTheReturnToo_thenTheFirstLegWins', () => {
    const flying = new Set([exchangeLegKey({ high: 0, low: 3 }, 'best'), exchangeLegKey({ high: 0, low: 3 }, 'back')]);
    expect(exchangeBoard(exchanging({ exchange: pairs(true) }), 4, flying)!.rows[0].state).toBe('giving');
  });

  it('givenTheExchangeView_thenEveryPairIsDoneOnceItsLegsHaveLanded', () => {
    const view = exchanging({ phase: 'EXCHANGE_VIEW', exchange: pairs(true, true) });
    expect(exchangeBoard(view, 4, nothing)!.rows.map(r => r.state)).toEqual(['done', 'done']);
    const flying = new Set([exchangeLegKey({ high: 1, low: 2 }, 'back')]);
    expect(exchangeBoard(view, 4, flying)!.rows.map(r => r.state)).toEqual(['done', 'returning']);
  });

  it('givenMyPairAsTheLowTitle_thenIKnowTheFacesIGaveAndTheOnesThatCameBack', () => {
    const state = exchanging({ exchange: pairs(true), given: best, received: back });
    const [mine, other] = exchangeBoard(state, 3, nothing)!.rows;
    expect(mine).toMatchObject({ mine: true, best, back });
    expect(other).toMatchObject({ mine: false, best: null, back: null });
  });

  it('givenMyPairAsTheHighTitle_thenItReadsTheSameCardsTheOtherWay', () => {
    const state = exchanging({ exchange: pairs(true), received: best, given: back });
    expect(exchangeBoard(state, 0, nothing)!.rows[0]).toMatchObject({ mine: true, best, back });
  });

  it('givenMyReturnNotGivenYet_thenOnlyTheBestCardsHaveFaces', () => {
    const asLow = exchangeBoard(exchanging({ given: best, received: [] }), 3, nothing)!.rows[0];
    expect(asLow).toMatchObject({ best, back: null });
    const asHigh = exchangeBoard(exchanging({ received: best, given: [] }), 0, nothing)!.rows[0];
    expect(asHigh).toMatchObject({ best, back: null });
  });

  it('givenANeutral_thenNoRowIsMineAndNoRowHasFaces', () => {
    const board = exchangeBoard(exchanging({ exchange: pairs(true, true), given: best, received: back }), 4, nothing)!;
    expect(board.neutral).toBe(true);
    expect(board.rows.every(r => !r.mine && r.best === null && r.back === null)).toBe(true);
  });

  it('givenIAmInAPair_thenIAmNotNeutral', () => {
    expect(exchangeBoard(exchanging(), 2, nothing)!.neutral).toBe(false);
  });

  it('givenAPairGoneAfterAForfeit_thenItsRowLeavesWithIt', () => {
    const board = exchangeBoard(exchanging({ exchange: [{ high: 0, low: 3, count: 2, done: true }] }), 4, nothing)!;
    expect(board.rows.map(r => r.key)).toEqual(['0-3']);
  });

  it('givenAReload_thenNothingIsInFlightAndTheBoardShowsTheStateAsItIs', () => {
    const view = exchanging({ phase: 'EXCHANGE_VIEW', exchange: pairs(true, true), given: best, received: back });
    expect(exchangeBoard(view, 3, nothing)!.rows.map(r => r.state)).toEqual(['done', 'done']);
  });
});
