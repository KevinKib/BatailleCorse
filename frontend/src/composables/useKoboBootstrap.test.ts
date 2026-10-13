import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { defineComponent, h } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { resolveKoboSession, useKoboBootstrap } from './useKoboBootstrap';
import { useKoboStore } from '../state/Kobo.store';
import { findGame, type GameDefinition } from '../model/games';

// Kobo is not on the Game selection yet (its screen comes later): a definition built by hand stands in for it.
const kobo = { ...findGame('bullshit')!, key: 'kobo', gameHomeRoute: '/games/kobo', joinRoute: (id?: string) => (id ? `/games/kobo/join/${id}` : '/games/kobo/join'), roomRoute: (id: string) => `/games/kobo/room/${id}` } as GameDefinition;

describe('resolveKoboSession', () => {
  beforeEach(() => { localStorage.clear(); });

  it('reads the seat and token stored under kobo:tokens:', () => {
    localStorage.setItem('kobo:tokens:g1', JSON.stringify({ 2: 'tok-2' }));
    expect(resolveKoboSession('g1')).toEqual({ seat: 2, token: 'tok-2' });
  });

  it('ignores the token of another game', () => {
    localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 2: 'tok-2' }));
    expect(resolveKoboSession('g1')).toBeNull();
  });
});

describe('useKoboBootstrap', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  async function mountAtRoom(id: string) {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:pathMatch(.*)*', component: { render: () => h('div') } }],
    });
    await router.push(`/games/kobo/room/${id}`);
    await router.isReady();
    const Probe = defineComponent({
      setup() { useKoboBootstrap(id, kobo); return () => h('div'); },
    });
    mount(Probe, { global: { plugins: [router] } });
    await flushPromises();
    return router;
  }

  it('without a stored token leads to the Kobo join form', async () => {
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/games/kobo/join/g1');
  });

  it('restores the stored seat into the Kobo store and hydrates', async () => {
    localStorage.setItem('kobo:tokens:g1', JSON.stringify({ 1: 'tok' }));
    const store = useKoboStore();
    const restore = vi.spyOn(store, 'restore').mockImplementation(() => {});
    const hydrate = vi.spyOn(store, 'hydrate').mockResolvedValue('ok');
    const router = await mountAtRoom('g1');
    expect(restore).toHaveBeenCalledWith('g1', 1, 'tok');
    expect(hydrate).toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe('/games/kobo/room/g1');
  });

  it('a game that is gone leads to the Kobo home and forgets the token', async () => {
    localStorage.setItem('kobo:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const store = useKoboStore();
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('gone');
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/games/kobo');
    expect(router.currentRoute.value.query.gone).toBe('1');
    expect(localStorage.getItem('kobo:tokens:g1')).toBeNull();
  });
});
