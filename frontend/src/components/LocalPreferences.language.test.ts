import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import PrimeVue from 'primevue/config';
import LocalPreferences from './LocalPreferences.vue';
import { GAMES, findGame, type GameKey } from '../model/games';
import { messagesEn } from '../locales/en';

// French is offered in this file only; the other tests keep the shipped single-locale list.
vi.mock('../locales', async (original) => ({
  ...(await original<typeof import('../locales')>()),
  SUPPORTED_LOCALES: ['en', 'fr'],
}));

function mountFor(key: GameKey) {
  return mount(LocalPreferences, { props: { game: findGame(key)! }, global: { plugins: [PrimeVue] }, attachTo: document.body });
}

describe('LocalPreferences with two languages', () => {
  beforeEach(() => { document.body.innerHTML = ''; localStorage.clear(); setActivePinia(createPinia()); });

  it.each(['bataillecorse', 'bullshit'] as const)('given%s_thenTheGearPanelHoldsTheLanguageRowAboveTheShortcuts', async (key) => {
    const wrapper = mountFor(key);
    const gear = wrapper.get('[data-test="hotkeys-button"]');
    expect(gear.attributes('aria-label')).toBe(messagesEn.language.settings);
    expect(wrapper.find('[data-test="language-button"]').exists()).toBe(false);

    await gear.trigger('click');
    const panel = wrapper.get('[data-test="hotkeys-panel"]');
    expect(panel.attributes('aria-label')).toBe(messagesEn.language.settings);
    const html = panel.html();
    expect(html.indexOf('data-test="language-row"')).toBeGreaterThan(-1);
    expect(html.indexOf('data-test="language-row"')).toBeLessThan(html.indexOf('data-test="hotkeys-heading"'));
    expect(wrapper.get('[data-test="hotkeys-heading"]').text()).toBe(messagesEn.hotkeys.title);
    wrapper.unmount();
  });

  it('givenEveryGameWithoutShortcuts_thenAGlobeMenuStandsInPlaceOfTheGear', () => {
    const quiet = GAMES.filter(g => !g.hotkeys);
    expect(quiet.map(g => g.key).sort()).toEqual(['draughts', 'kilo-de-merde', 'kobo', 'mangola', 'ninety-nine', 'president', 'three-men-morris']);
    for (const game of quiet) {
      const wrapper = mountFor(game.key);
      expect(wrapper.find('[data-test="hotkeys-button"]').exists()).toBe(false);
      expect(wrapper.get('[data-test="language-button"]').attributes('aria-label')).toBe(messagesEn.language.label);
      wrapper.unmount();
    }
  });
});
