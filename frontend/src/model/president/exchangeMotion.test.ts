import { describe, expect, it } from 'vitest';
import { exchangeLandings, planExchangeFlights } from './exchangeMotion';
import { PRESIDENT_TEMPO } from './tempo';
import { buildPresidentCard as card, buildPresidentState } from '../fixtures/president';
import type { PresidentState } from './PresidentState';

// Seat 0 is the President (high), seat 2 the Scum (low): two cards each way. Seat 1 is Neutral.
const roundOver = buildPresidentState({ round: 1, phase: 'ROUND_OVER' });
const pair = (done: boolean) => [{ high: 0, low: 2, count: 2, done }];
const best = [card('2', 'HEART'), card('ACE', 'CLUB')];
const back = [card('3', 'CLUB'), card('4', 'SPADE')];
const exchange = (over: Partial<PresidentState>) => buildPresidentState({ round: 2, phase: 'EXCHANGE', exchange: pair(false), ...over });

describe('planExchangeFlights', () => {
  it('givenTheDealOfAnExchangeRound_thenTheLowTitlesBestCardsFlyToTheHighTitle_facesInTheirPairOnly', () => {
    const asScum = planExchangeFlights(roundOver, exchange({ given: best, received: [] }), 2);
    expect(asScum.map(m => [m.from, m.to, m.card?.name])).toEqual([
      [{ kind: 'seat', seat: 2 }, { kind: 'seat', seat: 0 }, 'HEART_2'],
      [{ kind: 'seat', seat: 2 }, { kind: 'seat', seat: 0 }, 'CLUB_ACE'],
    ]);
    const asPresident = planExchangeFlights(roundOver, exchange({ received: best }), 0);
    expect(asPresident.map(m => m.card?.name)).toEqual(['HEART_2', 'CLUB_ACE']);
    const asNeutral = planExchangeFlights(roundOver, exchange({}), 1);
    expect(asNeutral.map(m => [m.from, m.to, m.card])).toEqual([
      [{ kind: 'seat', seat: 2 }, { kind: 'seat', seat: 0 }, null],
      [{ kind: 'seat', seat: 2 }, { kind: 'seat', seat: 0 }, null],
    ]);
  });

  it('givenAGiveBack_thenTheReturnedCardsFlyFromTheHighTitleToTheLowOne', () => {
    const before = exchange({ received: best });
    const asPresident = planExchangeFlights(before, exchange({ exchange: pair(true), received: best, given: back }), 0);
    expect(asPresident.map(m => [m.from, m.to, m.card?.name])).toEqual([
      [{ kind: 'seat', seat: 0 }, { kind: 'seat', seat: 2 }, 'CLUB_3'],
      [{ kind: 'seat', seat: 0 }, { kind: 'seat', seat: 2 }, 'SPADE_4'],
    ]);
    const asScum = planExchangeFlights(exchange({ given: best }), exchange({ exchange: pair(true), given: best, received: back }), 2);
    expect(asScum.map(m => m.card?.name)).toEqual(['CLUB_3', 'SPADE_4']);
    const asNeutral = planExchangeFlights(exchange({}), exchange({ exchange: pair(true) }), 1);
    expect(asNeutral.every(m => m.card === null)).toBe(true);
  });

  it('givenMyOwnCards_thenMyTempoAndNoArrow_andTheirsTheOpponentTempoWithAnArrow', () => {
    const [mine] = planExchangeFlights(roundOver, exchange({ given: best }), 2);
    expect(mine).toMatchObject({ byOpponent: false, durationMs: PRESIDENT_TEMPO.own.flightMs });
    const [theirs] = planExchangeFlights(roundOver, exchange({}), 1);
    expect(theirs).toMatchObject({ byOpponent: true, durationMs: PRESIDENT_TEMPO.opponent.flightMs });
  });

  it('givenAStateThatChangesNoPair_thenNothingFlies', () => {
    const now = exchange({});
    expect(planExchangeFlights(now, now, 1)).toEqual([]);
    expect(planExchangeFlights(null, now, 1)).toEqual([]);
  });

  it('givenTheFastSpeed_thenTwiceAsFast', () => {
    const [fast] = planExchangeFlights(roundOver, exchange({}), 1, 0.5);
    expect(fast.durationMs).toBe(PRESIDENT_TEMPO.opponent.flightMs / 2);
  });
  it('givenTheLastGiveBackThatStartsThePlay_thenTheReturnedCardsStillFly', () => {
    // the server leaves the exchange as soon as the last pair is done: the state is PLAYING with every pair done
    const before = exchange({ received: best });
    const after = buildPresidentState({ round: 2, phase: 'PLAYING', exchange: pair(true), received: best, given: back });
    expect(planExchangeFlights(before, after, 0).map(m => m.card?.name)).toEqual(['CLUB_3', 'SPADE_4']);
  });

  it('givenAStateAlreadySeenAgain_thenNothingFliesTwice', () => {
    const done = buildPresidentState({ round: 2, phase: 'PLAYING', exchange: pair(true), received: best, given: back });
    expect(planExchangeFlights(done, { ...done, version: 9 }, 0)).toEqual([]);
  });

  it('givenADealWhoseReturnsAreAlreadyMade_thenBothLegsFly_theReturnAfterTheFirstLeg', () => {
    // giveBack=lowest: the deal arrives with the pair done and the play started
    const after = buildPresidentState({ round: 2, phase: 'PLAYING', exchange: pair(true), received: best, given: back });
    const moves = planExchangeFlights(roundOver, after, 0);
    expect(moves.map(m => [m.from, m.to, m.card?.name])).toEqual([
      [{ kind: 'seat', seat: 2 }, { kind: 'seat', seat: 0 }, 'HEART_2'],
      [{ kind: 'seat', seat: 2 }, { kind: 'seat', seat: 0 }, 'CLUB_ACE'],
      [{ kind: 'seat', seat: 0 }, { kind: 'seat', seat: 2 }, 'CLUB_3'],
      [{ kind: 'seat', seat: 0 }, { kind: 'seat', seat: 2 }, 'SPADE_4'],
    ]);
    expect(moves[2].delayMs).toBeGreaterThanOrEqual(moves[0].delayMs + moves[0].durationMs);
  });

  it('givenAStateWithoutAnExchange_thenNothingFlies', () => {
    expect(planExchangeFlights(roundOver, buildPresidentState({ round: 2 }), 0)).toEqual([]);
  });
});

