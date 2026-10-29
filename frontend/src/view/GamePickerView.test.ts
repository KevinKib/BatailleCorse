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
    for (const game of [p.games.bataillecorse!, p.games.bullshit!, p.games.kobo!, p.games['three-men-morris']!, p.games.president!, p.games.mangola!, p.games['ninety-nine']!, p.games.draughts!, p.games.blackjack!, p.games['kilo-de-merde']!]) {
      expect(text).toContain(game.name);
      expect(text).toContain(game.description);
      expect(text).toContain(game.players);
    }
    expect(wrapper.findAll('article')).toHaveLength(10);
  });

  it('givenPicker_thenEachCardHasAKeyboardReachablePlayButtonNamedAfterItsGame', async () => {
    const { wrapper } = await mountPicker();
    const buttons = wrapper.findAll('button');
    expect(buttons).toHaveLength(10);
    // Card games first, then board games; the registry order inside each section.
    expect(buttons.map(b => b.attributes('aria-label'))).toEqual([
      'Play Bataille Corse', 'Play Bullshit', 'Play Kobo', 'Play President', 'Play 99', 'Play Blackjack', 'Play Kilo de merde',
      'Play Morris', 'Play Mangola', 'Play Draughts',
    ]);
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

  it('givenPlayOnBlackjack_thenGoesToItsGameHome', async () => {
    const { wrapper, router } = await mountPicker();
    await wrapper.get('[data-test="play-blackjack"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/blackjack');
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
    expect(wrapper.findAll('button')).toHaveLength(10);
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
    for (const key of ['bataillecorse', 'bullshit', 'kobo', 'three-men-morris', 'president', 'mangola', 'ninety-nine', 'blackjack', 'kilo-de-merde']) {
      const button = wrapper.get(`[data-test="play-${key}"]`);
      expect(button.find('span.pi').exists()).toBe(true);
      expect(button.find('svg').exists()).toBe(false);
    }
  });

  describe('categories', () => {
    const p = messagesEn.gamePicker;

    it('givenPicker_thenTheH1IsTheSiteNameAndTheGamesAreInTwoSectionsInOrder', async () => {
      const { wrapper } = await mountPicker();
      expect(wrapper.get('h1').text()).toBe('CardGamesZone');
      const sections = wrapper.findAll('section[data-test^="section-"]');
      expect(sections.map(s => s.attributes('id'))).toEqual(['cartes', 'plateau']);
      expect(sections.map(s => s.get('h2').text())).toEqual([`${p.categories.cards}7`, `${p.categories.board}3`]);
      expect(sections.map(s => s.attributes('aria-label'))).toEqual([p.categories.cards, p.categories.board]);
    });

    it('givenPicker_thenEachSectionListsItsGamesInRegistryOrder', async () => {
      const { wrapper } = await mountPicker();
      const keys = (id: string) => wrapper.findAll(`#${id} article`).map(a => a.attributes('data-test'));
      expect(keys('cartes')).toEqual(['bataillecorse', 'bullshit', 'kobo', 'president', 'ninety-nine', 'blackjack', 'kilo-de-merde'].map(k => `choice-${k}`));
      expect(keys('plateau')).toEqual(['three-men-morris', 'mangola', 'draughts'].map(k => `choice-${k}`));
    });

    it('givenGameCards_thenTheirNamesAreH3UnderTheSectionH2', async () => {
      const { wrapper } = await mountPicker();
      expect(wrapper.findAll('article h2')).toHaveLength(0);
      expect(wrapper.findAll('article h3')).toHaveLength(10);
    });

    it('givenPicker_thenAnAccessibleNavOffersOneChipPerSectionWithItsCount', async () => {
      const { wrapper } = await mountPicker();
      const nav = wrapper.get('nav');
      expect(nav.attributes('aria-label')).toBe(p.navLabel);
      const chips = nav.findAll('a');
      expect(chips.map(c => c.attributes('href'))).toEqual(['#cartes', '#plateau']);
      expect(chips.map(c => c.text())).toEqual([`${p.categories.cards}7`, `${p.categories.board}3`]);
    });

    it('givenAChip_thenItScrollsSmoothlyToItsSection', async () => {
      const { wrapper } = await mountPicker();
      const target = wrapper.get('#plateau').element as HTMLElement;
      const scrollIntoView = vi.fn();
      target.scrollIntoView = scrollIntoView;
      await wrapper.findAll('nav a')[1].trigger('click');
      expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
    });

    it('givenReducedMotion_thenTheChipJumpsWithoutSmoothScrolling', async () => {
      const original = window.matchMedia;
      window.matchMedia = ((q: string) => ({ matches: q.includes('reduce'), media: q })) as unknown as typeof window.matchMedia;
      try {
        const { wrapper } = await mountPicker();
        const target = wrapper.get('#cartes').element as HTMLElement;
        const scrollIntoView = vi.fn();
        target.scrollIntoView = scrollIntoView;
        await wrapper.findAll('nav a')[0].trigger('click');
        expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'auto', block: 'start' });
      } finally {
        window.matchMedia = original;
      }
    });
  });
});
