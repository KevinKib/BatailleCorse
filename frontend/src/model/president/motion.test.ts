import { describe, expect, it } from 'vitest';
import { planBubbles, planMovements, planSweep, sourcesFor, speedFactor, sweepStartMs } from './motion';
import { PRESIDENT_TEMPO } from './tempo';
import type { PresidentLogEntry } from './eventLog';
import { buildPresidentCard as card } from '../fixtures/president';
import { MOTION_MAX_MS } from '../kobo/tempo';

const entry = (over: Partial<PresidentLogEntry> = {}): PresidentLogEntry => ({
  id: 1, eventType: 'PLAY', action: 'PLAY', seat: 1, cards: [card('9', 'HEART')], round: 1, outOfTurn: false,
  outForbidden: false, outPlace: null, closure: null, ...over,
});
const ME = 0;

describe('sourcesFor', () => {
  const rect = { left: 1, top: 2, width: 3, height: 4 };
  const mine = entry({ seat: ME, cards: [card('9', 'HEART')] });

  it('givenFreshMeasuresOfMyPlay_thenTheyAreGiven', () => {
    expect(sourcesFor(mine, ME, { rects: { HEART_9: rect }, at: 1000 }, 3000)).toEqual({ HEART_9: rect });
  });

  it('givenAnOpponentsPlayOrAPass_thenNone', () => {
    const pending = { rects: { HEART_9: rect }, at: 1000 };
    expect(sourcesFor(entry({ seat: 1 }), ME, pending, 1500)).toBeUndefined();
    expect(sourcesFor(entry({ seat: ME, action: 'PASS', cards: [] }), ME, pending, 1500)).toBeUndefined();
  });

  it('givenStaleMeasuresOrOtherCards_thenNone', () => {
    expect(sourcesFor(mine, ME, { rects: { HEART_9: rect }, at: 1000 }, 1000 + 60_000)).toBeUndefined();
    expect(sourcesFor(mine, ME, { rects: { CLUB_9: rect }, at: 1000 }, 1500)).toBeUndefined();
    expect(sourcesFor(mine, ME, null, 1500)).toBeUndefined();
  });
});

