import { describe, expect, it } from 'vitest';
import { DEFAULT_MANGOLA_DIRECTION, MANGOLA_DIRECTIONS, directionOf } from './direction';

describe('Mangola sowing direction', () => {
  it('lists anticlockwise first and defaults to it', () => {
    expect(MANGOLA_DIRECTIONS).toEqual(['anticlockwise', 'clockwise']);
    expect(DEFAULT_MANGOLA_DIRECTION).toBe('anticlockwise');
  });

  it('reads the direction the server sent', () => {
    expect(directionOf({ direction: 'clockwise' })).toBe('clockwise');
    expect(directionOf({ direction: 'anticlockwise' })).toBe('anticlockwise');
  });

  it('reads an absent direction as the default', () => {
    expect(directionOf({})).toBe('anticlockwise');
  });

  it('reads an unknown direction as the default', () => {
    expect(directionOf({ direction: 'sideways' as never })).toBe('anticlockwise');
  });
});
