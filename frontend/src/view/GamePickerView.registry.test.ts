import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import { createMemoryHistory } from 'vue-router';

// The picker lists whatever the registry holds: with a single registered game, a single card.
vi.mock('../model/games', async (importOriginal) => {
  const original = await importOriginal<typeof import('../model/games')>();
  return { ...original, GAMES: original.GAMES.filter(g => g.key === 'bullshit') };
});

import GamePickerView from './GamePickerView.vue';
import { createAppRouter } from '../router';

describe('GamePickerView and the registry', () => {
  it('givenARegistryWithOneGame_thenThePickerOffersOnlyThatGame', async () => {
    const router = createAppRouter(createMemoryHistory());
    await router.push('/');
    await router.isReady();
    const wrapper = mount(GamePickerView, { global: { plugins: [router, PrimeVue] } });
    expect(wrapper.findAll('article')).toHaveLength(1);
    expect(wrapper.find('[data-test="choice-bullshit"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="choice-bataillecorse"]').exists()).toBe(false);
  });

  it('givenARegistryWithOnlyCardGames_thenTheBoardSectionAndItsChipAreHidden', async () => {
    const router = createAppRouter(createMemoryHistory());
    await router.push('/');
    await router.isReady();
    const wrapper = mount(GamePickerView, { global: { plugins: [router, PrimeVue] } });
    expect(wrapper.find('#cartes').exists()).toBe(true);
    expect(wrapper.find('#plateau').exists()).toBe(false);
    expect(wrapper.findAll('nav a').map(a => a.attributes('href'))).toEqual(['#cartes']);
  });
});
