import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentScores from './PresidentScores.vue';

function mountScores() {
  return mount(PresidentScores, {
    props: { standings: [{ seat: 1, points: 5 }, { seat: 0, points: 2 }], nameOf: (seat: number) => `P${seat}`, mySeat: 0 },
  });
}

describe('PresidentScores', () => {
  it('startsClosed', () => {
    expect(mountScores().find('[data-test="scores-panel"]').exists()).toBe(false);
  });

  it('whenScoresIsPressed_thenTheStandingsOpenAndCloseAgain', async () => {
    const wrapper = mountScores();
    await wrapper.get('[data-test="scores"]').trigger('click');
    expect(wrapper.get('[data-test="scores"]').attributes('aria-expanded')).toBe('true');
    expect(wrapper.findAll('[data-test^="final-row-"]')).toHaveLength(2);
    await wrapper.get('[data-test="scores-close"]').trigger('click');
    expect(wrapper.find('[data-test="scores-panel"]').exists()).toBe(false);
  });
});
