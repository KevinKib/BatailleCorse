import { describe, expect, it } from 'vitest';
import { ackDelayMs, exchangeViewStatus, waitingFor, SUMMARY_READ_MS } from './exchangeView';
import { buildPresidentPlayer as player, buildPresidentState } from '../fixtures/president';

const view = (sawBy: number[], over = {}) => buildPresidentState({
  phase: 'EXCHANGE_VIEW',
  players: [0, 1, 2, 3, 4].map(seat => player(seat, { sawExchange: sawBy.includes(seat), bot: seat === 3 || undefined })),
  ...over,
});

describe('waitingFor', () => {
  it('givenTheView_thenTheHumansOtherThanMeWhoHaveNotSeenItInSeatOrder', () => {
    expect(waitingFor(view([0]), 0)).toEqual([1, 2, 4]);
    expect(waitingFor(view([0, 2]), 0)).toEqual([1, 4]);
  });

  it('givenABot_thenItIsNeverWaitedFor', () => {
    expect(waitingFor(view([]), 0)).not.toContain(3);
  });

  it('givenMeNotYetAcknowledged_thenIAmNotInTheList', () => {
    expect(waitingFor(view([]), 1)).toEqual([0, 2, 4]);
  });

  it('givenEveryoneHasSeenIt_thenNobodyIsWaitedFor', () => {
    expect(waitingFor(view([0, 1, 2, 3, 4]), 0)).toEqual([]);
  });

  it('givenAnyOtherPhase_thenNobodyIsWaitedFor', () => {
    expect(waitingFor(view([], { phase: 'PLAYING' }), 0)).toEqual([]);
    expect(waitingFor(view([], { phase: 'EXCHANGE' }), 0)).toEqual([]);
  });
});

describe('exchangeViewStatus', () => {
  it('givenIHaveNotAcknowledged_thenIAmWatching', () => {
    expect(exchangeViewStatus(view([]), 0)).toEqual({ kind: 'watching' });
  });

  it('givenIHaveAcknowledgedAndOthersRemain_thenIAmWaitingForThem', () => {
    expect(exchangeViewStatus(view([0]), 0)).toEqual({ kind: 'waiting', seats: [1, 2, 4] });
  });

  it('givenIHaveAcknowledgedAndNobodyRemains_thenTheFirstTrickStarts', () => {
    expect(exchangeViewStatus(view([0, 1, 2, 3, 4]), 0)).toEqual({ kind: 'starting' });
  });

  it('givenAnotherPhase_thenThereIsNoStatus', () => {
    expect(exchangeViewStatus(view([], { phase: 'PLAYING' }), 0)).toBeNull();
  });
});

describe('ackDelayMs', () => {
  it('givenNormalSpeed_thenTheFlightsEndPlusThreeSeconds', () => {
    expect(SUMMARY_READ_MS).toBe(3000);
    expect(ackDelayMs({ flightsEndMs: 2400, factor: 1 })).toBe(5400);
  });

  it('givenFastSpeed_thenTheSummaryIsReadInHalfTheTime', () => {
    expect(ackDelayMs({ flightsEndMs: 1200, factor: 0.5 })).toBe(2700);
  });

  it('givenInstant_thenAtOnceWhateverTheFlights', () => {
    expect(ackDelayMs({ flightsEndMs: 1500, factor: 0 })).toBe(0);
  });

  it('givenAReload_thenOnlyTheSummaryTimeRemains', () => {
    expect(ackDelayMs({ flightsEndMs: 0, factor: 1 })).toBe(3000);
    expect(ackDelayMs({ flightsEndMs: 0, factor: 0.5 })).toBe(1500);
  });
});
