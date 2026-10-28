import { describe, it, expect } from 'vitest';
import { seatsAtTable, leftNeighbour, rightNeighbour } from './seatOrder';
import { kiloDeMerdeRaceState, kiloDeMerdeStepState } from './fixtures';

const ten = kiloDeMerdeRaceState.players;

describe('seatsAtTable', () => {
  it('puts me first, then my left neighbour and on, in seat order', () => {
    expect(seatsAtTable(ten, 3).map(p => p.seat)).toEqual([3, 4, 5, 6, 7, 8, 9, 0, 1, 2]);
    expect(seatsAtTable(ten, 0).map(p => p.seat)).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
  });

  it('leaves out the seats that forfeited', () => {
    const players = ten.map(p => (p.seat === 5 ? { ...p, active: false } : p));
    expect(seatsAtTable(players, 3).map(p => p.seat)).toEqual([3, 4, 6, 7, 8, 9, 0, 1, 2]);
  });

  it('keeps the seat order when I am not seated', () => {
    expect(seatsAtTable(kiloDeMerdeStepState.players, 40).map(p => p.seat)).toEqual([0, 1, 2]);
  });
});

describe('leftNeighbour and rightNeighbour', () => {
  it('pass to the next active seat, wrapping, and receive from the previous one', () => {
    expect(leftNeighbour(ten, 3)).toBe(4);
    expect(leftNeighbour(ten, 9)).toBe(0);
    expect(rightNeighbour(ten, 0)).toBe(9);
    expect(rightNeighbour(ten, 4)).toBe(3);
  });

  it('skip a forfeited seat', () => {
    const players = ten.map(p => (p.seat === 4 ? { ...p, active: false } : p));
    expect(leftNeighbour(players, 3)).toBe(5);
    expect(rightNeighbour(players, 5)).toBe(3);
  });

  it('are null for a seat that is not at the table', () => {
    expect(leftNeighbour(ten, 40)).toBeNull();
    expect(rightNeighbour(ten, 40)).toBeNull();
  });
});
