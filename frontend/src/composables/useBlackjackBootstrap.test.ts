import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { defineComponent, h } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import { resolveBlackjackSession, useBlackjackBootstrap } from './useBlackjackBootstrap';
import { useBlackjackStore } from '../state/Blackjack.store';
import { findGame, type GameDefinition } from '../model/games';

// Blackjack is not on the Game selection yet (the entry PR registers it): a definition built by hand stands in.
const blackjack = {
  ...findGame('bullshit')!,
  key: 'blackjack',
  gameHomeRoute: '/blackjack',
  joinRoute: (id?: string) => (id ? `/blackjack/join/${id}` : '/blackjack/join'),
  roomRoute: (id: string) => `/blackjack/room/${id}`,
} as GameDefinition;

describe('resolveBlackjackSession', () => {
  beforeEach(() => { localStorage.clear(); });

  it('reads the seat and token stored under blackjack:tokens:', () => {
    localStorage.setItem('blackjack:tokens:g1', JSON.stringify({ 2: 'tok-2' }));
    expect(resolveBlackjackSession('g1')).toEqual({ seat: 2, token: 'tok-2' });
  });

  it('ignores the token of another game', () => {
    localStorage.setItem('kobo:tokens:g1', JSON.stringify({ 2: 'tok-2' }));
    expect(resolveBlackjackSession('g1')).toBeNull();
  });
});

describe('useBlackjackBootstrap', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  async function mountAtRoom(id: string) {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/:pathMatch(.*)*', component: { render: () => h('div') } }],
    });
    await router.push(`/blackjack/room/${id}`);
    await router.isReady();
    const Probe = defineComponent({
      setup() { useBlackjackBootstrap(id, blackjack); return () => h('div'); },
    });
    mount(Probe, { global: { plugins: [router] } });
    await flushPromises();
    return router;
  }

  it('without a stored token leads to the join form', async () => {
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/blackjack/join/g1');
  });

  it('restores the stored seat into the store and hydrates', async () => {
    localStorage.setItem('blackjack:tokens:g1', JSON.stringify({ 1: 'tok' }));
    const store = useBlackjackStore();
    const restore = vi.spyOn(store, 'restore').mockImplementation(() => {});
    const hydrate = vi.spyOn(store, 'hydrate').mockResolvedValue('ok');
    const router = await mountAtRoom('g1');
    expect(restore).toHaveBeenCalledWith('g1', 1, 'tok');
    expect(hydrate).toHaveBeenCalled();
    expect(router.currentRoute.value.path).toBe('/blackjack/room/g1');
  });

  it('a game that is gone leads to the home and forgets the token', async () => {
    localStorage.setItem('blackjack:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const store = useBlackjackStore();
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('gone');
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/blackjack');
    expect(localStorage.getItem('blackjack:tokens:g1')).toBeNull();
  });
});
