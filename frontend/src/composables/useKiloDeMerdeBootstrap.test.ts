import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { defineComponent, h } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { resolveKiloDeMerdeSession, useKiloDeMerdeBootstrap } from './useKiloDeMerdeBootstrap';
import { useKiloDeMerdeStore } from '../state/KiloDeMerde.store';
import { findGame, type GameDefinition } from '../model/games';

// Kilo de merde is not on the Game selection yet (the entry PR registers it): a definition built by hand stands in.
const kilo = {
  ...findGame('bullshit')!,
  key: 'kilo-de-merde',
  gameHomeRoute: '/kilo-de-merde',
  joinRoute: (id?: string) => (id ? `/kilo-de-merde/join/${id}` : '/kilo-de-merde/join'),
  roomRoute: (id: string) => `/kilo-de-merde/room/${id}`,
} as GameDefinition;

describe('resolveKiloDeMerdeSession', () => {
  beforeEach(() => { localStorage.clear(); });

  it('reads the seat and token stored under kilo-de-merde:tokens:', () => {
    localStorage.setItem('kilo-de-merde:tokens:g1', JSON.stringify({ 2: 'tok-2' }));
    expect(resolveKiloDeMerdeSession('g1')).toEqual({ seat: 2, token: 'tok-2' });
  });

  it('ignores the token of another game', () => {
    localStorage.setItem('kobo:tokens:g1', JSON.stringify({ 2: 'tok-2' }));
    expect(resolveKiloDeMerdeSession('g1')).toBeNull();
  });
});

describe('useKiloDeMerdeBootstrap', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  async function mountAtRoom(id: string) {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:pathMatch(.*)*', component: { render: () => h('div') } }],
    });
    await router.push(`/kilo-de-merde/room/${id}`);
    await router.isReady();
    const Probe = defineComponent({
      setup() { useKiloDeMerdeBootstrap(id, kilo); return () => h('div'); },
    });
    mount(Probe, { global: { plugins: [router] } });
    await flushPromises();
    return router;
  }

  it('without a stored token leads to the join form', async () => {
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/kilo-de-merde/join/g1');
  });

  it('restores the stored seat into the store and hydrates', async () => {
    localStorage.setItem('kilo-de-merde:tokens:g1', JSON.stringify({ 1: 'tok' }));
    const store = useKiloDeMerdeStore();
    const restore = vi.spyOn(store, 'restore').mockImplementation(() => {});
    const hydrate = vi.spyOn(store, 'hydrate').mockResolvedValue('ok');
    const router = await mountAtRoom('g1');
    expect(restore).toHaveBeenCalledWith('g1', 1, 'tok');
    expect(hydrate).toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe('/kilo-de-merde/room/g1');
  });

  it('a game that is gone leads to the home and forgets the token', async () => {
    localStorage.setItem('kilo-de-merde:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const store = useKiloDeMerdeStore();
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('gone');
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/kilo-de-merde');
    expect(localStorage.getItem('kilo-de-merde:tokens:g1')).toBeNull();
  });
});
