import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { defineComponent, h } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { resolvePresidentSession, usePresidentBootstrap } from './usePresidentBootstrap';
import { usePresidentStore } from '../state/President.store';
import { findGame, type GameDefinition } from '../model/games';

// President is not on the Game selection yet (the entry PR registers it): a definition built by hand stands in.
const president = {
  ...findGame('bullshit')!,
  key: 'president',
  gameHomeRoute: '/president',
  joinRoute: (id?: string) => (id ? `/president/join/${id}` : '/president/join'),
  roomRoute: (id: string) => `/president/room/${id}`,
} as GameDefinition;

describe('resolvePresidentSession', () => {
  beforeEach(() => { localStorage.clear(); });

  it('reads the seat and token stored under president:tokens:', () => {
    localStorage.setItem('president:tokens:g1', JSON.stringify({ 2: 'tok-2' }));
    expect(resolvePresidentSession('g1')).toEqual({ seat: 2, token: 'tok-2' });
  });

  it('ignores the token of another game', () => {
    localStorage.setItem('kobo:tokens:g1', JSON.stringify({ 2: 'tok-2' }));
    expect(resolvePresidentSession('g1')).toBeNull();
  });
});

describe('usePresidentBootstrap', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  async function mountAtRoom(id: string) {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:pathMatch(.*)*', component: { render: () => h('div') } }],
    });
    await router.push(`/president/room/${id}`);
    await router.isReady();
    const Probe = defineComponent({
      setup() { usePresidentBootstrap(id, president); return () => h('div'); },
    });
    mount(Probe, { global: { plugins: [router] } });
    await flushPromises();
    return router;
  }

  it('without a stored token leads to the President join form', async () => {
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/president/join/g1');
  });

  it('restores the stored seat into the President store and hydrates', async () => {
    localStorage.setItem('president:tokens:g1', JSON.stringify({ 1: 'tok' }));
    const store = usePresidentStore();
    const restore = vi.spyOn(store, 'restore').mockImplementation(() => {});
    const hydrate = vi.spyOn(store, 'hydrate').mockResolvedValue('ok');
    const router = await mountAtRoom('g1');
    expect(restore).toHaveBeenCalledWith('g1', 1, 'tok');
    expect(hydrate).toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe('/president/room/g1');
  });

  it('a game that is gone leads to the President home and forgets the token', async () => {
    localStorage.setItem('president:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const store = usePresidentStore();
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('gone');
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/president');
    expect(router.currentRoute.value.query.gone).toBe('1');
    expect(localStorage.getItem('president:tokens:g1')).toBeNull();
  });
});
