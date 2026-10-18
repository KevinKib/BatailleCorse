import { describe, it, expect } from 'vitest';
import {
  GEOMETRIES, geometryOf, neighbourByArrow, neighbourByPage, segmentsOnLine, variantKeyOf, VARIANT_KEYS,
} from './geometries';
import { morrisState } from './morrisFixtures';

describe('variants', () => {
  it('lists Tapatan first (the default), then Achi, then Six, and no Picaria', () => {
    expect(VARIANT_KEYS).toEqual(['tapatan', 'achi', 'six']);
    expect(VARIANT_KEYS).not.toContain('picaria');
  });

  it('reads an absent variant as Tapatan, as the server omits it for the default', () => {
    expect(variantKeyOf(morrisState())).toBe('tapatan');
    expect(variantKeyOf(morrisState({ variant: 'achi' }))).toBe('achi');
    expect(variantKeyOf(morrisState({ variant: 'six' }))).toBe('six');
  });

  it('falls back to Tapatan for a variant it does not know, the removed Picaria included', () => {
    expect(variantKeyOf(morrisState({ variant: 'nine-men' as never }))).toBe('tapatan');
    expect(variantKeyOf(morrisState({ variant: 'picaria' as never }))).toBe('tapatan');
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

  it('has no Picaria geometry, and only Six is refined', () => {
    expect(Object.keys(GEOMETRIES)).toEqual(['tapatan', 'achi', 'six']);
    expect(GEOMETRIES.six.refined).toBe(true);
    for (const key of ['tapatan', 'achi'] as const) {
      expect(GEOMETRIES[key].refined, key).toBe(false);
    }
  });

  it('holds the pieces each player has: 3, 4 for Achi, 6 for Six', () => {
    expect(GEOMETRIES.tapatan.pieces).toBe(3);
    expect(GEOMETRIES.achi.pieces).toBe(4);
    expect(GEOMETRIES.six.pieces).toBe(6);
  });

  it('finds the geometry of a state from its variant, Tapatan when it is absent', () => {
    expect(geometryOf(morrisState())).toBe(GEOMETRIES.tapatan);
    expect(geometryOf(morrisState({ variant: 'achi' }))).toBe(GEOMETRIES.achi);
    expect(geometryOf(morrisState({ variant: 'six' }))).toBe(GEOMETRIES.six);
  });
});

describe('Six Men\'s Morris geometry', () => {
  const six = GEOMETRIES.six;

  it('places 16 points on two squares: outer at 8/50/92, inner at 29/50/71, numbered clockwise from the top left', () => {
    expect(six.points).toHaveLength(16);
    expect(six.points.slice(0, 8)).toEqual([
      { x: 8, y: 8 }, { x: 50, y: 8 }, { x: 92, y: 8 }, { x: 92, y: 50 },
      { x: 92, y: 92 }, { x: 50, y: 92 }, { x: 8, y: 92 }, { x: 8, y: 50 },
    ]);
    expect(six.points.slice(8)).toEqual([
      { x: 29, y: 29 }, { x: 50, y: 29 }, { x: 71, y: 29 }, { x: 71, y: 50 },
      { x: 71, y: 71 }, { x: 50, y: 71 }, { x: 29, y: 71 }, { x: 29, y: 50 },
    ]);
  });

  it('draws 20 segments: each of the 8 sides cut at its middle, and the 4 joins; no diagonal', () => {
    expect(six.segments).toHaveLength(20);
    expect(six.segments.every((s) => s.length === 2)).toBe(true);
    // Every segment joins two points of the same row or column (no diagonal) and no two segments repeat.
    for (const [a, b] of six.segments) {
      const [p, q] = [six.points[a], six.points[b]];
      expect(p.x === q.x || p.y === q.y, `${a}-${b}`).toBe(true);
    }
    expect(new Set(six.segments.map((s) => [...s].sort((a, b) => a - b).join('-'))).size).toBe(20);
    for (const join of [[1, 9], [3, 11], [5, 13], [7, 15]]) expect(six.segments).toContainEqual(join);
  });

  it('gives a corner two segments and a middle three', () => {
    const degree = (n: number) => six.segments.filter((s) => s.includes(n)).length;
    for (const corner of [0, 2, 4, 6, 8, 10, 12, 14]) expect(degree(corner), `corner ${corner}`).toBe(2);
    for (const middle of [1, 3, 5, 7, 9, 11, 13, 15]) expect(degree(middle), `middle ${middle}`).toBe(3);
  });

  it('is reachable by the arrows: from every point, every other can be visited', () => {
    const arrows = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'];
    for (let start = 0; start < 16; start++) {
      const seen = new Set([start]);
      const queue = [start];
      while (queue.length) {
        const at = queue.shift()!;
        for (const key of arrows) {
          const next = neighbourByArrow(six, at, key);
          if (next !== null && !seen.has(next)) { seen.add(next); queue.push(next); }
        }
      }
      expect([...seen].sort((a, b) => a - b), `from ${start}`).toEqual(Array.from({ length: 16 }, (_, n) => n));
    }
  });

  it('is reachable by Page Down from the first point and by Page Up from the last, in the server numbering', () => {
    let at: number | null = 0;
    const down: number[] = [];
    while (at !== null) { down.push(at); at = neighbourByPage(six, at, 'PageDown'); }
    expect(down).toEqual(Array.from({ length: 16 }, (_, n) => n));
    expect(neighbourByPage(six, 15, 'PageDown')).toBeNull();
    expect(neighbourByPage(six, 15, 'PageUp')).toBe(14);
  });

  it('moves the arrows along the sides and across the joins', () => {
    expect(neighbourByArrow(six, 0, 'ArrowRight')).toBe(1);
    expect(neighbourByArrow(six, 1, 'ArrowDown')).toBe(9);
    expect(neighbourByArrow(six, 9, 'ArrowDown')).toBe(13);
    expect(neighbourByArrow(six, 7, 'ArrowRight')).toBe(15);
    expect(neighbourByArrow(six, 0, 'ArrowUp')).toBeNull();
  });

  it('starts the keyboard focus on a corner, not on the centre of a 3x3 grid', () => {
    expect(six.focusStart).toBe(0);
    expect(GEOMETRIES.tapatan.focusStart).toBe(4);
    expect(GEOMETRIES.achi.focusStart).toBe(4);
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

  it('goes to the nearest point in the direction of the arrow, on both 3x3 variants', () => {
    for (const key of ['tapatan', 'achi'] as const) {
      const g = GEOMETRIES[key];
      expect(neighbourByArrow(g, 0, 'ArrowRight'), key).toBe(1);
      expect(neighbourByArrow(g, 1, 'ArrowRight'), key).toBe(2);
      expect(neighbourByArrow(g, 0, 'ArrowDown'), key).toBe(3);
      expect(neighbourByArrow(g, 7, 'ArrowUp'), key).toBe(4);
      expect(neighbourByArrow(g, 5, 'ArrowLeft'), key).toBe(4);
    }
  });
});

describe('page navigation', () => {
  const g = GEOMETRIES.tapatan;

  it('steps through the points in order with Page Down and Page Up', () => {
    expect(neighbourByPage(g, 4, 'PageDown')).toBe(5);
    expect(neighbourByPage(g, 4, 'PageUp')).toBe(3);
    expect(neighbourByPage(g, 8, 'PageDown')).toBeNull();
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

  it('finds the two half-sides of a mill on Six, whatever the order of its points', () => {
    expect(segmentsOnLine(GEOMETRIES.six, [0, 1, 2]).map(key).sort()).toEqual(['0-1', '1-2']);
    expect(segmentsOnLine(GEOMETRIES.six, [2, 0, 1]).map(key).sort()).toEqual(['0-1', '1-2']);
    expect(segmentsOnLine(GEOMETRIES.six, [6, 7, 0]).map(key).sort()).toEqual(['6-7', '7-0']);
    expect(segmentsOnLine(GEOMETRIES.six, [14, 15, 8]).map(key).sort()).toEqual(['14-15', '15-8']);
  });

  it('does not light a join or a neighbouring side with a mill', () => {
    const lit = segmentsOnLine(GEOMETRIES.six, [8, 9, 10]).map(key);
    expect(lit).not.toContain('1-9');
    expect(lit).not.toContain('10-11');
  });
});
