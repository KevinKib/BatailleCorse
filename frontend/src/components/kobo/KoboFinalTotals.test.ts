import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KoboFinalTotals from './KoboFinalTotals.vue';
import { seatDisplayName } from '../../model/table/displayName';
import type { StandingRow } from '../../model/kobo/roundScores';

const NAMES = ['Alice', 'Bob', 'Carol'];
const standing = (seat: number, total: number, rank: number, over = false): StandingRow => ({ seat, total, rank, over });

function mountTotals(rows: StandingRow[]) {
  return mount(KoboFinalTotals, { props: { rows, nameOf: (seat: number) => NAMES[seat], mySeat: 0 } });
}

describe('KoboFinalTotals', () => {
  it('showsRankNameAndTotalOfEveryPlayerInTheOrderGiven', () => {
    const wrapper = mountTotals([standing(1, 20, 1), standing(0, 55, 2)]);
    expect(wrapper.findAll('[data-test^="final-row-"]').map(r => r.attributes('data-test')))
      .toEqual(['final-row-1', 'final-row-0']);
    const bob = wrapper.get('[data-test="final-row-1"]');
    expect(bob.get('[data-test="final-rank"]').text()).toBe('1');
    expect(bob.get('[data-test="final-name"]').text()).toBe('Bob');
    expect(bob.get('[data-test="final-total"]').text()).toBe('20');
  });

  it('givenMySeat_thenMyRowIsCalledYou', () => {
    const wrapper = mountTotals([standing(0, 10, 1)]);
    expect(wrapper.get('[data-test="final-name"]').text()).toBe('You');
  });

  it('givenATotalOverOneHundred_thenTheOverPillIsOnThatRowOnly', () => {
    const wrapper = mountTotals([standing(1, 20, 1), standing(2, 104, 2, true)]);
    expect(wrapper.get('[data-test="final-row-2"] [data-test="pill-over"]').text()).toBe('over 100');
    expect(wrapper.find('[data-test="final-row-1"] [data-test="pill-over"]').exists()).toBe(false);
  });

  it('hasATitleAndColumnHeaders', () => {
    const wrapper = mountTotals([standing(0, 10, 1)]);
    expect(wrapper.get('[data-test="final-totals"]').attributes('aria-label')).toBe('Final totals');
    expect(wrapper.findAll('thead th').map(h => h.text())).toEqual(['Rank', 'Player', 'Total']);
  });

  it('givenAnamedAnUnnamedAndABotPlayer_thenEachRowShowsItsName', () => {
    const players = [
      { id: '0', name: 'Alice' }, { id: '1', name: '' }, { id: '2', name: 'Robo', bot: true },
    ];
    const labels = { player: 'Player {n}', bot: 'Bot {n}' };
    const wrapper = mount(KoboFinalTotals, {
      props: {
        rows: [standing(2, 5, 1), standing(1, 20, 2), standing(0, 30, 3)],
        nameOf: (seat: number) => seatDisplayName(players, seat, labels),
        mySeat: 0,
      },
    });
    expect(wrapper.findAll('[data-test="final-name"]').map(n => n.text())).toEqual(['Bot 1', 'Player 2', 'You']);
  });
});
