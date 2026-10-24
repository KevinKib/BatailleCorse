import { describe, it, expect } from 'vitest';
import { DOT_SIZE, MAX_DOTS, arrowOf, rowsWithBoard, seedLayout, showsCount, sideOfSeat, viewerRows } from './board';
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
  it('takes the game direction and ignores it for now (the screen mapping follows in a later chunk)', () => {
    expect(arrowOf(8, 'A', 'clockwise')).toBe('right');
    expect(arrowOf(8, 'A', 'anticlockwise')).toBe('right');
  });

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

describe('seedLayout', () => {
  const dist = (a: { x: number; y: number }, b: { x: number; y: number }) => Math.hypot(a.x - b.x, a.y - b.y);

  it('draws no seed for an empty or a hidden pit', () => {
    expect(seedLayout(0)).toEqual([]);
    expect(seedLayout(null)).toEqual([]);
  });

  it('draws exactly one seed per seed, for 1 to 64', () => {
    for (let n = 1; n <= 64; n++) expect(seedLayout(n)).toHaveLength(n);
  });

  it('keeps the dice layout up to eight seeds, all of the same size', () => {
    expect(MAX_DOTS).toBe(8);
    expect(seedLayout(1)).toEqual([{ x: 0.5, y: 0.5, size: DOT_SIZE }]);
    for (let n = 1; n <= MAX_DOTS; n++) {
      expect(new Set(seedLayout(n).map(p => p.size))).toEqual(new Set([DOT_SIZE]));
    }
    // 7 and 8 differ, as dice do.
    expect(seedLayout(7)).not.toEqual(seedLayout(8).slice(0, 7));
  });

  it('is deterministic: the same count always draws the same', () => {
    for (const n of [3, 9, 12, 30, 64]) expect(seedLayout(n)).toEqual(seedLayout(n));
  });

  it('keeps every seed inside the bowl, for 1 to 64', () => {
    for (let n = 1; n <= 64; n++) {
      for (const p of seedLayout(n)) {
        expect(Math.hypot(p.x - 0.5, p.y - 0.5) + p.size / 2).toBeLessThanOrEqual(0.5);
      }
    }
  });

  it('keeps seeds from piling on each other: no overlap beyond the pile rule, for 1 to 64', () => {
    for (let n = 2; n <= 64; n++) {
      const seeds = seedLayout(n);
      for (let i = 0; i < seeds.length; i++) {
        for (let j = i + 1; j < seeds.length; j++) {
          const smaller = Math.min(seeds[i].size, seeds[j].size);
          expect(dist(seeds[i], seeds[j])).toBeGreaterThanOrEqual(smaller * 0.8);
        }
      }
    }
  });

  it('shrinks the seeds only past 16 and past 32 seeds', () => {
    const sizeOf = (n: number) => new Set(seedLayout(n).map(p => p.size)).size === 1 ? seedLayout(n)[0].size : NaN;
    for (let n = 9; n <= 16; n++) expect(sizeOf(n)).toBe(sizeOf(9));
    for (let n = 17; n <= 32; n++) expect(sizeOf(n)).toBe(sizeOf(17));
    for (let n = 33; n <= 64; n++) expect(sizeOf(n)).toBe(sizeOf(33));
    expect(sizeOf(17)).toBeLessThan(sizeOf(16));
    expect(sizeOf(33)).toBeLessThan(sizeOf(32));
    expect(sizeOf(9)).toBeLessThan(DOT_SIZE);
  });

  it('never moves a seed when one lands, except at the 9th seed and at the two shrinks (16 to 17, 32 to 33)', () => {
    for (let n = 9; n < 64; n++) {
      if (n === 16 || n === 32) continue;
      expect(seedLayout(n + 1).slice(0, n)).toEqual(seedLayout(n));
    }
  });

  it('fills the bowl at 16, 32 and 64 seeds without leaving the centre empty', () => {
    for (const n of [16, 32, 64]) {
      const nearest = Math.min(...seedLayout(n).map(p => dist(p, { x: 0.5, y: 0.5 })));
      expect(nearest).toBeLessThan(0.08);
    }
  });
});

describe('badge', () => {
  it('shows the exact count on every non-empty pit and nothing otherwise', () => {
    expect(showsCount(1)).toBe(true);
    expect(showsCount(17)).toBe(true);
    expect(showsCount(0)).toBe(false);
    expect(showsCount(null)).toBe(false);
  });
});
describe('rowsWithBoard', () => {
  it('shows the counts of another board on the same cells, leaving the rest untouched', () => {
    const rows = viewerRows(mangolaState(), 0);
    const shown = Array.from({ length: 32 }, (_, pit) => pit);
    const out = rowsWithBoard(rows, shown);
    for (const cell of out.flat()) expect(cell.count).toBe(cell.pit);
    const before = rows.flat().find(c => c.pit === 8)!;
    const after = out.flat().find(c => c.pit === 8)!;
    expect({ ...after, count: before.count }).toEqual(before);
  });

  it('returns the rows as they are without a board', () => {
    const rows = viewerRows(mangolaState(), 0);
    expect(rowsWithBoard(rows, null)).toBe(rows);
  });
});
