import { describe, it, expect } from 'vitest';
import { MAX_DOTS, arrowOf, seedDisplay, sideOfSeat, viewerRows } from './board';
import { mangolaState, placementState } from './mangolaFixtures';

describe('sideOfSeat', () => {
  it('seatA owns side A, the other seat side B', () => {
    const state = mangolaState({ seatA: 1 });
    expect(sideOfSeat(state, 1)).toBe('A');
    expect(sideOfSeat(state, 0)).toBe('B');
  });
});

describe('viewerRows', () => {
  it('puts side A at the bottom for its seat: row 4 on top, row 1 at the bottom, columns a to h', () => {
    const rows = viewerRows(mangolaState(), 0);
    expect(rows).toHaveLength(4);
    expect(rows.map(r => r.map(c => c.pit))).toEqual([
      [24, 25, 26, 27, 28, 29, 30, 31],
      [16, 17, 18, 19, 20, 21, 22, 23],
      [8, 9, 10, 11, 12, 13, 14, 15],
      [0, 1, 2, 3, 4, 5, 6, 7],
    ]);
    expect(rows[3].map(c => c.name)).toEqual(['a1', 'b1', 'c1', 'd1', 'e1', 'f1', 'g1', 'h1']);
  });

  it('turns the board by half a turn for the other seat: its side at the bottom, columns reversed', () => {
    const rows = viewerRows(mangolaState(), 1);
    expect(rows.map(r => r.map(c => c.pit))).toEqual([
      [7, 6, 5, 4, 3, 2, 1, 0],
      [15, 14, 13, 12, 11, 10, 9, 8],
      [23, 22, 21, 20, 19, 18, 17, 16],
      [31, 30, 29, 28, 27, 26, 25, 24],
    ]);
  });

  it('seatA = 1 puts the seat 1 at the bottom the way seat 0 is when it owns side A', () => {
    const rows = viewerRows(mangolaState({ seatA: 1 }), 1);
    expect(rows[3].map(c => c.pit)).toEqual([0, 1, 2, 3, 4, 5, 6, 7]);
  });

  it('marks my pits and the inner rows, and carries the seed count of each', () => {
    const rows = viewerRows(mangolaState(), 0);
    expect(rows.map(r => r[0].mine)).toEqual([false, false, true, true]);
    expect(rows.map(r => r[0].inner)).toEqual([false, true, true, false]);
    expect(rows[3][1].count).toBe(4);
    expect(rows[3][0].count).toBe(0);
  });

  it('keeps the opponent seeds unknown (null) during a hidden placement', () => {
    const rows = viewerRows(placementState([2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2]), 0);
    expect(rows[0].every(c => c.count === null)).toBe(true);
    expect(rows[1].every(c => c.count === null)).toBe(true);
    expect(rows[2].every(c => c.count === 2)).toBe(true);
    expect(rows[3].every(c => c.count === 2)).toBe(true);
  });

  it('marks the legal pits of the viewer', () => {
    const rows = viewerRows(mangolaState({ legalPits: [8, 1] }), 0);
    const legal = rows.flat().filter(c => c.legal).map(c => c.pit).sort((a, b) => a - b);
    expect(legal).toEqual([1, 8]);
  });
});

describe('arrowOf (the direction a seed travels on screen)', () => {
  it('side A seen from below: the inner row goes right, turns down at h, the outer row goes left, turns up at a', () => {
    expect(arrowOf(8, 'A')).toBe('right');
    expect(arrowOf(14, 'A')).toBe('right');
    expect(arrowOf(15, 'A')).toBe('down');
    expect(arrowOf(7, 'A')).toBe('left');
    expect(arrowOf(1, 'A')).toBe('left');
    expect(arrowOf(0, 'A')).toBe('up');
  });

  it('side B seen from below (turned): the same shape, so its row 4 goes left on screen', () => {
    // viewer is on side B: the display is turned, columns reversed.
    expect(arrowOf(24, 'B')).toBe('left');
    expect(arrowOf(31, 'B')).toBe('up');
    expect(arrowOf(23, 'B')).toBe('right');
    expect(arrowOf(16, 'B')).toBe('down');
  });

  it('the opponent loop is seen from the viewer: its arrows are the half-turn of its own', () => {
    // viewer A looks at side B at the top: row 4 left to right in board terms is left to right on screen.
    expect(arrowOf(24, 'A')).toBe('right');
    expect(arrowOf(31, 'A')).toBe('down');
    expect(arrowOf(16, 'A')).toBe('up');
  });
});

describe('seedDisplay', () => {
  it('shows exact dots up to eight seeds', () => {
    expect(MAX_DOTS).toBe(8);
    for (let n = 0; n <= 8; n++) expect(seedDisplay(n)).toEqual({ kind: 'dots', count: n });
  });

  it('writes the number above eight', () => {
    expect(seedDisplay(9)).toEqual({ kind: 'number', count: 9 });
    expect(seedDisplay(41)).toEqual({ kind: 'number', count: 41 });
  });

  it('shows nothing for a hidden pit', () => {
    expect(seedDisplay(null)).toEqual({ kind: 'hidden' });
  });
});
