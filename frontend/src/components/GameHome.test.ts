import { describe, it, expect, beforeEach } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory } from 'vue-router';
import PrimeVue from 'primevue/config';
import GameHome from './GameHome.vue';
import { createAppRouter } from '../router';
import { findGame } from '../model/games';
import { useSettingsStore } from '../state/Settings.store';
import { messagesEn } from '../locales/en';

async function mountHome(key: 'bataillecorse' | 'bullshit', query = '',
  createLobby: (name?: string) => Promise<string> = vi.fn().mockResolvedValue('g1')) {
  const router = createAppRouter(createMemoryHistory());
  await router.push(`/${key}${query}`);
  await router.isReady();
  const wrapper = mount(GameHome, {
    props: { game: findGame(key)!, createLobby },
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

    it(`given_${key}_whenNewGame_thenCreatesTheLobbyAndLandsInItsRoom`, async () => {
      const createLobby = vi.fn().mockResolvedValue('g7');
      const { wrapper, router } = await mountHome(key, '', createLobby);
      await wrapper.get('[data-test="new-game"]').trigger('click');
      await flushPromises();
      expect(createLobby).toHaveBeenCalledTimes(1);
      expect(router.currentRoute.value.path).toBe(`/${key}/room/g7`);
    });

    it(`given_${key}_whenJoinGame_thenLeadsToItsJoinForm`, async () => {
      const { wrapper, router } = await mountHome(key);
      await wrapper.get('[data-test="join-game"]').trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.path).toBe(`/${key}/join`);
    });

    it(`given_${key}_whenBack_thenLeadsToTheGameSelection`, async () => {
      const { wrapper, router } = await mountHome(key);
      await wrapper.get('[data-test="back"]').trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.path).toBe('/');
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

  it('givenBullshit_whenNewGame_thenCreatesTheLobbyWithTheNameAndEntersIt', async () => {
    localStorage.setItem('bc_playerName', 'Alice');
    setActivePinia(createPinia());
    const createLobby = vi.fn().mockResolvedValue('g42');
    const { wrapper, router } = await mountHome('bullshit', '', createLobby);
    await wrapper.get('[data-test="new-game"]').trigger('click');
    await flushPromises();
    expect(createLobby).toHaveBeenCalledWith('Alice');
    expect(router.currentRoute.value.path).toBe('/bullshit/room/g42');
  });

  it('givenBlankName_whenNewGame_thenCreatesTheLobbyWithoutName', async () => {
    const createLobby = vi.fn().mockResolvedValue('g42');
    const { wrapper } = await mountHome('bullshit', '', createLobby);
    await wrapper.get('[data-test="new-game"]').trigger('click');
    await flushPromises();
    expect(createLobby).toHaveBeenCalledWith(undefined);
  });

  it('givenSlowCreation_thenNewGameIsDisabledUntilItAnswers', async () => {
    let resolve!: (id: string) => void;
    const createLobby = vi.fn().mockReturnValue(new Promise<string>((r) => { resolve = r; }));
    const { wrapper } = await mountHome('bullshit', '', createLobby);
    await wrapper.get('[data-test="new-game"]').trigger('click');
    const button = wrapper.get('[data-test="new-game"]');
    expect((button.element as HTMLButtonElement).disabled).toBe(true);
    expect(button.text()).toBe(messagesEn.home.creating);
    await button.trigger('click');
    expect(createLobby).toHaveBeenCalledTimes(1);
    resolve('g1');
    await flushPromises();
  });

  it('givenFailedCreation_thenStaysOnTheGameHomeWithAnAlert', async () => {
    const createLobby = vi.fn().mockRejectedValue(new Error('Create failed: 500'));
    const { wrapper, router } = await mountHome('bullshit', '', createLobby);
    await wrapper.get('[data-test="new-game"]').trigger('click');
    await flushPromises();
    const alert = wrapper.get('[data-test="create-error"]');
    expect(alert.text()).toBe(messagesEn.home.createFailed);
    expect(alert.attributes('role')).toBe('alert');
    expect(router.currentRoute.value.path).toBe('/bullshit');
    expect((wrapper.get('[data-test="new-game"]').element as HTMLButtonElement).disabled).toBe(false);
  });

  it('givenLostSeatNotice_thenItIsAnnounced', async () => {
    const { wrapper } = await mountHome('bullshit', '?lost=1');
    expect(wrapper.get('[data-test="lost-notice"]').text()).toBe(messagesEn.home.seatLost);
    expect(wrapper.get('[data-test="lost-notice"]').attributes('role')).toBe('status');
    expect(wrapper.find('[data-test="gone-notice"]').exists()).toBe(false);
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

describe('GameHome local preferences', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  for (const [key, actions] of [['bataillecorse', ['send', 'slap']], ['bullshit', ['discard', 'call']]] as const) {
    it(`given_${key}_thenAGearOpensItsShortcutsBeforeAnyGameStarts`, async () => {
      const { wrapper } = await mountHome(key);
      const gear = wrapper.get('[data-test="hotkeys-button"]');
      expect(gear.attributes('aria-label')).toBe(messagesEn.hotkeys.open);
      await gear.trigger('click');
      for (const id of actions) expect(wrapper.find(`[data-test="hotkey-${id}-key"]`).exists()).toBe(true);
    });
  }

  it('givenTheGear_thenItLivesInsideThePanelSoItNeverMovesTheLayout', async () => {
    const { wrapper } = await mountHome('bullshit');
    expect(wrapper.get('[data-test="start-panel"]').find('[data-test="hotkeys-button"]').exists()).toBe(true);
  });
});