describe('planMovements', () => {
  it('givenAnOpponentsPlay_thenEachCardFliesFromTheFanToItsPlaceOnThePile_withTheOpponentsTempo', () => {
    const moves = planMovements(entry({ cards: [card('9', 'HEART'), card('9', 'CLUB')] }), ME);
    expect(moves).toHaveLength(2);
    expect(moves.map(m => [m.kind, m.from, m.to, m.card?.name])).toEqual([
      ['travel', { kind: 'fanTop', seat: 1 }, { kind: 'pileCard', index: 0 }, 'HEART_9'],
      ['travel', { kind: 'fanTop', seat: 1 }, { kind: 'pileCard', index: 1 }, 'CLUB_9'],
    ]);
    const leg = PRESIDENT_TEMPO.opponent;
    expect(moves[0]).toMatchObject({ byOpponent: true, delayMs: leg.holdBeforeMs, durationMs: leg.flightMs, holdAfterMs: leg.holdAfterMs });
    expect(moves[1].delayMs).toBe(leg.holdBeforeMs + PRESIDENT_TEMPO.cardStaggerMs);
  });

  it('givenAnOpponentsPlay_thenEachCardLeavesTheTopBackOfTheFan_andOnlyTheCardsThatLandedAreRinged', () => {
    const moves = planMovements(entry({ cards: [card('9', 'HEART'), card('9', 'CLUB')] }), ME);
    expect(moves.map(m => m.from)).toEqual([{ kind: 'fanTop', seat: 1 }, { kind: 'fanTop', seat: 1 }]);
    expect(moves[0].marks).toEqual([{ kind: 'fanTop', seat: 1 }, { kind: 'pileCard', index: 0 }]);
    expect(moves[1].marks).toEqual([{ kind: 'fanTop', seat: 1 }, { kind: 'pileCard', index: 1 }]);
  });

  it('givenMyPlayWithMeasuredSources_thenEachCardLeavesItsExactPlaceAndThatPlaceIsTheDashedRing', () => {
    const a = { left: 10, top: 500, width: 56, height: 81 };
    const b = { left: 40, top: 500, width: 56, height: 81 };
    const moves = planMovements(entry({ seat: ME, cards: [card('9', 'HEART'), card('9', 'CLUB')] }), ME, 1,
      { HEART_9: a, CLUB_9: b });
    expect(moves.map(m => m.from)).toEqual([{ kind: 'rect', ...a }, { kind: 'rect', ...b }]);
    expect(moves[1].marks).toEqual([{ kind: 'rect', ...b }, { kind: 'pileCard', index: 1 }]);
  });

  it('givenMyPlayWithoutAMeasuredSource_thenItLeavesMyHandZone', () => {
    const [move] = planMovements(entry({ seat: ME }), ME);
    expect(move.from).toEqual({ kind: 'seat', seat: ME });
    expect(move.marks).toEqual([{ kind: 'seat', seat: ME }, { kind: 'pileCard', index: 0 }]);
  });

  it('givenMyPlay_thenMyTempoAndNoArrow', () => {
    const [move] = planMovements(entry({ seat: ME }), ME);
    expect(move).toMatchObject({ byOpponent: false, delayMs: PRESIDENT_TEMPO.own.holdBeforeMs, durationMs: PRESIDENT_TEMPO.own.flightMs });
  });

  it('givenAPass_thenNothingFlies', () => {
    expect(planMovements(entry({ action: 'PASS', cards: [] }), ME)).toEqual([]);
  });

  it('givenAPlayThatClosesTheTrick_thenAfterTheFlightsAnEmptyRingMarksTheEnd_andNoSeatIsRinged', () => {
    const moves = planMovements(entry({ closure: { winner: 2, reason: 'CUT', closer: 1, cards: [card('2', 'CLUB')], size: 2, trickCards: [card('2', 'CLUB'), card('9', 'HEART')] } }), ME);
    const ring = moves.at(-1)!;
    expect(ring).toMatchObject({ kind: 'ring', marks: [], durationMs: 0 });
    expect(ring.delayMs).toBe(moves[0].delayMs + moves[0].durationMs);
  });

  it('givenAPassThatClosesTheTrick_thenOnlyTheEmptyRingThatKeepsTheSweepGoing', () => {
    const moves = planMovements(entry({ action: 'PASS', cards: [], closure: { winner: 0, reason: 'ALL_PASSED', closer: 0, cards: [], size: 1, trickCards: [] } }), ME);
    expect(moves).toHaveLength(1);
    expect(moves[0]).toMatchObject({ kind: 'ring', marks: [], delayMs: 0 });
  });

  it('givenTheFastSpeed_thenEverythingIsTwiceAsFast', () => {
    const normal = planMovements(entry(), ME, speedFactor('normal'));
    const fast = planMovements(entry(), ME, speedFactor('fast'));
    expect(fast[0].durationMs).toBe(Math.round(normal[0].durationMs / 2));
    expect(fast[0].delayMs).toBe(Math.round(normal[0].delayMs / 2));
  });

  it('neverMakesAMovementLongerThanTheTableLimit', () => {
    const four = [card('9', 'HEART'), card('9', 'CLUB'), card('9', 'SPADE'), card('9', 'DIAMOND')];
    for (const m of planMovements(entry({ cards: four }), ME)) {
      expect(m.delayMs + m.durationMs + m.holdAfterMs).toBeLessThanOrEqual(MOTION_MAX_MS);
    }
  });
});

describe('speedFactor', () => {
  it.each([['normal', 1], ['fast', 0.5], ['instant', 0]] as const)('%s', (speed, factor) => {
    expect(speedFactor(speed)).toBe(factor);
  });
});

describe('planBubbles', () => {
  it('givenAPass_thenAPassBubbleOnTheSeat', () => {
    expect(planBubbles(entry({ action: 'PASS', cards: [] }))).toEqual([{ seat: 1, kind: 'pass' }]);
  });

  it('givenAPlay_thenNoBubble', () => {
    expect(planBubbles(entry())).toEqual([]);
  });

  it('givenAForbiddenFinish_thenAnOutOnATwoBubble', () => {
    expect(planBubbles(entry({ outForbidden: true }))).toEqual([{ seat: 1, kind: 'outForbidden' }]);
  });

  it('givenAPlayOutOfTurn_thenAFourCompletedBubble', () => {
    expect(planBubbles(entry({ outOfTurn: true }))).toEqual([{ seat: 1, kind: 'completed' }]);
  });
});

