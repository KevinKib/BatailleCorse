import { describe, it, expect, beforeEach } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory } from 'vue-router';
import PrimeVue from 'primevue/config';
import GameHome from './GameHome.vue';
import { createAppRouter } from '../router';
import { findGame } from '../model/games';
import { useSettingsStore } from '../state/Settings.store';
import { messagesEn } from '../locales/en';

async function mountHome(key: 'bataillecorse' | 'bullshit', query = '') {
  const router = createAppRouter(createMemoryHistory());
  await router.push(`/games/${key}${query}`);
  await router.isReady();
  const wrapper = mount(GameHome, {
    props: { game: findGame(key)! },
    global: { plugins: [router, PrimeVue] },
  });
  return { wrapper, router };
}

describe('GameHome', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  for (const [key, title] of [['bataillecorse', 'Bataille Corse'], ['bullshit', 'Bullshit']] as const) {
    it(`given_${key}_thenShowsTitleNameFieldNewJoinAndBack`, async () => {
      const { wrapper } = await mountHome(key);
      expect(wrapper.get('h1').text()).toBe(title);
      expect(wrapper.get('label[for="home-name"]').text()).toBe(messagesEn.home.yourName);
      expect(wrapper.get('[data-test="name"]').attributes('placeholder')).toBe(messagesEn.home.namePlaceholder);
      expect(wrapper.get('[data-test="new-game"]').text()).toBe(messagesEn.home.newGame);
      expect(wrapper.get('[data-test="join-game"]').text()).toBe(messagesEn.home.joinGame);
      expect(wrapper.get('[data-test="back"]').text()).toBe(messagesEn.home.back);
    });

    it(`given_${key}_whenNewGame_thenLeadsToItsNewGamePage`, async () => {
      const { wrapper, router } = await mountHome(key);
      await wrapper.get('[data-test="new-game"]').trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.path).toBe(`/games/${key}/new`);
    });

    it(`given_${key}_whenJoinGame_thenLeadsToItsJoinForm`, async () => {
      const { wrapper, router } = await mountHome(key);
      await wrapper.get('[data-test="join-game"]').trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.path).toBe(`/games/${key}/join`);
    });

    it(`given_${key}_whenBack_thenLeadsToTheGameSelection`, async () => {
      const { wrapper, router } = await mountHome(key);
      await wrapper.get('[data-test="back"]').trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.path).toBe('/games');
    });
  }

  it('givenStoredName_thenFieldIsPrefilledAndEditsArePersisted', async () => {
    localStorage.setItem('bc_playerName', 'Alice');
    setActivePinia(createPinia());
    const { wrapper } = await mountHome('bullshit');
    const input = wrapper.get('[data-test="name"]');
    expect((input.element as HTMLInputElement).value).toBe('Alice');
    await input.setValue('Bob');
    expect(useSettingsStore().playerName).toBe('Bob');
  });

  it('givenGoneNotice_thenItIsAnnounced', async () => {
    const { wrapper } = await mountHome('bullshit', '?gone=1');
    expect(wrapper.get('[data-test="gone-notice"]').text()).toBe(messagesEn.home.gameGone);
    expect(wrapper.get('[data-test="gone-notice"]').attributes('role')).toBe('status');
  });

  it('givenNoNotice_thenNoNoticeIsShown', async () => {
    const { wrapper } = await mountHome('bullshit');
    expect(wrapper.find('[data-test="gone-notice"]').exists()).toBe(false);
  });
});
