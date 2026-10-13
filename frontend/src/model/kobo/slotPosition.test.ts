import { describe, it, expect } from 'vitest';
import { slotPosition } from './slotPosition';

describe('slotPosition', () => {
  it('puts slots 0 and 1 on the bottom row and 2 and 3 on the top row of the square', () => {
    expect(slotPosition(0)).toEqual({ row: 1, col: 0 });
    expect(slotPosition(1)).toEqual({ row: 1, col: 1 });
    expect(slotPosition(2)).toEqual({ row: 0, col: 0 });
    expect(slotPosition(3)).toEqual({ row: 0, col: 1 });
  });

  it('opens a new column on the right for slots 4 and up, bottom first', () => {
    expect(slotPosition(4)).toEqual({ row: 1, col: 2 });
    expect(slotPosition(5)).toEqual({ row: 0, col: 2 });
    expect(slotPosition(6)).toEqual({ row: 1, col: 3 });
    expect(slotPosition(7)).toEqual({ row: 0, col: 3 });
    expect(slotPosition(8)).toEqual({ row: 1, col: 4 });
  });

  it('never gives two slots the same cell', () => {
    const cells = Array.from({ length: 12 }, (_, i) => JSON.stringify(slotPosition(i)));
    expect(new Set(cells).size).toBe(12);
  });
});
