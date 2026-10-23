import { describe, expect, it } from 'vitest';
import { leg, type LegTempo } from './tempo';

describe('leg', () => {
  const tempo = {
    opponent: { holdBeforeMs: 250, flightMs: 1000, holdAfterMs: 450 } satisfies LegTempo,
    own: { holdBeforeMs: 150, flightMs: 700, holdAfterMs: 300 } satisfies LegTempo,
  };

  it('givenAnOpponent_thenTheOpponentLeg', () => {
    expect(leg(true, tempo)).toBe(tempo.opponent);
  });

  it('givenMe_thenMyLeg', () => {
    expect(leg(false, tempo)).toBe(tempo.own);
  });
});
