import { describe, it, expect, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import { createAppRouter } from '../router';
import { createMemoryHistory } from 'vue-router';
import GamePickerView from './GamePickerView.vue';
import { messagesEn } from '../locales/en';

// These tests cover the single-language UI; the two-language UI is in the *.language.test.ts files.
vi.mock('../locales', async (original) => ({
  ...(await original<typeof import('../locales')>()),
  SUPPORTED_LOCALES: ['en'],
}));

async function mountPicker() {
  const router = createAppRouter(createMemoryHistory());
  await router.push('/');
  await router.isReady();
  const wrapper = mount(GamePickerView, { global: { plugins: [router, PrimeVue] } });
  return { router, wrapper };
}

describe('GamePickerView', () => {
  it('givenRemovedByTheHost_thenShowsTheNoticeOnlyThen', async () => {
    const router = createAppRouter(createMemoryHistory());
    await router.push('/?removed=1');
    await router.isReady();
    const removed = mount(GamePickerView, { global: { plugins: [router, PrimeVue] } });
    expect(removed.get('[data-test="removed-notice"]').text()).toBe(messagesEn.lobby.removedByHost);
    expect(removed.get('[data-test="removed-notice"]').attributes('role')).toBe('status');

    const { wrapper } = await mountPicker();
    expect(wrapper.find('[data-test="removed-notice"]').exists()).toBe(false);
  });

  it('givenOnlyEnglishIsOffered_thenThereIsNoLanguagePicker', async () => {
    const { wrapper } = await mountPicker();
    expect(wrapper.find('[data-test="language-button"]').exists()).toBe(false);
  });

  it('givenPicker_thenShowsTitleAndEveryGameWithDescriptionAndPlayerCount', async () => {
    const { wrapper } = await mountPicker();
    const text = wrapper.text();
    const p = messagesEn.gamePicker;
    expect(wrapper.get('h1').text()).toBe(p.title);
    for (const game of [p.games.bataillecorse!, p.games.bullshit!, p.games.kobo!, p.games['three-men-morris']!, p.games.president!, p.games.mangola!, p.games['ninety-nine']!, p.games.draughts!, p.games['kilo-de-merde']!]) {
      expect(text).toContain(game.name);
      expect(text).toContain(game.description);
      expect(text).toContain(game.players);
    }
    expect(wrapper.findAll('article')).toHaveLength(9);
  });

  it('givenPicker_thenEachCardHasAKeyboardReachablePlayButtonNamedAfterItsGame', async () => {
    const { wrapper } = await mountPicker();
    const buttons = wrapper.findAll('button');
    expect(buttons).toHaveLength(9);
    expect(buttons[0].attributes('aria-label')).toBe('Play Bataille Corse');
    expect(buttons[1].attributes('aria-label')).toBe('Play Bullshit');
    expect(buttons[2].attributes('aria-label')).toBe('Play Kobo');
    expect(buttons[3].attributes('aria-label')).toBe('Play Morris');
    expect(buttons[4].attributes('aria-label')).toBe('Play President');
    expect(buttons[5].attributes('aria-label')).toBe('Play Mangola');
    expect(buttons[6].attributes('aria-label')).toBe('Play 99');
    expect(buttons[7].attributes('aria-label')).toBe('Play Draughts');
  });

  it('givenPlayOnDraughts_thenGoesToItsGameHome', async () => {
    const { wrapper, router } = await mountPicker();
    await wrapper.get('[data-test="play-draughts"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/draughts');
  });

  it('givenPlayOnPresident_thenGoesToItsGameHome', async () => {
    const { wrapper, router } = await mountPicker();
    await wrapper.get('[data-test="play-president"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/president');
  });

  it('givenPlayOnMangola_thenGoesToItsGameHome', async () => {
    const { wrapper, router } = await mountPicker();
    await wrapper.get('[data-test="play-mangola"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/mangola');
  });

  it('givenPlayOnNinetyNine_thenGoesToItsGameHome', async () => {
    const { wrapper, router } = await mountPicker();
    await wrapper.get('[data-test="play-ninety-nine"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/ninety-nine');
  });

  it('givenPlayOnKiloDeMerde_thenGoesToItsGameHome', async () => {
    const { wrapper, router } = await mountPicker();
    await wrapper.get('[data-test="play-kilo-de-merde"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/kilo-de-merde');
  });

  it('givenPlayOnBatailleCorse_thenGoesToItsGameHome', async () => {
    const { wrapper, router } = await mountPicker();
    await wrapper.get('[data-test="play-bataillecorse"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/bataillecorse');
  });

  it('givenPlayOnThreeMenMorris_thenGoesToItsGameHome', async () => {
    const { wrapper, router } = await mountPicker();
    await wrapper.get('[data-test="play-three-men-morris"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/three-men-morris');
  });

  it('givenPlayOnBullshit_thenGoesToItsGameHome', async () => {
    const { wrapper, router } = await mountPicker();
    await wrapper.get('[data-test="play-bullshit"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/bullshit');
  });

  it('givenPlayOnKobo_thenGoesToItsGameHome', async () => {
    const { wrapper, router } = await mountPicker();
    await wrapper.get('[data-test="play-kobo"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/kobo');
  });

  it('givenPicker_thenShowsADiscretePrivacyLinkThatOpensThePrivacyPage', async () => {
    const { wrapper, router } = await mountPicker();
    const link = wrapper.get('[data-test="privacy-link"]');
    expect(link.element.tagName).toBe('A');
    expect(link.text()).toBe(messagesEn.gamePicker.privacy);
    expect(link.attributes('href')).toBe('/privacy');
    // It is the last thing in the panel's flow, after the games (the panel gap spaces it, no absolute position).
    expect(link.element.parentElement!.lastElementChild).toBe(link.element);
    // It sits after the games, not among them: the Play buttons stay the only buttons.
    expect(wrapper.findAll('button')).toHaveLength(9);
    await link.trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/privacy');
  });

  it('givenTheGames_thenOnlyDraughtsDrawsACustomSvgIconAndTheOthersKeepTheirPrimeIcon', async () => {
    const { wrapper } = await mountPicker();
    const draughts = wrapper.get('[data-test="play-draughts"]');
    expect(draughts.find('svg[data-test="checkerboard-icon"]').exists()).toBe(true);
    expect(draughts.find('svg').attributes('aria-hidden')).toBe('true');
    expect(draughts.find('.pi-table').exists()).toBe(false);
    for (const key of ['bataillecorse', 'bullshit', 'kobo', 'three-men-morris', 'president', 'mangola', 'ninety-nine', 'kilo-de-merde']) {
      const button = wrapper.get(`[data-test="play-${key}"]`);
      expect(button.find('span.pi').exists()).toBe(true);
      expect(button.find('svg').exists()).toBe(false);
    }
  });
});
