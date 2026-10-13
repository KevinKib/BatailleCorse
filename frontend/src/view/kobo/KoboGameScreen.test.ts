import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, RouterView } from 'vue-router';
import { defineComponent, h } from 'vue';
import PrimeVue from 'primevue/config';
import { createAppRouter } from '../../router';
import { useKoboStore } from '../../state/Kobo.store';
import type { LobbyView } from '../../model/lobby/LobbyView';
import { buildKoboState } from '../../model/fixtures/kobo';
import { messagesEn } from '../../locales/en';

function lobby(joined: number, mySeat = 0): LobbyView {
  return {
    started: false, gameId: 'k1',
    players: Array.from({ length: joined }, (_, seat) => ({ seat, name: `P${seat}`, joined: true })),
    hostSeat: 0, mySeat, minPlayers: 2, maxPlayers: 6, canStart: joined >= 2, lobbyVersion: 1,
  };
}

async function mountRoom(setup: (store: ReturnType<typeof useKoboStore>) => void) {
  const store = useKoboStore();
  localStorage.setItem('kobo:tokens:k1', JSON.stringify({ 0: 'tok' }));
  setup(store);
  vi.spyOn(store, 'hydrate').mockImplementation(() => {});
  vi.spyOn(store, 'restore').mockImplementation(() => {});
  const router = createAppRouter(createMemoryHistory());
  await router.push('/games/kobo/room/k1');
  await router.isReady();
  const Root = defineComponent({ render: () => h(RouterView) });
  const wrapper = mount(Root, { global: { plugins: [router, PrimeVue] } });
  await flushPromises();
  return { store, router, wrapper };
}

describe('KoboGameScreen', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))); setActivePinia(createPinia()); localStorage.clear(); });

  it('givenNoStateYet_thenShowsTheConnectingScreen', async () => {
    const { wrapper } = await mountRoom(() => {});
    expect(wrapper.get('[data-test="connecting"]').text()).toContain(messagesEn.koboUi.connecting);
    expect(wrapper.find('[data-test="lobby"]').exists()).toBe(false);
  });

  it('givenALobby_thenShowsTheSharedLobbyForKoboWithoutAddBot', async () => {
    const { wrapper } = await mountRoom(store => {
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: lobby(2) });
    });
    expect(wrapper.get('[data-test="lobby"]').text()).toContain('Kobo');
    expect(wrapper.get('[data-test="player-count"]').text()).toContain('2 / 6');
    expect(wrapper.find('[data-test="add-bot"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="start"]').attributes('disabled')).toBeUndefined();
  });

  it('givenTheHostPressesStart_thenTheStoreStartsTheGame', async () => {
    const { wrapper, store } = await mountRoom(s => {
      s.applyEvent({ type: 'seat-change', seat: 0 });
      s.applyEvent({ type: 'state-update', state: lobby(3) });
    });
    const start = vi.spyOn(store, 'startGame').mockImplementation(() => {});
    await wrapper.get('[data-test="start"]').trigger('click');
    expect(start).toHaveBeenCalled();
  });

  it('givenALobbyLeave_thenGoesToTheGameSelection', async () => {
    const { wrapper, store, router } = await mountRoom(s => {
      s.applyEvent({ type: 'seat-change', seat: 0 });
      s.applyEvent({ type: 'state-update', state: lobby(2) });
    });
    vi.spyOn(store, 'leave').mockReturnValue(true);
    await wrapper.get('[data-test="leave-lobby"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.name).toBe('games');
  });

  it('givenAStartedGame_thenShowsTheProvisionalScreenWithBackAndNoLobby', async () => {
    const { wrapper, router, store } = await mountRoom(s => {
      s.applyEvent({ type: 'seat-change', seat: 0 });
      s.applyEvent({ type: 'state-update', state: buildKoboState({ round: 2 }) });
    });
    expect(wrapper.get('[data-test="kobo-provisional"]').text()).toContain(messagesEn.koboUi.provisional);
    expect(wrapper.find('[data-test="lobby"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="connecting"]').exists()).toBe(false);
    window.confirm = () => true;
    vi.spyOn(store, 'forfeit').mockImplementation(() => {});
    await wrapper.get('[data-test="back"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.name).toBe('games');
  });

  it('givenTheHostRemovedMe_thenLeadsToTheGameSelectionWithTheNotice', async () => {
    const { store, router } = await mountRoom(s => {
      s.applyEvent({ type: 'seat-change', seat: 1 });
      s.applyEvent({ type: 'state-update', state: lobby(2, 1) });
    });
    store.removedByHost = true;
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/games?removed=1');
  });
});
