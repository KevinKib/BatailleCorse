import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentSeat from './PresidentSeat.vue';
import { buildPresidentPlayer } from '../../model/fixtures/president';

function mountSeat(props: Record<string, unknown> = {}) {
  return mount(PresidentSeat, { props: { player: buildPresidentPlayer(1, { handSize: 5 }), label: 'Bob', ...props } });
}

describe('PresidentSeat', () => {
  it('showsTheNameAndTheCardCount', () => {
    const wrapper = mountSeat();
    expect(wrapper.get('[data-test="seat-label"]').text()).toBe('Bob');
    expect(wrapper.get('[data-test="seat-cards"]').text()).toBe('5 cards');
  });

  it('givenOneCard_thenTheCountIsSingular', () => {
    expect(mountSeat({ player: buildPresidentPlayer(1, { handSize: 1 }) }).get('[data-test="seat-cards"]').text()).toBe('1 card');
  });

  it.each([
    ['PRESIDENT', 'President'],
    ['VICE_PRESIDENT', 'Vice-President'],
    ['NEUTRAL', 'Neutral'],
    ['VICE_SCUM', 'Vice-Scum'],
    ['SCUM', 'Scum'],
  ] as const)('givenTheTitle_%s_thenItIsWrittenAsText', (title, text) => {
    const wrapper = mountSeat({ player: buildPresidentPlayer(1, { title }) });
    expect(wrapper.get('[data-test="seat-title"]').text()).toBe(text);
  });

  it('givenNoTitleYet_thenNoTitleIsShown', () => {
    expect(mountSeat().find('[data-test="seat-title"]').exists()).toBe(false);
  });

  it('givenTheCurrentSeat_thenItSaysSo', () => {
    const wrapper = mountSeat({ current: true });
    expect(wrapper.get('[data-test="seat-label"]').classes()).toContain('seat-label--active');
    expect(wrapper.get('[data-test="seat-current"]').text()).toBe('Playing');
  });

  it('givenAPassedSeat_thenItSaysPassed', () => {
    const wrapper = mountSeat({ player: buildPresidentPlayer(1, { passed: true }) });
    expect(wrapper.get('[data-test="seat-passed"]').text()).toBe('Passed');
  });

  it('givenASeatThatIsOut_thenItShowsItsPlaceAndNotThePassMarker', () => {
    const wrapper = mountSeat({ player: buildPresidentPlayer(1, { out: true, place: 2, handSize: 0, passed: true }) });
    expect(wrapper.get('[data-test="seat-out"]').text()).toBe('Out, place 2');
    expect(wrapper.find('[data-test="seat-passed"]').exists()).toBe(false);
  });

  it('givenAReadySeatAtTheRoundEnd_thenItSaysReady', () => {
    const wrapper = mountSeat({ player: buildPresidentPlayer(1, { ready: true }), showReady: true });
    expect(wrapper.get('[data-test="seat-ready"]').text()).toBe('Ready');
  });

  it('givenADisconnectedSeat_thenTheBadgeShows', () => {
    const wrapper = mountSeat({ disconnected: true, secondsRemaining: 30 });
    expect(wrapper.find('[data-test="seat-disconnect-badge"]').exists()).toBe(true);
  });
});
