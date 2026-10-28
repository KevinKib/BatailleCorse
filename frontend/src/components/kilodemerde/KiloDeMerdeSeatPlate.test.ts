import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KiloDeMerdeSeatPlate from './KiloDeMerdeSeatPlate.vue';
import { kiloCard, kiloDeMerdeStepState } from '../../model/kilodemerde/fixtures';
import type { KiloDeMerdePlayer } from '../../model/kilodemerde/KiloDeMerdeState';

const base: KiloDeMerdePlayer = { ...kiloDeMerdeStepState.players[2], name: 'Alice', kilos: 113 };
const mountPlate = (extra: Record<string, unknown> = {}, player: KiloDeMerdePlayer = base) =>
  mount(KiloDeMerdeSeatPlate, { props: { player, label: 'Alice', ...extra } });

describe('KiloDeMerdeSeatPlate', () => {
  it('givenAPlayer_thenTheNameAndTheKilosAreShownAndNamed', () => {
    const wrapper = mountPlate();
    expect(wrapper.get('[data-test="seat-name"]').text()).toBe('Alice');
    expect(wrapper.get('[data-test="seat-kilos"]').text()).toBe('113 kg');
    expect(wrapper.get('[data-test="seat-plate"]').attributes('aria-label')).toContain('Alice: 113 kg');
  });

  it('givenABot_thenItsLevelIsShown', () => {
    const wrapper = mountPlate({}, { ...base, bot: true, level: 'medium' });
    expect(wrapper.get('[data-test="seat-level"]').text()).toBe('Medium');
    expect(mountPlate().find('[data-test="seat-level"]').exists()).toBe(false);
  });

  it('givenASeatThatChose_thenAReadyCheckIsShownAndNamed', () => {
    const wrapper = mountPlate({ ready: true });
    expect(wrapper.get('[data-test="seat-ready"]').exists()).toBe(true);
    expect(wrapper.get('[data-test="seat-plate"]').attributes('aria-label')).toContain('Alice has chosen a card');
    expect(mountPlate().find('[data-test="seat-ready"]').exists()).toBe(false);
  });

  it('givenASlapRank_thenItIsShownOnTheHandMark', () => {
    const wrapper = mountPlate({}, { ...base, slapRank: 2 });
    expect(wrapper.get('[data-test="seat-rank"]').text()).toBe('2');
    expect(mountPlate().find('[data-test="seat-rank"]').exists()).toBe(false);
  });

  it('givenTheLastToSlap_thenTheLastTagIsShown', () => {
    expect(mountPlate({ last: true }).get('[data-test="seat-last"]').text()).toBe('Last');
    expect(mountPlate().find('[data-test="seat-last"]').exists()).toBe(false);
  });

  it('givenARevealedHand_thenItIsShownAsShortLabels', () => {
    const hand = [kiloCard('KING', 'SPADE'), kiloCard('KING', 'HEART'), kiloCard('9', 'CLUB'), kiloCard('10', 'DIAMOND')];
    const wrapper = mountPlate({ hand });
    expect(wrapper.get('[data-test="seat-hand"]').text()).toBe('K♠ K♥ 9♣ 10♦');
    expect(mountPlate().find('[data-test="seat-hand"]').exists()).toBe(false);
  });

  it('givenTheKilosButton_thenItAsksToShowTheKiloCards', async () => {
    const wrapper = mountPlate();
    await wrapper.get('[data-test="seat-kilos"]').trigger('click');
    expect(wrapper.emitted('show-kilos')).toHaveLength(1);
  });

  it('givenADisconnectedSeat_thenTheBadgeIsShown', () => {
    expect(mountPlate({ disconnected: true, secondsRemaining: 20 }).find('.plate-badge').exists()).toBe(true);
  });
});
