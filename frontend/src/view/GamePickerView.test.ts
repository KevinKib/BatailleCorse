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
    for (const game of [p.games.bataillecorse!, p.games.bullshit!, p.games.kobo!, p.games['three-men-morris']!, p.games.president!, p.games.mangola!, p.games['ninety-nine']!]) {
      expect(text).toContain(game.name);
      expect(text).toContain(game.description);
      expect(text).toContain(game.players);
    }
    expect(wrapper.findAll('article')).toHaveLength(7);
  });

  it('givenPicker_thenEachCardHasAKeyboardReachablePlayButtonNamedAfterItsGame', async () => {
    const { wrapper } = await mountPicker();
    const buttons = wrapper.findAll('button');
    expect(buttons).toHaveLength(7);
    expect(buttons[0].attributes('aria-label')).toBe('Play Bataille Corse');
    expect(buttons[1].attributes('aria-label')).toBe('Play Bullshit');
    expect(buttons[2].attributes('aria-label')).toBe('Play Kobo');
    expect(buttons[3].attributes('aria-label')).toBe('Play Morris');
    expect(buttons[4].attributes('aria-label')).toBe('Play President');
    expect(buttons[5].attributes('aria-label')).toBe('Play Mangola');
    expect(buttons[6].attributes('aria-label')).toBe('Play 99');
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
    expect(wrapper.findAll('button')).toHaveLength(7);
    await link.trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/privacy');
  });
});
