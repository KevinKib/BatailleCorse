import { describe, expect, it } from 'vitest';
import { buildPresidentState } from '../fixtures/president';
import type { PresidentState } from './PresidentState';
import { endKind, endTimings } from './endSequence';

const lastRound = (round: number): PresidentState['lastRound'] => ({
  round, finishingOrder: [0, 1, 2],
  players: [0, 1, 2].map(seat => ({ seat, place: seat + 1, title: null, points: 3 - seat, total: 3 - seat, forbiddenFinish: false })),
});

describe('endKind', () => {
  it('givenARunningRound_thenNothingEnds', () => {
    expect(endKind(buildPresidentState())).toBe('none');
    expect(endKind(buildPresidentState({ phase: 'EXCHANGE' }))).toBe('none');
  });

  it('givenTheRoundIsScored_thenItIsARoundEnd', () => {
    expect(endKind(buildPresidentState({ phase: 'ROUND_OVER', lastRound: lastRound(1) }))).toBe('round');
  });

  it('givenTheMatchEndedOnTheLastRound_thenTheRoundSheetComesBeforeTheFinalScores', () => {
    const game = buildPresidentState({
      phase: 'FINISHED', round: 5, lastRound: lastRound(5), outcome: { status: 'FINISHED', winners: [0], reason: 'POINTS' },
    });
    expect(endKind(game)).toBe('final');
  });

  it('givenTheMatchEndedByAForfeit_thenThereIsNoRoundToShow', () => {
    const game = buildPresidentState({
      phase: 'FINISHED', round: 2, lastRound: lastRound(1), outcome: { status: 'FINISHED', winners: [0], reason: 'FORFEIT' },
    });
    expect(endKind(game)).toBe('abandoned');
  });

  it('givenAFinishedMatchWithoutTheScoreOfItsLastRound_thenItGoesStraightToTheFinalScores', () => {
    const game = buildPresidentState({
      phase: 'FINISHED', round: 3, lastRound: lastRound(2), outcome: { status: 'FINISHED', winners: [0], reason: 'POINTS' },
    });
    expect(endKind(game)).toBe('abandoned');
  });
});

describe('endTimings', () => {
  it('givenTheNormalSpeed_thenTheGameGetsAMomentToSettleBeforeTheSheet', () => {
    expect(endTimings(1, false)).toEqual({ graceMs: 80, settleMs: 1100 });
  });

  it('givenFast_thenTheWaitsAreShorter', () => {
    const fast = endTimings(0.5, false);
    expect(fast.settleMs).toBe(550);
    expect(fast.graceMs).toBe(80);
  });

  it('givenAStillTable_thenThereIsNothingToWaitFor', () => {
    expect(endTimings(1, true)).toEqual({ graceMs: 0, settleMs: 0 });
  });
});
