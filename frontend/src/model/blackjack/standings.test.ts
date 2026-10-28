import { describe, it, expect } from 'vitest';
import { standingsOf } from './standings';
import { blackjackState, blackjackTournamentEndState, blackjackPlayingState } from './fixtures';
import type { BlackjackSeat } from './BlackjackState';

const seatOf = (n: number, overrides: Partial<BlackjackSeat> = {}): BlackjackSeat => ({
  ...blackjackState().players[0], seat: n, name: `P${n}`, ...overrides,
});

describe('standingsOf', () => {
  it('orders live seats by stack, more first', () => {
    const rows = standingsOf(blackjackPlayingState.players);
    expect(rows.map(r => r.player.seat)).toEqual([1, 0, 2, 3]);
    expect(rows.map(r => r.place)).toEqual([1, 2, 3, 4]);
    expect(rows.every(r => !r.shared && r.status === 'live')).toBe(true);
  });

  it('shares the place of equal stacks and skips the next one', () => {
    const rows = standingsOf(blackjackTournamentEndState.players);
    expect(rows.map(r => [r.player.seat, r.place, r.shared])).toEqual([
      [0, 1, true], [1, 1, true], [2, 3, false], [3, 4, false],
    ]);
  });

  it('puts the eliminated below the live seats, the later elimination first', () => {
    const rows = standingsOf([
      seatOf(0, { stack: 0, eliminated: true, place: 4 }),
      seatOf(1, { stack: 50 }),
      seatOf(2, { stack: 0, eliminated: true, place: 3 }),
      seatOf(3, { stack: 150 }),
    ]);
    expect(rows.map(r => [r.player.seat, r.place, r.status])).toEqual([
      [3, 1, 'live'], [1, 2, 'live'], [2, 3, 'eliminated'], [0, 4, 'eliminated'],
    ]);
  });

  it('puts the forfeited at the very bottom', () => {
    const rows = standingsOf([
      seatOf(0, { stack: 80, forfeited: true, eliminated: true, place: 3 }),
      seatOf(1, { stack: 0, eliminated: true, place: 2 }),
      seatOf(2, { stack: 120 }),
    ]);
    expect(rows.map(r => [r.player.seat, r.place, r.status])).toEqual([
      [2, 1, 'live'], [1, 2, 'eliminated'], [0, 3, 'forfeited'],
    ]);
  });

  it('shares an eliminated place when the server did', () => {
    const rows = standingsOf([
      seatOf(0, { stack: 90 }),
      seatOf(1, { stack: 0, eliminated: true, place: 2 }),
      seatOf(2, { stack: 0, eliminated: true, place: 2 }),
    ]);
    expect(rows.map(r => [r.player.seat, r.place, r.shared])).toEqual([[0, 1, false], [1, 2, true], [2, 2, true]]);
  });

  it('is one row alone in solo', () => {
    const rows = standingsOf([seatOf(0, { stack: 123 })]);
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ place: 1, shared: false, status: 'live' });
  });
});
