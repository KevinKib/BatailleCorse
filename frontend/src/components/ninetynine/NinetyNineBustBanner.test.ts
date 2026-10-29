import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import NinetyNineBustBanner from './NinetyNineBustBanner.vue';
import { messages } from '../../locales';
import { messagesFr } from '../../locales/fr';
import { setCurrentLocaleForTest } from '../../locales/locale';
import { buildNinetyNineBust } from '../../model/fixtures/ninetynine';

const bust = buildNinetyNineBust().lastBust!;

describe('NinetyNineBustBanner', () => {
  it('givenAStuckHand_thenTheBannerNamesWhoIsStuckAtWhatTotal', () => {
    const wrapper = mount(NinetyNineBustBanner, { props: { bust, label: 'Gina', mine: false } });
    expect(wrapper.attributes('role')).toBe('alert');
    expect(wrapper.get('[data-test="bust-title"]').text()).toBe('Gina goes over 99 (103)');
  });

  it('givenMyOwnBust_thenTheTitleSaysYou', () => {
    const wrapper = mount(NinetyNineBustBanner, { props: { bust, label: 'You', mine: true } });
    expect(wrapper.get('[data-test="bust-title"]').text()).toBe('You go over 99 (103)');
  });

  it('givenTheRemainingHand_thenEveryCardIsRevealedAndNamed', () => {
    const wrapper = mount(NinetyNineBustBanner, { props: { bust, label: 'Gina', mine: false } });
    const cards = wrapper.findAll('[data-test="bust-card"]');
    expect(cards).toHaveLength(2);
    expect(cards.map(c => c.attributes('aria-label'))).toEqual(['8 of clubs', 'Jack of spades']);
    expect(wrapper.get('[data-test="bust-hand"]').attributes('aria-label')).toBe('Hand of Gina');
  });

  it('givenMyOwnBust_thenTheHandIsMine', () => {
    const wrapper = mount(NinetyNineBustBanner, { props: { bust, label: 'You', mine: true } });
    expect(wrapper.get('[data-test="bust-hand"]').attributes('aria-label')).toBe('Your hand');
  });

  it('givenFrench_thenTheBannerIsConjugatedForMeOrTheNamedSeat', () => {
    messages.fr = messagesFr;
    setCurrentLocaleForTest('fr');
    try {
      const mine = mount(NinetyNineBustBanner, { props: { bust, label: 'Vous', mine: true } });
      expect(mine.get('[data-test="bust-title"]').text()).toBe('Vous dépassez 99 (103)');
      expect(mine.get('[data-test="bust-hand"]').attributes('aria-label')).toBe('Votre main');
      expect(mine.get('[data-test="bust-lives"]').text()).toBe('Une vie perdue · 2 restantes');
      const theirs = mount(NinetyNineBustBanner, { props: { bust, label: 'Gina', mine: false } });
      expect(theirs.get('[data-test="bust-title"]').text()).toBe('Gina dépasse 99 (103)');
      expect(theirs.get('[data-test="bust-hand"]').attributes('aria-label')).toBe('Main de Gina');
    } finally {
      setCurrentLocaleForTest('en');
      delete messages.fr;
    }
  });

  it('givenTheReason_thenItSaysEveryCardGoesOver', () => {
    const wrapper = mount(NinetyNineBustBanner, { props: { bust, label: 'Gina', mine: false } });
    expect(wrapper.get('[data-test="bust-reason"]').text()).toBe('No card could stay at 99 or below.');
  });

  it('givenALifeLeft_thenTheBannerSaysTheLifeLostAndHowManyRemain', () => {
    const wrapper = mount(NinetyNineBustBanner, { props: { bust, label: 'Gina', mine: false } });
    expect(wrapper.get('[data-test="bust-lives"]').text()).toBe('Life lost · 2 left');
  });

  it('givenTheLastLifeLost_thenTheBannerSaysTheSeatIsOut', () => {
    const out = { ...bust, livesLeft: 0, eliminated: true };
    const wrapper = mount(NinetyNineBustBanner, { props: { bust: out, label: 'Gina', mine: false } });
    expect(wrapper.get('[data-test="bust-lives"]').text()).toBe('Gina is out');
    const mineOut = mount(NinetyNineBustBanner, { props: { bust: out, label: 'You', mine: true } });
    expect(mineOut.get('[data-test="bust-lives"]').text()).toBe('You are out');
  });

  it('givenTheRoundEnd_thenTheProgressBarRunsForTheWholeTimeTheBannerIsShown', () => {
    const wrapper = mount(NinetyNineBustBanner, { props: { bust, label: 'Gina', mine: false } });
    expect(wrapper.get('[data-test="bust-bar-fill"]').attributes('style')).toContain('animation-duration: 7500ms');
  });

  // The banner is only shown at a round end, so a next deal always follows.
  it('givenTheRoundEnd_thenTheBannerSaysTheNextDealComesInAMoment', () => {
    const wrapper = mount(NinetyNineBustBanner, { props: { bust, label: 'Gina', mine: false } });
    expect(wrapper.get('[data-test="bust-next"]').text()).toBe('The next deal comes in a moment');
  });
});
