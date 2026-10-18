import { describe, it, expect } from 'vitest';
import {
  GEOMETRIES, geometryOf, neighbourByArrow, neighbourByPage, segmentsOnLine, variantKeyOf, VARIANT_KEYS,
} from './geometries';
import { morrisState } from './morrisFixtures';

describe('variants', () => {
  it('lists Tapatan first (the default), Achi and Picaria', () => {
    expect(VARIANT_KEYS).toEqual(['tapatan', 'achi', 'picaria']);
  });

  it('reads an absent variant as Tapatan, as the server omits it for the default', () => {
    expect(variantKeyOf(morrisState())).toBe('tapatan');
    expect(variantKeyOf(morrisState({ variant: 'achi' }))).toBe('achi');
    expect(variantKeyOf(morrisState({ variant: 'picaria' }))).toBe('picaria');
  });

  it('falls back to Tapatan for a variant it does not know', () => {
    expect(variantKeyOf(morrisState({ variant: 'nine-men' as never }))).toBe('tapatan');
  });
});

describe('geometries', () => {
  it('places the nine points of Tapatan and Achi on a 3x3 grid in a 0-100 box, row by row', () => {
    for (const key of ['tapatan', 'achi'] as const) {
      const { points } = GEOMETRIES[key];
      expect(points, key).toHaveLength(9);
      expect(points[0].x).toBeCloseTo(16.67, 1);
      expect(points[0].y).toBeCloseTo(16.67, 1);
      expect(points[4]).toEqual({ x: 50, y: 50 });
      expect(points[8].x).toBeCloseTo(83.33, 1);
      expect(points[8].y).toBeCloseTo(83.33, 1);
      expect(points[5].x).toBeCloseTo(83.33, 1);
      expect(points[5].y).toBe(50);
    }
  });

  it('draws the eight lines for Tapatan and Achi: three rows, three columns, two diagonals through the centre', () => {
    for (const key of ['tapatan', 'achi'] as const) {
      const { segments } = GEOMETRIES[key];
      expect(segments, key).toHaveLength(8);
      expect(segments).toContainEqual([0, 1, 2]);
      expect(segments).toContainEqual([1, 4, 7]);
      expect(segments).toContainEqual([0, 4, 8]);
      expect(segments).toContainEqual([2, 4, 6]);
    }
  });

  it('places Picaria on 13 points: the nine of the grid, then the four crossings of the diamond with the diagonals', () => {
    const { points } = GEOMETRIES.picaria;
    expect(points).toHaveLength(13);
    expect(points[0].x).toBeCloseTo(16.67, 1);
    expect(points[4]).toEqual({ x: 50, y: 50 });
    expect(points[8].x).toBeCloseTo(83.33, 1);
    // 9 sits between 0 and 4, 10 between 2 and 4, 11 between 8 and 4, 12 between 6 and 4.
    expect(points[9].x).toBeCloseTo(33.33, 1);
    expect(points[9].y).toBeCloseTo(33.33, 1);
    expect(points[10].x).toBeCloseTo(66.67, 1);
    expect(points[10].y).toBeCloseTo(33.33, 1);
    expect(points[12].x).toBeCloseTo(33.33, 1);
    expect(points[12].y).toBeCloseTo(66.67, 1);
    expect(points[11].x).toBeCloseTo(66.67, 1);
    expect(points[11].y).toBeCloseTo(66.67, 1);
  });

  it('keeps every Picaria point more than 23.5% of the board from the others, so a 23% target never overlaps', () => {
    const { points } = GEOMETRIES.picaria;
    points.forEach((a, i) => {
      points.forEach((b, j) => {
        if (i !== j) expect(Math.hypot(a.x - b.x, a.y - b.y), `${i}-${j}`).toBeGreaterThan(23.5);
      });
    });
  });

  it("splits Picaria's segments at the crossings: 28 two-point edges, none passing through a point", () => {
    const { segments } = GEOMETRIES.picaria;
    expect(segments).toHaveLength(28);
    segments.forEach((s) => expect(s, s.join('-')).toHaveLength(2));
    // The long diagonals 0-4-8 and 2-4-6 run through the crossings.
    for (const edge of [[0, 9], [9, 4], [4, 11], [11, 8], [2, 10], [10, 4], [4, 12], [12, 6]]) {
      expect(segments).toContainEqual(edge);
    }
    // The diamond of the middles of the sides is split at its four crossings too.
    for (const edge of [[1, 9], [9, 3], [1, 10], [10, 5], [5, 11], [11, 7], [7, 12], [12, 3]]) {
      expect(segments).toContainEqual(edge);
    }
    // Rows and columns are kept, cut at the grid points.
    for (const edge of [[0, 1], [1, 2], [3, 4], [4, 5], [6, 7], [7, 8], [0, 3], [3, 6], [1, 4], [4, 7], [2, 5], [5, 8]]) {
      expect(segments).toContainEqual(edge);
    }
    expect(segments).not.toContainEqual([0, 4]);
    expect(segments).not.toContainEqual([1, 3]);
  });

  it("names Picaria's crossings, the point closed while placing and the refined look; Tapatan and Achi have none", () => {
    expect(GEOMETRIES.picaria.crossings).toEqual([9, 10, 11, 12]);
    expect(GEOMETRIES.picaria.closedWhilePlacing).toEqual([4]);
    expect(GEOMETRIES.picaria.refined).toBe(true);
    for (const key of ['tapatan', 'achi'] as const) {
      expect(GEOMETRIES[key].crossings, key).toEqual([]);
      expect(GEOMETRIES[key].closedWhilePlacing, key).toEqual([]);
      expect(GEOMETRIES[key].refined, key).toBe(false);
    }
  });

  it('holds the pieces each player has: 3, 4 for Achi', () => {
    expect(GEOMETRIES.tapatan.pieces).toBe(3);
    expect(GEOMETRIES.achi.pieces).toBe(4);
    expect(GEOMETRIES.picaria.pieces).toBe(3);
  });

  it('finds the geometry of a state from its variant, Tapatan when it is absent', () => {
    expect(geometryOf(morrisState())).toBe(GEOMETRIES.tapatan);
    expect(geometryOf(morrisState({ variant: 'picaria' }))).toBe(GEOMETRIES.picaria);
  });
});

