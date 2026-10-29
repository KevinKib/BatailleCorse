import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import NinetyNineCenter from './NinetyNineCenter.vue';
import { buildNinetyNineCard } from '../../model/fixtures/ninetynine';

const base = {
  total: 94, direction: 'CLOCKWISE' as const, pileTop: buildNinetyNineCard('4', 'HEART'), stockSize: 8,
};

describe('NinetyNineCenter', () => {
  it('givenARunningTotal_thenItIsShownWithItsLimitAndAnAccessibleName', () => {
    const wrapper = mount(NinetyNineCenter, { props: base });
    expect(wrapper.get('[data-test="total"]').text()).toContain('94');
    expect(wrapper.get('[data-test="total"]').attributes('aria-label')).toBe('Total 94 of 99');
    expect(wrapper.text()).toContain('of 99');
  });

  it.each([[42, 'calm'], [85, 'warm'], [94, 'hot']])('givenATotalOf%i_thenItsColourClassIs_%s', (total, zone) => {
    expect(mount(NinetyNineCenter, { props: { ...base, total } }).get('[data-test="total"]').classes()).toContain(`total--${zone}`);
  });

  it('givenTheDirectionIsNormal_thenNoReversedChipIsShown', () => {
    expect(mount(NinetyNineCenter, { props: base }).find('[data-test="reversed"]').exists()).toBe(false);
  });

  it('givenTheDirectionIsReversed_thenTheChipSaysSo', () => {
    const wrapper = mount(NinetyNineCenter, { props: { ...base, direction: 'COUNTER_CLOCKWISE' } });
    expect(wrapper.get('[data-test="reversed"]').text()).toBe('Reversed');
  });

  it('givenAPileTop_thenItIsShownAndNamed', () => {
    const wrapper = mount(NinetyNineCenter, { props: base });
    expect(wrapper.get('[data-test="pile"]').attributes('aria-label')).toBe('Pile: 4 of hearts');
    expect(wrapper.find('[data-test="pile"] img.playing_card').exists()).toBe(true);
  });

  it('givenNoCardPlayedYet_thenThePileIsEmptyAndSaysSo', () => {
    const wrapper = mount(NinetyNineCenter, { props: { ...base, pileTop: null } });
    expect(wrapper.get('[data-test="pile"]').attributes('aria-label')).toBe('Pile: nothing played yet');
    expect(wrapper.find('[data-test="pile"] img.playing_card').exists()).toBe(false);
  });

  it('givenTheStock_thenItsCountIsShown', () => {
    const wrapper = mount(NinetyNineCenter, { props: base });
    expect(wrapper.get('[data-test="stock"]').attributes('aria-label')).toBe('8 cards in the stock');
    expect(wrapper.get('[data-test="stock-count"]').text()).toBe('8');
  });

  it.each([0, 7, 42, 94, 99, 100])('givenATotalOf%i_thenItsSlotKeepsTheSameReservedWidth', (total) => {
    const reference = mount(NinetyNineCenter, { props: { ...base, total: 100 } }).get('[data-test="total"]').attributes('style');
    const wrapper = mount(NinetyNineCenter, { props: { ...base, total } });
    expect(wrapper.get('[data-test="total"]').attributes('style')).toBe(reference);
    expect(reference).toContain('--total-digits: 3');
    expect(wrapper.get('[data-test="total"]').classes()).toContain('total--reserved');
  });
});
