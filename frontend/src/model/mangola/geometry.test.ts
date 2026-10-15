import { describe, it, expect } from 'vitest';
import { COLUMNS, PITS, loopOf, nextPit, pitFromName, pitName, sideOfPit } from './geometry';

describe('Mangola geometry', () => {
  it('names the pits a1..h4, row number from 1 and column letter from a', () => {
    expect(pitName(0)).toBe('a1');
    expect(pitName(7)).toBe('h1');
    expect(pitName(8)).toBe('a2');
    expect(pitName(31)).toBe('h4');
    expect(COLUMNS).toBe(8);
    expect(PITS).toBe(32);
  });

  it('reads a name back to its pit, and refuses what is not a pit', () => {
    for (let pit = 0; pit < PITS; pit++) expect(pitFromName(pitName(pit))).toBe(pit);
    expect(pitFromName('i1')).toBeNull();
    expect(pitFromName('a5')).toBeNull();
    expect(pitFromName('a')).toBeNull();
  });

  it('gives the owner of a pit: rows 1 and 2 are side A, rows 3 and 4 side B', () => {
    expect(sideOfPit(0)).toBe('A');
    expect(sideOfPit(15)).toBe('A');
    expect(sideOfPit(16)).toBe('B');
    expect(sideOfPit(31)).toBe('B');
  });

  it('the loop of side A is the inner row left to right, then the outer row right to left', () => {
    expect(loopOf('A')).toEqual([8, 9, 10, 11, 12, 13, 14, 15, 7, 6, 5, 4, 3, 2, 1, 0]);
  });

  it('the loop of side B is the same path turned by half a turn: row 4 left to right, then row 3 right to left', () => {
    expect(loopOf('B')).toEqual([24, 25, 26, 27, 28, 29, 30, 31, 23, 22, 21, 20, 19, 18, 17, 16]);
  });

  it('next follows the loop of the pit owner and closes it', () => {
    expect(nextPit(8)).toBe(9);
    expect(nextPit(15)).toBe(7);
    expect(nextPit(0)).toBe(8);
    expect(nextPit(31)).toBe(23);
    expect(nextPit(16)).toBe(24);
  });
});