describe('arrow navigation', () => {
  const tapatan = GEOMETRIES.tapatan;

  it('moves between points with the arrows and stops at the edges', () => {
    expect(neighbourByArrow(tapatan, 4, 'ArrowUp')).toBe(1);
    expect(neighbourByArrow(tapatan, 4, 'ArrowDown')).toBe(7);
    expect(neighbourByArrow(tapatan, 4, 'ArrowLeft')).toBe(3);
    expect(neighbourByArrow(tapatan, 4, 'ArrowRight')).toBe(5);
    expect(neighbourByArrow(tapatan, 0, 'ArrowUp')).toBeNull();
    expect(neighbourByArrow(tapatan, 0, 'ArrowLeft')).toBeNull();
    expect(neighbourByArrow(tapatan, 8, 'ArrowDown')).toBeNull();
    expect(neighbourByArrow(tapatan, 2, 'ArrowRight')).toBeNull();
    expect(neighbourByArrow(tapatan, 4, 'Enter')).toBeNull();
  });

  it('goes to the nearest point in the direction of the arrow, whatever the variant', () => {
    for (const key of VARIANT_KEYS) {
      const g = GEOMETRIES[key];
      expect(neighbourByArrow(g, 0, 'ArrowRight'), key).toBe(1);
      expect(neighbourByArrow(g, 1, 'ArrowRight'), key).toBe(2);
      expect(neighbourByArrow(g, 0, 'ArrowDown'), key).toBe(3);
      expect(neighbourByArrow(g, 7, 'ArrowUp'), key).toBe(4);
      expect(neighbourByArrow(g, 5, 'ArrowLeft'), key).toBe(4);
    }
  });
});

describe('arrow navigation on the 13 points of Picaria', () => {
  const g = GEOMETRIES.picaria;

  it('keeps the arrows on the lines of the grid, and moves between the crossings along their rows and columns', () => {
    expect(neighbourByArrow(g, 4, 'ArrowUp')).toBe(1);
    expect(neighbourByArrow(g, 0, 'ArrowRight')).toBe(1);
    expect(neighbourByArrow(g, 9, 'ArrowRight')).toBe(10);
    expect(neighbourByArrow(g, 10, 'ArrowLeft')).toBe(9);
    expect(neighbourByArrow(g, 9, 'ArrowDown')).toBe(12);
    expect(neighbourByArrow(g, 11, 'ArrowUp')).toBe(10);
    expect(neighbourByArrow(g, 11, 'ArrowLeft')).toBe(12);
  });

  it('steps through the points in order with Page Down and Page Up, so that every crossing is reachable', () => {
    expect(neighbourByPage(g, 8, 'PageDown')).toBe(9);
    expect(neighbourByPage(g, 9, 'PageUp')).toBe(8);
    expect(neighbourByPage(g, 12, 'PageDown')).toBeNull();
    expect(neighbourByPage(g, 0, 'PageUp')).toBeNull();
    expect(neighbourByPage(g, 0, 'Enter')).toBeNull();
  });
});

describe('segmentsOnLine', () => {
  const key = (s: readonly number[]) => s.join('-');

  it('finds the drawn segment of a three-point line on Tapatan', () => {
    expect(segmentsOnLine(GEOMETRIES.tapatan, [2, 4, 6]).map(key)).toEqual(['2-4-6']);
    expect(segmentsOnLine(GEOMETRIES.tapatan, [3, 4, 5]).map(key)).toEqual(['3-4-5']);
    expect(segmentsOnLine(GEOMETRIES.tapatan, [])).toEqual([]);
  });

  it('finds the two edges of a corner, crossing, centre line on Picaria', () => {
    expect(segmentsOnLine(GEOMETRIES.picaria, [0, 9, 4]).map(key).sort()).toEqual(['0-9', '9-4']);
    expect(segmentsOnLine(GEOMETRIES.picaria, [4, 0, 9]).map(key).sort()).toEqual(['0-9', '9-4']);
  });

  it('finds the edges of a row and of a line of the diamond on Picaria', () => {
    expect(segmentsOnLine(GEOMETRIES.picaria, [0, 1, 2]).map(key).sort()).toEqual(['0-1', '1-2']);
    expect(segmentsOnLine(GEOMETRIES.picaria, [1, 9, 3]).map(key).sort()).toEqual(['1-9', '9-3']);
  });
});
