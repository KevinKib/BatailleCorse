import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { defineComponent, h } from 'vue';
import { createMemoryHistory } from 'vue-router';
import { createAppRouter } from '../router';
import { resolveBullshitSession, useBullshitBootstrap } from './useBullshitBootstrap';
import { useBullshitStore } from '../state/Bullshit.store';

describe('resolveBullshitSession', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  it('returns the stored seat and token for a game', () => {
    localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 1: 'tok-1' }));
    expect(resolveBullshitSession('g1')).toEqual({ seat: 1, token: 'tok-1' });
  });

  it('returns null when no token is stored', () => {
    expect(resolveBullshitSession('g1')).toBeNull();
  });

  it('givenInvalidJson_thenReturnsNullAndClearsTheEntry', () => {
    localStorage.setItem('bullshit:tokens:g1', '{not json');
    expect(resolveBullshitSession('g1')).toBeNull();
    expect(localStorage.getItem('bullshit:tokens:g1')).toBeNull();
  });

  it.each(['null', '42', '"str"', '[1,2]'])('givenNonObjectValue_%s_thenReturnsNullAndClearsTheEntry', (raw) => {
    localStorage.setItem('bullshit:tokens:g1', raw);
    expect(resolveBullshitSession('g1')).toBeNull();
    expect(localStorage.getItem('bullshit:tokens:g1')).toBeNull();
  });
});

describe('useBullshitBootstrap restore failures', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  async function mountAtRoom(id: string) {
    const router = createAppRouter(createMemoryHistory());
    await router.push(`/games/bullshit/room/${id}`);
    await router.isReady();
    const Probe = defineComponent({
      setup() { useBullshitBootstrap(id); return () => h('div'); },
    });
    mount(Probe, { global: { plugins: [router] } });
    await flushPromises();
    return router;
  }

  it('givenNoStoredToken_thenGoesToTheJoinFormWithTheIdPrefilled', async () => {
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.fullPath).toBe('/games/bullshit/join/g1');
  });

  it.each(['{not json', '42'])('givenCorruptedStoredToken_%s_thenGoesToTheJoinFormAndForgetsIt', async (raw) => {
    localStorage.setItem('bullshit:tokens:g1', raw);
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.fullPath).toBe('/games/bullshit/join/g1');
    expect(localStorage.getItem('bullshit:tokens:g1')).toBeNull();
  });

  it('givenStoredTokenButGameGone_thenGoesToTheGameHomeWithTheNoticeAndForgetsTheToken', async () => {
    localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const store = useBullshitStore();
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('gone');
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/games/bullshit');
    expect(router.currentRoute.value.query.gone).toBe('1');
    expect(localStorage.getItem('bullshit:tokens:g1')).toBeNull();
  });

  it('givenStoredTokenRefused_thenGoesToTheGameHomeWithTheLostSeatNoticeAndForgetsTheToken', async () => {
    localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const store = useBullshitStore();
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('lost');
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/games/bullshit');
    expect(router.currentRoute.value.query.lost).toBe('1');
    expect(router.currentRoute.value.query.gone).toBeUndefined();
    expect(localStorage.getItem('bullshit:tokens:g1')).toBeNull();
  });

  it('givenServerFailure_thenStaysOnTheRoomAndKeepsTheToken', async () => {
    localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const store = useBullshitStore();
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('unavailable');
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/games/bullshit/room/g1');
    expect(localStorage.getItem('bullshit:tokens:g1')).not.toBeNull();
  });

  it('givenStoredTokenAndGameAlive_thenStaysOnTheRoom', async () => {
    localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const store = useBullshitStore();
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('ok');
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/games/bullshit/room/g1');
  });
});
