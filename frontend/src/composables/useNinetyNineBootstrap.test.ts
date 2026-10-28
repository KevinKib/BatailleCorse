import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { defineComponent, h } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { resolveNinetyNineSession, useNinetyNineBootstrap } from './useNinetyNineBootstrap';
import { useNinetyNineStore } from '../state/NinetyNine.store';
import { findGame, type GameDefinition } from '../model/games';

// 99 is not on the Game selection yet (the entry PR registers it): a definition built by hand stands in.
const ninetyNine = {
  ...findGame('bullshit')!,
  key: 'ninety-nine',
  gameHomeRoute: '/ninety-nine',
  joinRoute: (id?: string) => (id ? `/ninety-nine/join/${id}` : '/ninety-nine/join'),
  roomRoute: (id: string) => `/ninety-nine/room/${id}`,
} as GameDefinition;

describe('resolveNinetyNineSession', () => {
  beforeEach(() => { localStorage.clear(); });

  it('reads the seat and token stored under ninety-nine:tokens:', () => {
    localStorage.setItem('ninety-nine:tokens:g1', JSON.stringify({ 2: 'tok-2' }));
    expect(resolveNinetyNineSession('g1')).toEqual({ seat: 2, token: 'tok-2' });
  });

  it('ignores the token of another game', () => {
    localStorage.setItem('kobo:tokens:g1', JSON.stringify({ 2: 'tok-2' }));
    expect(resolveNinetyNineSession('g1')).toBeNull();
  });
});

describe('useNinetyNineBootstrap', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  async function mountAtRoom(id: string) {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:pathMatch(.*)*', component: { render: () => h('div') } }],
    });
    await router.push(`/ninety-nine/room/${id}`);
    await router.isReady();
    const Probe = defineComponent({
      setup() { useNinetyNineBootstrap(id, ninetyNine); return () => h('div'); },
    });
    mount(Probe, { global: { plugins: [router] } });
    await flushPromises();
    return router;
  }

  it('without a stored token leads to the join form', async () => {
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/ninety-nine/join/g1');
  });

  it('restores the stored seat into the store and hydrates', async () => {
    localStorage.setItem('ninety-nine:tokens:g1', JSON.stringify({ 1: 'tok' }));
    const store = useNinetyNineStore();
    const restore = vi.spyOn(store, 'restore').mockImplementation(() => {});
    const hydrate = vi.spyOn(store, 'hydrate').mockResolvedValue('ok');
    const router = await mountAtRoom('g1');
    expect(restore).toHaveBeenCalledWith('g1', 1, 'tok');
    expect(hydrate).toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe('/ninety-nine/room/g1');
  });

  it('a game that is gone leads to the home and forgets the token', async () => {
    localStorage.setItem('ninety-nine:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const store = useNinetyNineStore();
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('gone');
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/ninety-nine');
    expect(localStorage.getItem('ninety-nine:tokens:g1')).toBeNull();
  });
});
