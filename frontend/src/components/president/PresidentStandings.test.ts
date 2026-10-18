import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentStandings from './PresidentStandings.vue';

const standings = [{ seat: 2, points: 9 }, { seat: 0, points: 9 }, { seat: 1, points: 4 }];

function mountStandings(props: Record<string, unknown> = {}) {
  return mount(PresidentStandings, {
    props: { standings, nameOf: (seat: number) => `P${seat}`, mySeat: 0, ...props },
  });
}
const rowsOf = (w: ReturnType<typeof mountStandings>) => w.findAll('tbody tr');

// The standings are the shared table of totals (Kobo's final totals look): rank, name ("You" for me), points.
describe('PresidentStandings', () => {
  it('listsTheSeatsInTheOrderGivenWithTheirPoints_andNamesMeYou', () => {
    const rows = rowsOf(mountStandings());
    expect(rows.map(r => r.attributes('data-test'))).toEqual(['final-row-2', 'final-row-0', 'final-row-1']);
    expect(rows.map(r => r.get('[data-test="final-name"]').text())).toEqual(['P2', 'You', 'P1']);
    expect(rows.map(r => r.get('[data-test="final-total"]').text())).toEqual(['9', '9', '4']);
  });

  it('givenTiedPoints_thenTheyShareThePlace', () => {
    expect(rowsOf(mountStandings()).map(r => r.get('[data-test="final-rank"]').text())).toEqual(['1', '1', '3']);
  });

  it('marksMyOwnRow', () => {
    expect(rowsOf(mountStandings()).map(r => r.classes('final-row--me'))).toEqual([false, true, false]);
  });

  it('isATableWithAnAccessibleNameAndColumnHeaders', () => {
    const w = mountStandings();
    expect(w.get('table').attributes('aria-label')).toBe('Standings');
    expect(w.findAll('thead th').map(h => h.text())).toEqual(['Rank', 'Player', 'Points']);
  });
});
