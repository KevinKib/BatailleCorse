import { describe, expect, it } from 'vitest';
import { opponentsInPlayOrder } from './opponentOrder';

const table = (ids: number[]) => ids.map(id => ({ id: String(id) }));
const ids = (list: { id: string }[]) => list.map(p => Number(p.id));

// The backend deals turns in list order (ascending seat, wrapping, departed seats simply removed).
// The ring is filled left to right, so the first opponent must be the one who plays right after me.
describe('opponentsInPlayOrder', () => {
  for (let n = 2; n <= 6; n++) {
    for (let me = 0; me < n; me++) {
      it(`${n} players, I am seat ${me}: opponents follow the turn order after me`, () => {
        const all = Array.from({ length: n }, (_, i) => i);
        const expected = all.slice(me + 1).concat(all.slice(0, me));
        expect(ids(opponentsInPlayOrder(table(all), String(me)))).toEqual(expected);
      });
    }
  }

  it('keeps the turn order when seats have left (holes in the numbering)', () => {
    // seats 1 and 4 forfeited out of 0..5; I am seat 3
    expect(ids(opponentsInPlayOrder(table([0, 2, 3, 5]), '3'))).toEqual([5, 0, 2]);
  });

  it('excludes me and leaves the order alone when I am no longer seated', () => {
    expect(ids(opponentsInPlayOrder(table([0, 2, 5]), '3'))).toEqual([0, 2, 5]);
  });
});
