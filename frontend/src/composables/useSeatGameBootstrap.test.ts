import { describe, it, expect, beforeEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h } from 'vue';
import { createMemoryHistory } from 'vue-router';
import { createAppRouter } from '../router';
import { findGame } from '../model/games';
import { resolveSeatSession, useSeatGameBootstrap, type SeatGameBootstrapStore } from './useSeatGameBootstrap';

// The prefix is a parameter: this one belongs to no real game.
const PREFIX = 'fake:tokens:';

describe('resolveSeatSession', () => {
  beforeEach(() => { localStorage.clear(); });

  it('returns the stored seat and token under the given prefix', () => {
    localStorage.setItem('fake:tokens:g1', JSON.stringify({ 1: 'tok-1' }));
    expect(resolveSeatSession(PREFIX, 'g1')).toEqual({ seat: 1, token: 'tok-1' });
  });

  it('returns null when nothing is stored under that prefix', () => {
    localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 1: 'tok-1' }));
    expect(resolveSeatSession(PREFIX, 'g1')).toBeNull();
  });

  it('returns null and clears an entry that holds no seat', () => {
    localStorage.setItem('fake:tokens:g1', '{}');
    expect(resolveSeatSession(PREFIX, 'g1')).toBeNull();
  });

  it('returns null and clears a corrupted entry', () => {
    localStorage.setItem('fake:tokens:g1', '{not json');
    expect(resolveSeatSession(PREFIX, 'g1')).toBeNull();
    expect(localStorage.getItem('fake:tokens:g1')).toBeNull();
  });
});

describe('useSeatGameBootstrap', () => {
  beforeEach(() => { localStorage.clear(); });

  const game = findGame('bullshit')!;

  function fakeStore(hydrate: SeatGameBootstrapStore['hydrate']) {
    return { restore: vi.fn(), hydrate: vi.fn(hydrate) };
  }

  async function mountAtRoom(id: string, store: SeatGameBootstrapStore) {
    const router = createAppRouter(createMemoryHistory());
    await router.push(`/games/bullshit/room/${id}`);
    await router.isReady();
    const Probe = defineComponent({
      setup() { useSeatGameBootstrap(game, id, PREFIX, store); return () => h('div'); },
    });
    mount(Probe, { global: { plugins: [router] } });
    await flushPromises();
    return router;
  }

  it('without a stored seat leads to the join form of the game and restores nothing', async () => {
    const store = fakeStore(async () => 'ok');
    const router = await mountAtRoom('g1', store);
    expect(router.currentRoute.value.fullPath).toBe('/games/bullshit/join/g1');
    expect(store.restore).not.toHaveBeenCalled();
  });

  it('restores the stored seat then hydrates, and stays on the room when all is well', async () => {
    localStorage.setItem('fake:tokens:g1', JSON.stringify({ 2: 'tok' }));
    const store = fakeStore(async () => 'ok');
    const router = await mountAtRoom('g1', store);
    expect(store.restore).toHaveBeenCalledWith('g1', 2, 'tok');
    expect(router.currentRoute.value.path).toBe('/games/bullshit/room/g1');
  });

  it.each([['gone', 'gone'], ['lost', 'lost']] as const)(
    'on %s forgets the token under the prefix and leads to the game home with the %s notice', async (result, query) => {
      localStorage.setItem('fake:tokens:g1', JSON.stringify({ 0: 'tok' }));
      const router = await mountAtRoom('g1', fakeStore(async () => result));
      expect(router.currentRoute.value.path).toBe('/games/bullshit');
      expect(router.currentRoute.value.query[query]).toBe('1');
      expect(localStorage.getItem('fake:tokens:g1')).toBeNull();
    });

  it('on unavailable keeps the token and the room page', async () => {
    localStorage.setItem('fake:tokens:g1', JSON.stringify({ 0: 'tok' }));
    const router = await mountAtRoom('g1', fakeStore(async () => 'unavailable'));
    expect(router.currentRoute.value.path).toBe('/games/bullshit/room/g1');
    expect(localStorage.getItem('fake:tokens:g1')).not.toBeNull();
  });
});
