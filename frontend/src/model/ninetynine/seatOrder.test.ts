import { describe, it, expect } from 'vitest';
import { opponentsAfterMe } from './seatOrder';
import { buildNinetyNineTwelveSeatRound } from '../fixtures/ninetynine';

describe('opponentsAfterMe', () => {
  const players = buildNinetyNineTwelveSeatRound().players;

  it('lists the 11 opponents in seat order starting just after me, eliminated ones included', () => {
    const seats = opponentsAfterMe(players, 3).map(p => p.seat);
    expect(seats).toEqual([4, 5, 6, 7, 8, 9, 10, 11, 0, 1, 2]);
  });

  it('excludes me and keeps the list as is when I am no longer seated', () => {
    expect(opponentsAfterMe(players, 0).map(p => p.seat)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]);
    expect(opponentsAfterMe(players, 40)).toHaveLength(12);
  });
});
