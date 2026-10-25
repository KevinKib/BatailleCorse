import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createMemoryHistory } from 'vue-router';
import { createPinia, setActivePinia } from 'pinia';
import PrimeVue from 'primevue/config';
import GameJoinView from './GameJoinView.vue';
import { createAppRouter } from '../router';
import { useBullshitStore } from '../state/Bullshit.store';
import { useKoboStore } from '../state/Kobo.store';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';
import * as rooms from '../composables/useGameRooms';

async function mountJoin(path: string) {
  const router = createAppRouter(createMemoryHistory());
  await router.push(path);
  await router.isReady();
  // The join form navigates to the room once seated; the room screens are not under test here.
  vi.spyOn(router, 'push').mockResolvedValue(undefined);
  const wrapper = mount(GameJoinView, { global: { plugins: [router, PrimeVue] } });
  return { wrapper, router };
}

describe('GameJoinView', () => {
  beforeEach(() => {
    setActivePinia(createPinia()); localStorage.clear(); vi.restoreAllMocks();
    // The public list fetches its snapshot on mount: keep the other tests off the network.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500, json: async () => ({}) }));
  });
  afterEach(() => { vi.unstubAllGlobals(); });

  it('givenABullshitInviteLink_thenJoinsThroughTheBullshitStore', async () => {
    const bullshit = vi.spyOn(useBullshitStore(), 'join').mockResolvedValue();
    const bataille = vi.spyOn(useBatailleCorseStore(), 'join').mockResolvedValue();
    const { wrapper } = await mountJoin('/bullshit/join/abc');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(bullshit).toHaveBeenCalledWith('abc', undefined);
    expect(bataille).not.toHaveBeenCalled();
  });

  it('givenABatailleCorseInviteLink_thenJoinsThroughTheBatailleCorseStore', async () => {
    const bullshit = vi.spyOn(useBullshitStore(), 'join').mockResolvedValue();
    const bataille = vi.spyOn(useBatailleCorseStore(), 'join').mockResolvedValue();
    const { wrapper } = await mountJoin('/bataillecorse/join/xyz');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(bataille).toHaveBeenCalledWith('xyz', undefined);
    expect(bullshit).not.toHaveBeenCalled();
  });

  it('givenAKoboInviteLink_thenJoinsThroughTheKoboStore', async () => {
    const kobo = vi.spyOn(useKoboStore(), 'join').mockResolvedValue();
    const bullshit = vi.spyOn(useBullshitStore(), 'join').mockResolvedValue();
    const { wrapper } = await mountJoin('/kobo/join/k1');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(kobo).toHaveBeenCalledWith('k1', undefined);
    expect(bullshit).not.toHaveBeenCalled();
  });

  it('givenTheRoomRegistry_thenTheViewJoinsThroughItAndNotThroughAHardCodedStore', async () => {
    const join = vi.fn().mockResolvedValue(undefined);
    const spy = vi.spyOn(rooms, 'useGameRooms').mockReturnValue(() => ({ create: vi.fn(), join }));
    const { wrapper } = await mountJoin('/bullshit/join/abc');
    await wrapper.get('form').trigger('submit');
    await flushPromises();
    expect(spy).toHaveBeenCalled();
    expect(join).toHaveBeenCalledWith('abc', undefined);
  });

  it('showsThePublicGamesOfTheGameAndJoinsFromARow', async () => {
    const snapshot = {
      gameType: 'kobo', total: 1, truncated: false, version: 1,
      lobbies: [{ gameId: 'k9', gameType: 'kobo', hostName: 'Zoe', joined: 1, bots: 0, maxPlayers: 4, state: 'OPEN' }],
    };
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => snapshot });
    vi.stubGlobal('fetch', fetchMock);
    const kobo = vi.spyOn(useKoboStore(), 'join').mockResolvedValue();
    const { wrapper } = await mountJoin('/kobo/join');
    await flushPromises();
    expect(fetchMock).toHaveBeenCalledWith('/api/lobbies/kobo');
    await wrapper.get('[data-test="pg-join"]').trigger('click');
    await flushPromises();
    expect(kobo).toHaveBeenCalledWith('k9', undefined);
    vi.unstubAllGlobals();
  });
});