// Pairs one after the other: (high 0, low 7) then (high 1, low 6), then two more; seat 9 is me, in no pair.
const pairsOf = (count: number, done: boolean) => [
  { high: 0, low: 7, count: 2, done }, { high: 1, low: 6, count: 2, done },
  { high: 2, low: 5, count: 1, done }, { high: 3, low: 4, count: 1, done },
].slice(0, count);
const dealt = (count: number, done: boolean) => buildPresidentState({
  round: 2, phase: done ? 'PLAYING' : 'EXCHANGE', exchange: pairsOf(count, done),
});
const landing = (m: { delayMs: number; durationMs: number }) => m.delayMs + m.durationMs;
const { opponent, exchange: gaps } = PRESIDENT_TEMPO;
const seatOf = (anchor: unknown) => (anchor as { seat: number }).seat;

describe('planExchangeFlights, pair by pair', () => {
  it('givenTwoPairsAtTheDeal_thenTheSecondStartsOnceTheFirstHasLanded_plusTheGap_itsStaggerCountedInsideItself', () => {
    const moves = planExchangeFlights(roundOver, dealt(2, false), 9);
    expect(moves.map(m => seatOf(m.from))).toEqual([7, 7, 6, 6]);
    const firstLanded = landing(moves[1]);
    expect(firstLanded).toBe(opponent.holdBeforeMs + PRESIDENT_TEMPO.cardStaggerMs + opponent.flightMs);
    expect(moves[2].delayMs).toBe(firstLanded + gaps.pairGapMs + opponent.holdBeforeMs);
    expect(moves[3].delayMs - moves[2].delayMs).toBe(PRESIDENT_TEMPO.cardStaggerMs);
  });

  it('givenTheTopPairFirst_thenEveryPairStartsAfterTheOneBeforeIt', () => {
    const moves = planExchangeFlights(roundOver, dealt(4, false), 9);
    const delays = moves.map(m => m.delayMs);
    expect(delays).toEqual([...delays].sort((a, b) => a - b));
    expect(moves[0].delayMs).toBe(opponent.holdBeforeMs);
  });

  it('givenTheLowestGiveBack_thenTheReturnComesBehindItsOwnPairBeforeTheNextPair', () => {
    const moves = planExchangeFlights(roundOver, dealt(2, true), 9);
    expect(moves.map(m => [seatOf(m.from), seatOf(m.to)])).toEqual([
      [7, 0], [7, 0], [0, 7], [0, 7], [6, 1], [6, 1], [1, 6], [1, 6],
    ]);
    expect(moves[2].delayMs).toBeGreaterThanOrEqual(landing(moves[0]));
    const returned = Math.max(landing(moves[2]), landing(moves[3]));
    expect(moves[4].delayMs).toBe(returned + gaps.pairGapMs + opponent.holdBeforeMs);
  });

  it('givenADealLongerThanTheCap_thenThePairsAreScaledDownToEndAtTheCap_theFirstStillLeavingFirst', () => {
    const moves = planExchangeFlights(roundOver, dealt(4, true), 9);
    expect(Math.max(...moves.map(landing))).toBe(gaps.maxSpanMs);
    expect(moves[0].delayMs).toBe(opponent.holdBeforeMs);
    const starts = [0, 4, 8, 10].map(i => moves[i].delayMs);
    expect(starts).toEqual([...starts].sort((a, b) => a - b));
    // the pairs overlap: the second leaves before the first has landed
    expect(moves[4].delayMs).toBeLessThan(landing(moves[3]));
  });

  it('givenADealUnderTheCap_thenNothingIsScaledDown', () => {
    const moves = planExchangeFlights(roundOver, dealt(2, true), 9);
    expect(Math.max(...moves.map(landing))).toBeLessThan(gaps.maxSpanMs);
    expect(moves[4].delayMs).toBe(Math.max(landing(moves[2]), landing(moves[3])) + gaps.pairGapMs + opponent.holdBeforeMs);
  });

  it('givenTheFastSpeed_thenTheCappedDealEndsAtHalfTheCap_andAnUncappedOneAtHalfItsSpan', () => {
    const capped = planExchangeFlights(roundOver, dealt(4, true), 9, 0.5);
    // each delay and duration is rounded on its own: 1 ms of rounding at most
    expect(Math.abs(Math.max(...capped.map(landing)) - gaps.maxSpanMs / 2)).toBeLessThanOrEqual(1);
    const normal = planExchangeFlights(roundOver, dealt(2, false), 9);
    const fast = planExchangeFlights(roundOver, dealt(2, false), 9, 0.5);
    expect(Math.abs(Math.max(...fast.map(landing)) - Math.max(...normal.map(landing)) / 2)).toBeLessThanOrEqual(1);
  });

  it('givenSeveralPairsGivenBackInOneState_thenTheirReturnsAreSequencedToo', () => {
    const before = buildPresidentState({ round: 2, phase: 'EXCHANGE', exchange: pairsOf(2, false) });
    const after = buildPresidentState({ round: 2, phase: 'PLAYING', exchange: pairsOf(2, true) });
    const moves = planExchangeFlights(before, after, 9);
    expect(moves.map(m => seatOf(m.from))).toEqual([0, 0, 1, 1]);
    expect(moves[2].delayMs).toBe(landing(moves[1]) + gaps.pairGapMs + opponent.holdBeforeMs);
  });
});

