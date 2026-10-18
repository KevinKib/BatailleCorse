import { describe, expect, it } from 'vitest';
import { eventSpanMs, scaleMovements, type TableMovement } from './motion';

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
