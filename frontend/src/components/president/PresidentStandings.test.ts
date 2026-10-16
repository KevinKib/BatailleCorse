import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentStandings from './PresidentStandings.vue';

const standings = [{ seat: 2, points: 9 }, { seat: 0, points: 9 }, { seat: 1, points: 4 }];

function mountStandings(props: Record<string, unknown> = {}) {
  return mount(PresidentStandings, {
    props: { standings, nameOf: (seat: number) => `P${seat}`, mySeat: 0, ...props },
  });
}

describe('PresidentStandings', () => {
  it('listsTheSeatsInTheOrderGivenWithTheirPoints', () => {
    const rows = mountStandings().findAll('[data-test="standing-row"]');
    expect(rows.map(r => r.get('[data-test="standing-name"]').text())).toEqual(['P2', 'P0', 'P1']);
    expect(rows.map(r => r.get('[data-test="standing-points"]').text())).toEqual(['9', '9', '4']);
  });

  it('givenTiedPoints_thenTheyShareThePlace', () => {
    const rows = mountStandings().findAll('[data-test="standing-row"]');
    expect(rows.map(r => r.get('[data-test="standing-rank"]').text())).toEqual(['1', '1', '3']);
  });

  it('marksMyOwnRow', () => {
    const rows = mountStandings().findAll('[data-test="standing-row"]');
    expect(rows.map(r => r.classes('standing--me'))).toEqual([false, true, false]);
  });

  it('exposesTheListWithAnAccessibleName', () => {
    expect(mountStandings().get('ol').attributes('aria-label')).toBe('Standings');
  });
});
