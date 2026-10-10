import { describe, it, expect } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import { createAppRouter } from '../router';
import { createMemoryHistory } from 'vue-router';
import GamePickerView from './GamePickerView.vue';
import { messagesEn } from '../locales/en';

async function mountPicker() {
  const router = createAppRouter(createMemoryHistory());
  await router.push('/games');
  await router.isReady();
  const wrapper = mount(GamePickerView, { global: { plugins: [router, PrimeVue] } });
  return { router, wrapper };
}

describe('GamePickerView', () => {
  it('givenPicker_thenShowsTitleAndBothGamesWithDescriptionAndPlayerCount', async () => {
    const { wrapper } = await mountPicker();
    const text = wrapper.text();
    const p = messagesEn.gamePicker;
    expect(wrapper.get('h1').text()).toBe(p.title);
    for (const game of [p.bataillecorse, p.bullshit]) {
      expect(text).toContain(game.name);
      expect(text).toContain(game.description);
      expect(text).toContain(game.players);
    }
    expect(wrapper.findAll('article')).toHaveLength(2);
  });

  it('givenPicker_thenEachCardHasAKeyboardReachablePlayButtonNamedAfterItsGame', async () => {
    const { wrapper } = await mountPicker();
    const buttons = wrapper.findAll('button');
    expect(buttons).toHaveLength(2);
    expect(buttons[0].attributes('aria-label')).toBe('Play Bataille Corse');
    expect(buttons[1].attributes('aria-label')).toBe('Play Bullshit');
  });

  it('givenPlayOnBatailleCorse_thenGoesToItsMenu', async () => {
    const { wrapper, router } = await mountPicker();
    await wrapper.get('[data-test="play-bataillecorse"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/games/bataillecorse');
  });

  it('givenPlayOnBullshit_thenGoesToItsCreateScreen', async () => {
    const { wrapper, router } = await mountPicker();
    await wrapper.get('[data-test="play-bullshit"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/games/bullshit/create');
  });
});
