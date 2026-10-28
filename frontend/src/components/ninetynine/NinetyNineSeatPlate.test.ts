import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import NinetyNineSeatPlate from './NinetyNineSeatPlate.vue';
import NinetyNineLives from './NinetyNineLives.vue';
import type { NinetyNinePlayer } from '../../model/ninetynine/NinetyNineState';

const player = (overrides: Partial<NinetyNinePlayer> = {}): NinetyNinePlayer => ({
  seat: 1, name: 'Alice', lives: 3, handSize: 3, eliminated: false, place: null, ...overrides,
});

describe('NinetyNineLives', () => {
  it('givenTwoLivesOfThree_thenThreeHeartsTwoFull', () => {
    const wrapper = mount(NinetyNineLives, { props: { lives: 2, max: 3 } });
    expect(wrapper.findAll('[data-test="heart"]')).toHaveLength(3);
    expect(wrapper.findAll('[data-test="heart"][data-full="true"]')).toHaveLength(2);
    expect(wrapper.attributes('aria-label')).toBe('2 lives left');
  });

  it('givenOneLife_thenTheLabelIsSingular', () => {
    expect(mount(NinetyNineLives, { props: { lives: 1, max: 3 } }).attributes('aria-label')).toBe('1 life left');
  });

  it('givenMoreLivesThanTheOption_thenTheRowGrowsToHoldThemAll', () => {
    expect(mount(NinetyNineLives, { props: { lives: 4, max: 3 } }).findAll('[data-test="heart"]')).toHaveLength(4);
  });
});

describe('NinetyNineSeatPlate', () => {
  it('givenALivePlayer_thenTheNameTheHeartsAndTheCardsAreShown', () => {
    const wrapper = mount(NinetyNineSeatPlate, { props: { player: player({ lives: 2 }), label: 'Alice', maxLives: 3 } });
    expect(wrapper.get('[data-test="seat-name"]').text()).toBe('Alice');
    expect(wrapper.findAll('[data-test="heart"][data-full="true"]')).toHaveLength(2);
    expect(wrapper.get('[data-test="seat-cards"]').text()).toBe('3 cards');
  });

  it('givenACompactPlate_thenTheCardCountIsLeftToTheAccessibleName', () => {
    const wrapper = mount(NinetyNineSeatPlate, { props: { player: player(), label: 'Alice', maxLives: 3, compact: true } });
    expect(wrapper.find('[data-test="seat-cards"]').exists()).toBe(false);
    expect(wrapper.attributes('aria-label')).toBe('Alice, 3 lives left, 3 cards');
  });

  it('givenAnEliminatedPlayer_thenItShowsOutWithItsPlaceInsteadOfTheHearts', () => {
    const wrapper = mount(NinetyNineSeatPlate, {
      props: { player: player({ lives: 0, handSize: 0, eliminated: true, place: 12 }), label: 'Chloé', maxLives: 3 },
    });
    expect(wrapper.get('[data-test="seat-out"]').text()).toBe('OUT · 12th');
    expect(wrapper.find('[data-test="heart"]').exists()).toBe(false);
    expect(wrapper.classes()).toContain('plate--out');
    expect(wrapper.attributes('aria-label')).toBe('Chloé, OUT · 12th');
  });

  it('givenTheSeatOnTurn_thenItIsMarked', () => {
    const wrapper = mount(NinetyNineSeatPlate, { props: { player: player(), label: 'Alice', maxLives: 3, current: true } });
    expect(wrapper.attributes('data-current')).toBe('true');
    expect(wrapper.attributes('aria-current')).toBe('true');
  });

  it('givenTheSeatThatLostALife_thenTheMinusOneBadgeIsShown', () => {
    const wrapper = mount(NinetyNineSeatPlate, { props: { player: player({ lives: 1 }), label: 'Gina', maxLives: 3, hit: true } });
    expect(wrapper.get('[data-test="seat-hit"]').text()).toBe('−1');
  });

  it('givenMyOwnPlate_thenItIsHighlighted', () => {
    const wrapper = mount(NinetyNineSeatPlate, { props: { player: player({ seat: 0 }), label: 'You', maxLives: 3, mine: true } });
    expect(wrapper.classes()).toContain('plate--mine');
  });

  it('givenADisconnectedPlayer_thenTheBadgeCountsDown', () => {
    const wrapper = mount(NinetyNineSeatPlate, {
      props: { player: player(), label: 'Alice', maxLives: 3, disconnected: true, secondsRemaining: 42 },
    });
    expect(wrapper.get('[data-test="seat-disconnect-badge"]').text()).toContain('42');
  });

  it('givenALongName_thenItIsTruncatedWithTheFullNameInTheTitle', () => {
    const wrapper = mount(NinetyNineSeatPlate, { props: { player: player(), label: 'Bartholomew the Great', maxLives: 3 } });
    expect(wrapper.get('[data-test="seat-name"]').attributes('title')).toBe('Bartholomew the Great');
  });

  it('givenTheRailChip_thenItShowsTheTurnNumberTheNameAndPips', () => {
    const wrapper = mount(NinetyNineSeatPlate, {
      props: { player: player({ lives: 2 }), label: 'Alice', maxLives: 3, variant: 'chip', order: 4 },
    });
    expect(wrapper.get('[data-test="seat-order"]').text()).toBe('4');
    expect(wrapper.findAll('[data-test="heart"][data-full="true"]')).toHaveLength(2);
  });
});
