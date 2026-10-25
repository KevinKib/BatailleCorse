import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { defineComponent, h } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';
import type { GameDefinition } from '../model/games';
import { resolveMorrisSession, useMorrisBootstrap } from './useMorrisBootstrap';
import { useThreeMenMorrisStore } from '../state/ThreeMenMorris.store';

// The game is not in the registry yet (it joins `GAMES` with its screen): the bootstrap takes its definition.
const base = '/three-men-morris';
const game: GameDefinition = {
  key: 'three-men-morris',
  gameHomeRoute: base,
  joinRoute: (id) => (id ? `${base}/join/${id}` : `${base}/join`),
  roomRoute: (id) => `${base}/room/${id}`,
  supportsBots: false,
  picker: { icon: 'pi pi-th-large', severity: 'info' },
};

describe('resolveMorrisSession', () => {
  beforeEach(() => { localStorage.clear(); });

  it('returns the stored seat and token under the Morris key', () => {
    localStorage.setItem('three-men-morris:tokens:g1', JSON.stringify({ 1: 'tok-1' }));
    expect(resolveMorrisSession('g1')).toEqual({ seat: 1, token: 'tok-1' });
  });

  it('returns null when nothing is stored, or for another game', () => {
    localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 1: 'tok-1' }));
    expect(resolveMorrisSession('g1')).toBeNull();
  });
});

describe('useMorrisBootstrap', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  async function mountAtRoom(id: string) {
    const page = { render: () => h('div') };
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: base, component: page },
        { path: `${base}/join/:id`, component: page },
        { path: `${base}/room/:id`, component: page },
      ],
    });
    await router.push(`${base}/room/${id}`);
    await router.isReady();
    const Probe = defineComponent({
      setup() { useMorrisBootstrap(id, game); return () => h('div'); },
    });
    mount(Probe, { global: { plugins: [router] } });
    await flushPromises();
    return router;
  }

  it('givenNoStoredToken_thenGoesToTheJoinFormWithTheIdPrefilled', async () => {
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.fullPath).toBe(`${base}/join/g1`);
  });

  it('givenStoredToken_thenRestoresTheSeatAndHydrates', async () => {
    localStorage.setItem('three-men-morris:tokens:g1', JSON.stringify({ 1: 'tok' }));
    const store = useThreeMenMorrisStore();
    const restore = vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('ok');
    const router = await mountAtRoom('g1');
    expect(restore).toHaveBeenCalledWith('g1', 1, 'tok');
    expect(router.currentRoute.value.path).toBe(`${base}/room/g1`);
  });

  it('givenGameGone_thenGoesToTheGameHomeWithTheNoticeAndForgetsTheToken', async () => {
    localStorage.setItem('three-men-morris:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const store = useThreeMenMorrisStore();
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('gone');
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe(base);
    expect(router.currentRoute.value.query.gone).toBe('1');
    expect(localStorage.getItem('three-men-morris:tokens:g1')).toBeNull();
  });

  it('givenTokenRefused_thenGoesToTheGameHomeWithTheLostSeatNotice', async () => {
    localStorage.setItem('three-men-morris:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const store = useThreeMenMorrisStore();
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('lost');
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.query.lost).toBe('1');
    expect(localStorage.getItem('three-men-morris:tokens:g1')).toBeNull();
  });

  it('givenServerFailure_thenStaysOnTheRoomAndKeepsTheToken', async () => {
    localStorage.setItem('three-men-morris:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const store = useThreeMenMorrisStore();
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    vi.spyOn(store, 'hydrate').mockResolvedValue('unavailable');
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe(`${base}/room/g1`);
    expect(localStorage.getItem('three-men-morris:tokens:g1')).not.toBeNull();
  });
});
