import { describe, it, expect } from 'vitest';
import { opponentsAfterMe, seatsAtTable, seatsInTurnOrder } from './seatOrder';
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

describe('seatsAtTable', () => {
  const players = buildNinetyNineTwelveSeatRound().players;

  it('puts me first, then the opponents in seat order', () => {
    expect(seatsAtTable(players, 3).map(p => p.seat)).toEqual([3, 4, 5, 6, 7, 8, 9, 10, 11, 0, 1, 2]);
  });

  it('keeps the seat order when I am not seated', () => {
    expect(seatsAtTable(players, 40).map(p => p.seat)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11]);
  });
});

describe('seatsInTurnOrder', () => {
  const players = buildNinetyNineTwelveSeatRound().players.slice(0, 5);

  it('follows the seat order while the direction is normal', () => {
    expect(seatsInTurnOrder(players, 0, 'CLOCKWISE').map(p => p.seat)).toEqual([0, 1, 2, 3, 4]);
  });

  it('reads the opponents the other way once the direction is reversed', () => {
    expect(seatsInTurnOrder(players, 0, 'COUNTER_CLOCKWISE').map(p => p.seat)).toEqual([0, 4, 3, 2, 1]);
  });

  it('starts from my seat whatever it is', () => {
    expect(seatsInTurnOrder(players, 2, 'COUNTER_CLOCKWISE').map(p => p.seat)).toEqual([2, 1, 0, 4, 3]);
  });
});
