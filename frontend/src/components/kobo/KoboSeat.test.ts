import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KoboSeat from './KoboSeat.vue';
import { buildKoboPlayer } from '../../model/fixtures/kobo';

function mountSeat(props: Record<string, unknown> = {}) {
  return mount(KoboSeat, { props: { player: buildKoboPlayer(1, { total: 12 }), label: 'Bob', ...props } });
}

describe('KoboSeat', () => {
  it('showsTheNameTheTotalAndTheCardCount', () => {
    const wrapper = mountSeat();
    expect(wrapper.get('[data-test="seat-label"]').text()).toBe('Bob');
    expect(wrapper.get('[data-test="seat-total"]').text()).toBe('Total 12');
    expect(wrapper.get('[data-test="seat-cards"]').text()).toBe('4 cards');
  });

  it('givenOneCard_thenTheCountIsSingular', () => {
    const wrapper = mountSeat({ player: buildKoboPlayer(1, { handSize: 1 }) });
    expect(wrapper.get('[data-test="seat-cards"]').text()).toBe('1 card');
  });

  it('givenTheCurrentPlayer_thenTheSeatSaysSo', () => {
    const wrapper = mountSeat({ player: buildKoboPlayer(1, { current: true }) });
    expect(wrapper.get('[data-test="seat-label"]').classes()).toContain('seat-label--active');
    expect(wrapper.get('[data-test="seat-current"]').text()).toBe('Playing now');
  });

  it('givenNoMarker_thenNoneIsShown', () => {
    const wrapper = mountSeat();
    for (const id of ['seat-current', 'seat-ready', 'seat-announcer', 'seat-final-turn', 'seat-disconnect-badge']) {
      expect(wrapper.find(`[data-test="${id}"]`).exists()).toBe(false);
    }
  });

  it('givenReadyAnnouncerAndFinalTurn_thenEachMarkerShows', () => {
    const wrapper = mountSeat({ ready: true, announcer: true, finalTurn: true });
    expect(wrapper.get('[data-test="seat-ready"]').text()).toBe('Ready');
    expect(wrapper.get('[data-test="seat-announcer"]').text()).toBe('Kobo!');
    expect(wrapper.get('[data-test="seat-final-turn"]').text()).toBe('Final turn');
  });

  it('givenADisconnectedPlayer_thenTheBadgeShowsTheCountdown', () => {
    const wrapper = mountSeat({ disconnected: true, secondsRemaining: 20 });
    expect(wrapper.get('[data-test="seat-disconnect-badge"]').text()).toContain('20');
  });

  it('givenAMarkupName_thenItIsRenderedAsTextOnly', () => {
    const wrapper = mountSeat({ label: '<b>x</b>' });
    expect(wrapper.get('[data-test="seat-label"]').text()).toBe('<b>x</b>');
    expect(wrapper.find('b').exists()).toBe(false);
  });
});
