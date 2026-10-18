import { describe, expect, it } from 'vitest';
import { planExchangeFlights } from './exchangeMotion';
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
});
