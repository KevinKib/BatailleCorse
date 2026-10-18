import { describe, expect, it } from 'vitest';
import { planBubbles, planMovements, speedFactor } from './motion';
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
    const moves = planMovements(entry({ closure: { winner: 2, reason: 'CUT', closer: 1, cards: [card('2', 'CLUB')], size: 2 } }), ME);
    const ring = moves.at(-1)!;
    expect(ring).toMatchObject({ kind: 'ring', marks: [{ kind: 'seat', seat: 2 }], durationMs: 0 });
    expect(ring.delayMs).toBe(moves[0].delayMs + moves[0].durationMs);
  });

  it('givenAPassThatClosesTheTrick_thenOnlyTheRingOnTheWinner', () => {
    const moves = planMovements(entry({ action: 'PASS', cards: [], closure: { winner: 0, reason: 'ALL_PASSED', closer: 0, cards: [], size: 1 } }), ME);
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
