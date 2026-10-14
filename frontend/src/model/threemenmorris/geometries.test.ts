import { describe, it, expect } from 'vitest';
import { GEOMETRIES, geometryOf, neighbourByArrow, variantKeyOf, VARIANT_KEYS } from './geometries';
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
  it('places the nine points of every variant on a 3x3 grid in a 0-100 box, row by row', () => {
    for (const key of VARIANT_KEYS) {
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

  it('adds the four short diagonals of the diamond to Picaria', () => {
    const { segments } = GEOMETRIES.picaria;
    expect(segments).toHaveLength(12);
    for (const diagonal of [[1, 3], [3, 7], [7, 5], [5, 1]]) {
      expect(segments).toContainEqual(diagonal);
    }
    // The eight lines of Tapatan are all still there.
    for (const line of GEOMETRIES.tapatan.segments) expect(segments).toContainEqual(line);
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