describe('planSweep', () => {
  const closing = (over: Partial<PresidentLogEntry> = {}): PresidentLogEntry => entry({
    seat: 1, cards: [card('2', 'CLUB'), card('2', 'SPADE')],
    closure: {
      winner: 2, reason: 'CUT', closer: 1, cards: [card('2', 'CLUB'), card('2', 'SPADE')], size: 4,
      trickCards: [card('2', 'CLUB'), card('2', 'SPADE'), card('9', 'HEART'), card('9', 'CLUB')],
    },
    ...over,
  });

  it('givenNoClosure_thenNothingSweeps', () => {
    expect(planSweep(entry(), ME)).toEqual([]);
  });

  it('givenAClosedTrick_thenEveryCardFliesToTheWinnersSeat_theClosingPlayFirstFromItsOwnCards', () => {
    const moves = planSweep(closing(), ME).filter(m => m.kind === 'travel');
    expect(moves.map(m => [m.card?.name, m.from, m.to])).toEqual([
      ['CLUB_2', { kind: 'pileCard', index: 0 }, { kind: 'seat', seat: 2 }],
      ['SPADE_2', { kind: 'pileCard', index: 1 }, { kind: 'seat', seat: 2 }],
      ['HEART_9', { kind: 'pileSlot' }, { kind: 'seat', seat: 2 }],
      ['CLUB_9', { kind: 'pileSlot' }, { kind: 'seat', seat: 2 }],
    ]);
  });

  it('givenTheSweepTempo_thenCardsLeaveNinetyMillisecondsApartAndFlyFor620', () => {
    const moves = planSweep(closing(), ME).filter(m => m.kind === 'travel');
    expect(moves.map(m => m.delayMs)).toEqual([0, 90, 180, 270]);
    expect(moves.every(m => m.durationMs === 620 && m.holdAfterMs === PRESIDENT_TEMPO.sweep.landHoldMs && !m.byOpponent)).toBe(true);
  });

  it('givenTheSweep_thenAnEmptyEndMovementWhenTheLastCardHasLanded', () => {
    const moves = planSweep(closing(), ME);
    expect(moves.at(-1)).toMatchObject({ kind: 'ring', marks: [], delayMs: 270 + 620 });
  });

  it('givenAPassThatClosesTheTrick_thenTheCardsStartFromThePileSlots', () => {
    const moves = planSweep(entry({
      action: 'PASS', cards: [], closure: {
        winner: 0, reason: 'ALL_PASSED', closer: 0, cards: [card('7', 'HEART')], size: 2,
        trickCards: [card('7', 'HEART'), card('5', 'CLUB')],
      },
    }), ME).filter(m => m.kind === 'travel');
    expect(moves.map(m => m.from)).toEqual([{ kind: 'pileCard', index: 0 }, { kind: 'pileSlot' }]);
  });

  it('givenAHugeTrick_thenOnlyTheLatestCardsSweepSoTheLayerStaysLight', () => {
    const many = Array.from({ length: 30 }, (_, i) => card('3', i % 2 ? 'CLUB' : 'HEART'));
    const moves = planSweep(closing({ closure: { winner: 2, reason: 'CUT', closer: 1, cards: many.slice(0, 1), size: 30, trickCards: many } }), ME);
    expect(moves.filter(m => m.kind === 'travel')).toHaveLength(PRESIDENT_TEMPO.sweep.maxCards);
  });

  it('givenTheFastSpeed_thenTheSweepIsTwiceAsFast', () => {
    const normal = planSweep(closing(), ME, 1).filter(m => m.kind === 'travel');
    const fast = planSweep(closing(), ME, 0.5).filter(m => m.kind === 'travel');
    expect(fast[1].durationMs).toBe(normal[1].durationMs / 2);
    expect(fast[1].delayMs).toBe(normal[1].delayMs / 2);
  });
});

describe('sweepStartMs', () => {
  const passClosure = { winner: 0, reason: 'ALL_PASSED', closer: 0, cards: [], size: 1, trickCards: [] };

  it('givenAPlayThatClosesTheTrick_thenTheSweepWaitsForTheFlightsAndTheBadgeHold', () => {
    const e = entry({ cards: [card('2', 'CLUB')], closure: { winner: 2, reason: 'CUT', closer: 1, cards: [card('2', 'CLUB')], size: 2, trickCards: [card('2', 'CLUB')] } });
    const leg = PRESIDENT_TEMPO.opponent;
    expect(sweepStartMs(e, ME)).toBe(leg.holdBeforeMs + leg.flightMs + leg.holdAfterMs + PRESIDENT_TEMPO.sweep.badgeHoldMs);
  });

  it('givenAPassThatClosesTheTrick_thenOnlyTheBadgeHold', () => {
    const e = entry({ action: 'PASS', cards: [], closure: passClosure });
    expect(sweepStartMs(e, ME)).toBe(PRESIDENT_TEMPO.sweep.badgeHoldMs);
  });

  it('givenTheFastSpeed_thenHalf', () => {
    const e = entry({ action: 'PASS', cards: [], closure: passClosure });
    expect(sweepStartMs(e, ME, 0.5)).toBe(PRESIDENT_TEMPO.sweep.badgeHoldMs / 2);
  });
});
