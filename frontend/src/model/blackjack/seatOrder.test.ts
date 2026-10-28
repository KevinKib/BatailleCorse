import { describe, it, expect } from 'vitest';
import { opponentsOf } from './seatOrder';
import { blackjackBettingState } from './fixtures';

describe('opponentsOf', () => {
  const players = blackjackBettingState.players;

  it('lists the others in seat order after me, wrapping', () => {
    expect(opponentsOf(players, 0).map(p => p.seat)).toEqual([1, 2, 3]);
    expect(opponentsOf(players, 2).map(p => p.seat)).toEqual([3, 0, 1]);
    expect(opponentsOf(players, 3).map(p => p.seat)).toEqual([0, 1, 2]);
  });

  it('is empty in solo', () => {
    expect(opponentsOf([players[0]], 0)).toEqual([]);
  });

  it('keeps everyone when I am not seated', () => {
    expect(opponentsOf(players, 9).map(p => p.seat)).toEqual([0, 1, 2, 3]);
  });
});
