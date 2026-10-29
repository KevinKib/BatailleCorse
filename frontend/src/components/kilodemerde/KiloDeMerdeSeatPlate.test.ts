import { describe, it, expect, afterEach } from 'vitest';
import { mount } from '@vue/test-utils';
import KiloDeMerdeSeatPlate from './KiloDeMerdeSeatPlate.vue';
import { messages } from '../../locales';
import { messagesFr } from '../../locales/fr';
import { setCurrentLocaleForTest } from '../../locales/locale';
import { kiloCard, kiloDeMerdeStepState } from '../../model/kilodemerde/fixtures';
import type { KiloDeMerdePlayer } from '../../model/kilodemerde/KiloDeMerdeState';

const base: KiloDeMerdePlayer = { ...kiloDeMerdeStepState.players[2], name: 'Alice', kilos: 113 };
const mountPlate = (extra: Record<string, unknown> = {}, player: KiloDeMerdePlayer = base) =>
  mount(KiloDeMerdeSeatPlate, { props: { player, label: 'Alice', ...extra } });

describe('KiloDeMerdeSeatPlate', () => {
  it('givenARevealedHand_thenThePlateIsExpandedAndKeepsEveryFact', () => {
    // jsdom has no layout: the class drives the CSS that lets the plate grow, wrap and scroll.
    const wrapper = mountPlate({ hand: [kiloCard('JACK', 'CLUB'), kiloCard('JACK', 'HEART')], mine: true, last: true, ready: true });
    expect(wrapper.get('[data-test="seat-plate"]').classes()).toContain('plate--expanded');
    for (const hook of ['seat-name', 'seat-kilos', 'seat-hand', 'seat-last', 'seat-ready']) {
      expect(wrapper.find(`[data-test="${hook}"]`).exists(), hook).toBe(true);
    }
    expect(mountPlate().get('[data-test="seat-plate"]').classes()).not.toContain('plate--expanded');
  });

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

  describe('my own plate', () => {
    afterEach(() => { setCurrentLocaleForTest('en'); delete messages.fr; });
    const hand = [kiloCard('KING', 'SPADE')];
    const label = (wrapper: ReturnType<typeof mountPlate>) => wrapper.get('[data-test="seat-plate"]').attributes('aria-label');

    it('givenMyPlate_thenItsReadyCheckAndHandSpeakToMe', () => {
      expect(label(mountPlate({ label: 'You', mine: true, ready: true, hand }))).toContain('You have chosen a card');
      expect(label(mountPlate({ label: 'You', mine: true, ready: true, hand }))).toContain('Your hand: K♠');
      messages.fr = messagesFr;
      setCurrentLocaleForTest('fr');
      expect(label(mountPlate({ label: 'Vous', mine: true, ready: true, hand }))).toContain('Vous avez choisi une carte');
      expect(label(mountPlate({ label: 'Vous', mine: true, ready: true, hand }))).toContain('Votre main: K♠');
      expect(label(mountPlate({ ready: true, hand }))).toContain('Alice a choisi une carte');
      expect(label(mountPlate({ ready: true, hand }))).toContain('Main d’Alice: K♠');
    });
  });

  it('givenADisconnectedSeat_thenTheBadgeIsShown', () => {
    expect(mountPlate({ disconnected: true, secondsRemaining: 20 }).find('.plate-badge').exists()).toBe(true);
  });
});
