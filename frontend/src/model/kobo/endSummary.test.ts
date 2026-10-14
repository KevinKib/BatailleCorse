import { describe, it, expect } from 'vitest';
import { endSummary } from './endSummary';
import { buildKoboPlayer, buildKoboState } from '../fixtures/kobo';

const finished = (winners: number[], reason: string, totals: number[] = [30, 41, 41]) => buildKoboState({
  phase: 'FINISHED',
  players: totals.map((total, seat) => buildKoboPlayer(seat, { total })),
  outcome: { status: 'FINISHED', winners, reason },
});

describe('endSummary', () => {
  it('givenAWinByScore_thenTheWinnersTheReasonAndTheirLowestTotal', () => {
    const summary = endSummary(finished([0], 'SCORE'), 1);
    expect(summary).toEqual({ winners: [0], iWon: false, byForfeit: false, lowestTotal: 30 });
  });

  it('givenATie_thenEveryTiedPlayerWins', () => {
    const summary = endSummary(finished([1, 2], 'SCORE'), 2);
    expect(summary).toEqual({ winners: [1, 2], iWon: true, byForfeit: false, lowestTotal: 41 });
  });

  it('givenAForfeit_thenByForfeitAndTheRemainingTotal', () => {
    const summary = endSummary(finished([0], 'FORFEIT', [12]), 0);
    expect(summary).toEqual({ winners: [0], iWon: true, byForfeit: true, lowestTotal: 12 });
  });

  it('givenAGameStillOn_thenNull', () => {
    expect(endSummary(buildKoboState(), 0)).toBeNull();
  });
});
