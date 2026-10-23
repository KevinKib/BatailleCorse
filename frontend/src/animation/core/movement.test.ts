import { describe, expect, it } from 'vitest';
import { eventSpanMs, scaleMovements, scalePlan, type MotionPlan, type TableMovement } from './movement';

const move = (delayMs: number, durationMs: number, holdAfterMs = 100): TableMovement<string> => ({
  kind: 'travel', from: 'a', to: 'b', card: null, flip: false, turnDown: false, actor: 0, byOpponent: false,
  delayMs, durationMs, holdAfterMs, marks: [],
});

describe('eventSpanMs', () => {
  it('givenNoMovement_thenZero', () => {
    expect(eventSpanMs([])).toBe(0);
  });

  it('givenMovements_thenTheEndOfTheLastFlight_holdsAfterExcluded', () => {
    expect(eventSpanMs([move(100, 500, 900), move(300, 500)])).toBe(800);
  });
});

describe('scaleMovements', () => {
  it('givenAFactor_thenEveryDurationIsScaledAndRounded', () => {
    const [scaled] = scaleMovements([move(101, 701, 251)], 0.5);
    expect([scaled.delayMs, scaled.durationMs, scaled.holdAfterMs]).toEqual([51, 351, 126]);
    expect(scaled.from).toBe('a');
  });

  it('givenTheFactorOne_thenTheSameMovements', () => {
    const moves = [move(100, 500)];
    expect(scaleMovements(moves, 1)).toEqual(moves);
  });
});

describe('scalePlan', () => {
  it('givenAFactor_thenTheMovementsAndTheFollowUpsAreScaledAndTheirStartsToo', () => {
    const plan: MotionPlan<string> = {
      movements: [move(100, 500)],
      followUps: [{ movements: [move(40, 200, 60)], atMs: 1000 }],
      spanMs: 1600,
    };
    const scaled = scalePlan(plan, 0.5);
    expect(scaled.movements[0].durationMs).toBe(250);
    expect(scaled.followUps?.[0].movements[0].delayMs).toBe(20);
    expect(scaled.followUps?.[0].atMs).toBe(500);
    expect(scaled.spanMs).toBe(800);
  });

  it('givenTheFactorOne_thenTheSamePlan', () => {
    const plan: MotionPlan<string> = { movements: [move(100, 500)], spanMs: 700 };
    expect(scalePlan(plan, 1)).toEqual(plan);
  });

  it('givenNoFollowUpAndNoSpan_thenNeitherAppears', () => {
    const scaled = scalePlan({ movements: [move(100, 500)] }, 0.5);
    expect('followUps' in scaled).toBe(false);
    expect('spanMs' in scaled).toBe(false);
  });
});
