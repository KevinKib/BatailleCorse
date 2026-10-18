import { describe, expect, it } from 'vitest';
import { planBubbles, planMovements, planSweep, speedFactor, sweepStartMs } from './motion';
import { PRESIDENT_TEMPO } from './tempo';
import type { PresidentLogEntry } from './eventLog';
import { buildPresidentCard as card } from '../fixtures/president';
import { MOTION_MAX_MS } from '../kobo/tempo';

const entry = (over: Partial<PresidentLogEntry> = {}): PresidentLogEntry => ({
  id: 1, eventType: 'PLAY', action: 'PLAY', seat: 1, cards: [card('9', 'HEART')], round: 1, outOfTurn: false,
  outForbidden: false, outPlace: null, closure: null, ...over,
});
const ME = 0;

describe('planMovements', () => {
  it('givenAnOpponentsPlay_thenEachCardFliesFromTheSeatToItsPlaceOnThePile_withTheOpponentsTempo', () => {
    const moves = planMovements(entry({ cards: [card('9', 'HEART'), card('9', 'CLUB')] }), ME);
    expect(moves).toHaveLength(2);
    expect(moves.map(m => [m.kind, m.from, m.to, m.card?.name])).toEqual([
      ['travel', { kind: 'seat', seat: 1 }, { kind: 'pileCard', index: 0 }, 'HEART_9'],
      ['travel', { kind: 'seat', seat: 1 }, { kind: 'pileCard', index: 1 }, 'CLUB_9'],
    ]);
    const leg = PRESIDENT_TEMPO.opponent;
    expect(moves[0]).toMatchObject({ byOpponent: true, delayMs: leg.holdBeforeMs, durationMs: leg.flightMs, holdAfterMs: leg.holdAfterMs });
    expect(moves[1].delayMs).toBe(leg.holdBeforeMs + PRESIDENT_TEMPO.cardStaggerMs);
  });

  it('givenAPlay_thenTheSeatIsRingedDashedAndThePilePlain', () => {
    const [move] = planMovements(entry(), ME);
    expect(move.marks).toEqual([{ kind: 'seat', seat: 1 }, { kind: 'pile' }]);
  });

  it('givenMyPlay_thenMyTempoAndNoArrow', () => {
    const [move] = planMovements(entry({ seat: ME }), ME);
    expect(move).toMatchObject({ byOpponent: false, delayMs: PRESIDENT_TEMPO.own.holdBeforeMs, durationMs: PRESIDENT_TEMPO.own.flightMs });
  });

  it('givenAPass_thenNothingFlies', () => {
    expect(planMovements(entry({ action: 'PASS', cards: [] }), ME)).toEqual([]);
  });

  it('givenAPlayThatClosesTheTrick_thenAfterTheFlightsTheWinnersSeatIsRinged', () => {
    const moves = planMovements(entry({ closure: { winner: 2, reason: 'CUT', closer: 1, cards: [card('2', 'CLUB')], size: 2, trickCards: [card('2', 'CLUB'), card('9', 'HEART')] } }), ME);
    const ring = moves.at(-1)!;
    expect(ring).toMatchObject({ kind: 'ring', marks: [{ kind: 'seat', seat: 2 }], durationMs: 0 });
    expect(ring.delayMs).toBe(moves[0].delayMs + moves[0].durationMs);
  });

  it('givenAPassThatClosesTheTrick_thenOnlyTheRingOnTheWinner', () => {
    const moves = planMovements(entry({ action: 'PASS', cards: [], closure: { winner: 0, reason: 'ALL_PASSED', closer: 0, cards: [], size: 1, trickCards: [] } }), ME);
    expect(moves).toHaveLength(1);
    expect(moves[0]).toMatchObject({ kind: 'ring', marks: [{ kind: 'seat', seat: 0 }], delayMs: 0 });
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
    expect(moves.every(m => m.durationMs === 620 && m.holdAfterMs === 0 && !m.byOpponent)).toBe(true);
  });

  it('givenTheSweep_thenAnEndRingOnTheWinnerWhenTheLastCardHasLanded', () => {
    const moves = planSweep(closing(), ME);
    expect(moves.at(-1)).toMatchObject({ kind: 'ring', marks: [{ kind: 'seat', seat: 2 }], delayMs: 270 + 620 });
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
