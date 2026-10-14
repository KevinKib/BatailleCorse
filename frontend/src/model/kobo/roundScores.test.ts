import { describe, it, expect } from 'vitest';
import { finalStandings, roundRows, roundVerdict, resetFrom, winnersLowestTotal } from './roundScores';
import type { KoboRound, KoboRoundPlayer } from './KoboState';
import { buildKoboPlayer } from '../fixtures/kobo';

function rp(seat: number, overrides: Partial<KoboRoundPlayer> = {}): KoboRoundPlayer {
  return { seat, handValue: 10, roundPoints: 10, totalBefore: 0, totalAfter: 10, reset: false, ...overrides };
}

function round(overrides: Partial<KoboRound> = {}): KoboRound {
  return { players: [rp(0), rp(1)], announcer: null, announcerWon: false, giftTo: null, ...overrides };
}

describe('roundRows', () => {
  it('givenARound_thenOneRowPerPlayerInSeatOrderWithTheServerNumbers', () => {
    const rows = roundRows(round({ players: [rp(2, { handValue: 5, roundPoints: 5, totalAfter: 5 }), rp(0)] }));
    expect(rows.map(r => r.seat)).toEqual([0, 2]);
    expect(rows[1]).toMatchObject({ seat: 2, handValue: 5, roundPoints: 5, totalBefore: 0, totalAfter: 5 });
  });

  it('givenAnAnnouncerAndAGift_thenOnlyTheAnnouncerAndTheReceiverAreMarked', () => {
    const rows = roundRows(round({ players: [rp(0), rp(1), rp(2)], announcer: 0, announcerWon: true, giftTo: 2 }));
    expect(rows.map(r => [r.seat, r.announcer, r.gift])).toEqual([[0, true, false], [1, false, false], [2, false, true]]);
  });

  it('givenNoAnnouncer_thenNobodyIsMarked', () => {
    expect(roundRows(round()).every(r => !r.announcer && !r.gift)).toBe(true);
  });
});

describe('resetFrom', () => {
  it('givenAResetWithoutGift_thenTheTotalReachedBeforeTheResetIsBeforePlusRoundPoints', () => {
    const player = rp(1, { totalBefore: 60, roundPoints: 15, totalAfter: 50, reset: true });
    expect(resetFrom(player, false)).toBe(75);
  });

  it('givenAResetOfTheReceiverOfTheGift_thenTheGiftCountsInTheTotalReached', () => {
    const player = rp(1, { totalBefore: 60, roundPoints: 5, totalAfter: 50, reset: true });
    expect(resetFrom(player, true)).toBe(75);
  });

  it('givenNoReset_thenNull', () => {
    expect(resetFrom(rp(1), false)).toBeNull();
  });
});

describe('roundVerdict', () => {
  it('givenAnAnnouncerWhoWon_thenAnnouncerWon', () => {
    expect(roundVerdict(round({ announcer: 1, announcerWon: true, giftTo: 0 }))).toBe('won');
  });

  it('givenAnAnnouncerWhoDidNotHaveTheFewest_thenLost', () => {
    expect(roundVerdict(round({ announcer: 1, announcerWon: false }))).toBe('lost');
  });

  it('givenNoAnnouncer_thenNone', () => {
    expect(roundVerdict(round())).toBe('none');
  });
});

describe('finalStandings', () => {
  const player = (seat: number, total: number) => buildKoboPlayer(seat, { total });

  it('givenTotals_thenSortedLowestFirstWithRanks', () => {
    const rows = finalStandings([player(0, 80), player(1, 20), player(2, 55)]);
    expect(rows.map(r => [r.seat, r.rank])).toEqual([[1, 1], [2, 2], [0, 3]]);
  });

  it('givenATie_thenTheTiedPlayersShareTheRankAndTheNextOneSkips', () => {
    const rows = finalStandings([player(0, 30), player(1, 30), player(2, 40)]);
    expect(rows.map(r => r.rank)).toEqual([1, 1, 3]);
  });

  it('givenATotalOverOneHundred_thenItIsFlaggedOver', () => {
    const rows = finalStandings([player(0, 101), player(1, 100), player(2, 12)]);
    expect(rows.map(r => [r.seat, r.over])).toEqual([[2, false], [1, false], [0, true]]);
  });

  it('givenTheSameTotals_thenSeatOrderBreaksTheTie', () => {
    expect(finalStandings([player(3, 10), player(1, 10)]).map(r => r.seat)).toEqual([1, 3]);
  });
});

describe('winnersLowestTotal', () => {
  it('givenTheWinners_thenTheLowestTotalIsTheirs', () => {
    const players = [buildKoboPlayer(0, { total: 70 }), buildKoboPlayer(1, { total: 41 }), buildKoboPlayer(2, { total: 41 })];
    expect(winnersLowestTotal(players, [1, 2])).toBe(41);
  });

  it('givenNoWinnerAmongThePlayers_thenNull', () => {
    expect(winnersLowestTotal([buildKoboPlayer(0, { total: 3 })], [4])).toBeNull();
  });
});
