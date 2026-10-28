import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { defineComponent, h } from 'vue';
import { createMemoryHistory } from 'vue-router';
import { createAppRouter } from '../router';

vi.mock('../service/WebSocketService', () => ({
  default: {
    subscribeToGame: vi.fn(), unsubscribeFromGame: vi.fn(), subscribeToSeat: vi.fn(), unsubscribeFromSeat: vi.fn(),
    setPresence: vi.fn(), clearPresence: vi.fn(), publish: vi.fn(),
  },
}));

import { useBatailleCorseBootstrap } from './useBatailleCorseBootstrap';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';

async function mountAtRoom(id: string) {
  const router = createAppRouter(createMemoryHistory());
  await router.push(`/bataillecorse/room/${id}`);
  await router.isReady();
  const Probe = defineComponent({
    setup() { useBatailleCorseBootstrap(id, () => {}); return () => h('div'); },
  });
  mount(Probe, { global: { plugins: [router] } });
  await flushPromises();
  return router;
}

describe('useBatailleCorseBootstrap restore failures', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });
  afterEach(() => { vi.unstubAllGlobals(); });

  it('givenNoStoredToken_thenGoesToTheJoinFormWithTheIdPrefilled', async () => {
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.fullPath).toBe('/bataillecorse/join/g1');
  });

  it.each(['{not json', '42', 'null', '[1]'])('givenCorruptedStoredToken_%s_thenGoesToTheJoinFormAndForgetsIt', async (raw) => {
    localStorage.setItem('tokens:g1', raw);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.fullPath).toBe('/bataillecorse/join/g1');
    expect(localStorage.getItem('tokens:g1')).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('givenStoredTokenButGameGone_thenGoesToTheGameHomeWithTheNotice', async () => {
    localStorage.setItem('tokens:g1', JSON.stringify({ 0: 'tok' }));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404 }));
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/bataillecorse');
    expect(router.currentRoute.value.query.gone).toBe('1');
    expect(localStorage.getItem('tokens:g1')).toBeNull();
  });

  it('givenStoredTokenRefused_thenGoesToTheGameHomeWithTheLostNoticeAndForgetsIt', async () => {
    localStorage.setItem('tokens:g1', JSON.stringify({ 1: 'tok' }));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 403 }));
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/bataillecorse');
    expect(router.currentRoute.value.query.lost).toBe('1');
    expect(localStorage.getItem('tokens:g1')).toBeNull();
  });

  it('givenServerUnavailable_thenStaysOnTheRoomAndKeepsTheToken', async () => {
    localStorage.setItem('tokens:g1', JSON.stringify({ 1: 'tok' }));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 502 }));
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/bataillecorse/room/g1');
    expect(localStorage.getItem('tokens:g1')).not.toBeNull();
  });

  it('givenOneStoredSeat_thenReadsTheRoomWithItsTokenAndSitsThere', async () => {
    localStorage.setItem('tokens:g1', JSON.stringify({ 1: 'tok-1' }));
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => ({
      started: false, gameId: 'g1', players: [], hostSeat: 0, mySeat: 1, minPlayers: 2, maxPlayers: 2, canStart: false,
    }) });
    vi.stubGlobal('fetch', fetchMock);
    const router = await mountAtRoom('g1');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/game/g1?token=tok-1');
    expect(useBatailleCorseStore().myPlayerIndex).toBe(1);
    expect(useBatailleCorseStore().phase).toBe('lobby');
    expect(router.currentRoute.value.path).toBe('/bataillecorse/room/g1');
  });

  it('givenAnOldSoloEntryWithTwoTokens_thenIgnoresItAndGoesToTheJoinForm', async () => {
    localStorage.setItem('tokens:g1', JSON.stringify({ 0: 'tok-0', 1: 'tok-1' }));
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.fullPath).toBe('/bataillecorse/join/g1');
    expect(localStorage.getItem('tokens:g1')).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
