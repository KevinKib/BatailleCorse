import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import NinetyNineStandings from './NinetyNineStandings.vue';
import { buildNinetyNineQuickEnd } from '../../model/fixtures/ninetynine';

const players = buildNinetyNineQuickEnd().players;
const nameOf = (seat: number) => ['Kevin', 'Alice', 'Chloé'][seat];

describe('NinetyNineStandings', () => {
  it('givenSharedPlacesInQuickMode_thenBothFirstsShowFirst', () => {
    const wrapper = mount(NinetyNineStandings, { props: { players, nameOf, mySeat: 0, maxLives: 3 } });
    const rows = wrapper.findAll('[data-test="place-row"]');
    expect(rows.map(r => r.get('[data-test="place"]').text())).toEqual(['1st', '1st', '3rd']);
    expect(rows.map(r => r.get('[data-test="place-name"]').text())).toEqual(['You', 'Alice', 'Chloé']);
  });

  it('givenMyRow_thenItIsMarkedAndNamedYou', () => {
    const wrapper = mount(NinetyNineStandings, { props: { players, nameOf, mySeat: 0, maxLives: 3 } });
    expect(wrapper.findAll('[data-test="place-row"]')[0].classes()).toContain('place-row--me');
    expect(wrapper.findAll('[data-test="place-row"]')[1].classes()).not.toContain('place-row--me');
  });

  it('givenTheLivesLeft_thenEachRowShowsHeartsAndAnAccessibleCount', () => {
    const wrapper = mount(NinetyNineStandings, { props: { players, nameOf, mySeat: 0, maxLives: 3 } });
    const first = wrapper.findAll('[data-test="place-row"]')[0];
    expect(first.findAll('[data-test="heart"][data-full="true"]')).toHaveLength(2);
    expect(first.get('[data-test="lives"]').attributes('aria-label')).toBe('2 lives left');
    expect(wrapper.findAll('[data-test="place-row"]')[2].get('[data-test="lives"]').attributes('aria-label')).toBe('0 lives left');
  });

  it('givenPlayersUnsorted_thenTheyAreListedByPlaceThenBySeat', () => {
    const shuffled = [players[2], players[1], players[0]];
    const wrapper = mount(NinetyNineStandings, { props: { players: shuffled, nameOf, mySeat: 0, maxLives: 3 } });
    expect(wrapper.findAll('[data-test="place-name"]').map(n => n.text())).toEqual(['You', 'Alice', 'Chloé']);
  });

  it('givenTheList_thenItIsALabelledOrderedList', () => {
    const wrapper = mount(NinetyNineStandings, { props: { players, nameOf, mySeat: 0, maxLives: 3 } });
    expect(wrapper.get('ol').attributes('aria-label')).toBe('Final places');
  });
});