describe('exchangeLandings', () => {
  it('givenNoMoves_thenNoLandings', () => {
    expect(exchangeLandings(exchange({}), [])).toEqual({});
  });

  it('givenTheSequencedDeal_thenEachLegLandsWithItsLastCard_bestUpAndBackDown', () => {
    const after = dealt(2, true);
    const moves = planExchangeFlights(roundOver, after, 9);
    expect(exchangeLandings(after, moves)).toEqual({
      '0-7:best': landing(moves[1]), '0-7:back': landing(moves[3]),
      '1-6:best': landing(moves[5]), '1-6:back': landing(moves[7]),
    });
  });

  it('givenAGiveBackOnly_thenOnlyTheBackLegLands', () => {
    const before = buildPresidentState({ round: 2, phase: 'EXCHANGE', exchange: pairsOf(2, false) });
    const [first, second] = pairsOf(2, false);
    const after = buildPresidentState({ round: 2, phase: 'EXCHANGE', exchange: [{ ...first, done: true }, second] });
    const moves = planExchangeFlights(before, after, 9);
    expect(Object.keys(exchangeLandings(after, moves))).toEqual(['0-7:back']);
  });

  it('givenTheFastSpeed_thenTheLandingsFollowTheScaledMoves', () => {
    const after = dealt(1, false);
    const moves = planExchangeFlights(roundOver, after, 9, 0.5);
    expect(exchangeLandings(after, moves)['0-7:best']).toBe(landing(moves[1]));
  });
});

describe('the exchange tempo', () => {
  it('givenTheApprovedNumbers_thenPairsAreSpacedAndTheDealCappedAndTheCountsWaitForThem', () => {
    expect(PRESIDENT_TEMPO.exchange).toEqual({ pairGapMs: 250, maxSpanMs: 6000 });
    expect(PRESIDENT_TEMPO.countLagMaxMs).toBe(6500);
  });
});
