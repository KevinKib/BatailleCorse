import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import type { HydrateResult } from '../application/SeatGameSession';
import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h } from 'vue';
import { createMemoryHistory } from 'vue-router';
import { createAppRouter } from '../router';
import { findGame } from '../model/games';
import { resolveSeatSession, useSeatGameBootstrap, type SeatGameBootstrapStore, type SeatGameBootstrapOptions } from './useSeatGameBootstrap';

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

  async function mountAtRoom(id: string, store: SeatGameBootstrapStore, options?: SeatGameBootstrapOptions) {
    const router = createAppRouter(createMemoryHistory());
    await router.push(`/bullshit/room/${id}`);
    await router.isReady();
    const Probe = defineComponent({
      setup() { useSeatGameBootstrap(game, id, PREFIX, store, options); return () => h('div'); },
    });
    mount(Probe, { global: { plugins: [router] } });
    await flushPromises();
    return router;
  }

  it('without a stored seat leads to the join form of the game and restores nothing', async () => {
    const store = fakeStore(async () => 'ok');
    const router = await mountAtRoom('g1', store);
    expect(router.currentRoute.value.fullPath).toBe('/bullshit/join/g1');
    expect(store.restore).not.toHaveBeenCalled();
  });

  it('restores the stored seat then hydrates, and stays on the room when all is well', async () => {
    localStorage.setItem('fake:tokens:g1', JSON.stringify({ 2: 'tok' }));
    const store = fakeStore(async () => 'ok');
    const router = await mountAtRoom('g1', store);
    expect(store.restore).toHaveBeenCalledWith('g1', 2, 'tok');
    expect(router.currentRoute.value.path).toBe('/bullshit/room/g1');
  });

  it.each([['gone', 'gone'], ['lost', 'lost']] as const)(
    'on %s forgets the token under the prefix and leads to the game home with the %s notice', async (result, query) => {
      localStorage.setItem('fake:tokens:g1', JSON.stringify({ 0: 'tok' }));
      const router = await mountAtRoom('g1', fakeStore(async () => result));
      expect(router.currentRoute.value.path).toBe('/bullshit');
      expect(router.currentRoute.value.query[query]).toBe('1');
      expect(localStorage.getItem('fake:tokens:g1')).toBeNull();
    });

  describe('options', () => {
    it('singleSeat forgets an entry holding several seats and leads to the join form', async () => {
      localStorage.setItem('fake:tokens:g1', JSON.stringify({ 0: 'a', 1: 'b' }));
      const store = fakeStore(async () => 'ok');
      const router = await mountAtRoom('g1', store, { singleSeat: true });
      expect(router.currentRoute.value.fullPath).toBe('/bullshit/join/g1');
      expect(localStorage.getItem('fake:tokens:g1')).toBeNull();
      expect(store.hydrate).not.toHaveBeenCalled();
    });

    it('singleSeat forgets an entry whose token is not a string', async () => {
      localStorage.setItem('fake:tokens:g1', JSON.stringify({ 1: 5 }));
      const router = await mountAtRoom('g1', fakeStore(async () => 'ok'), { singleSeat: true });
      expect(router.currentRoute.value.fullPath).toBe('/bullshit/join/g1');
      expect(localStorage.getItem('fake:tokens:g1')).toBeNull();
    });

    it('without singleSeat the first seat of several is taken (unchanged for the other games)', async () => {
      localStorage.setItem('fake:tokens:g1', JSON.stringify({ 0: 'a', 1: 'b' }));
      const store = fakeStore(async () => 'ok');
      await mountAtRoom('g1', store);
      expect(store.restore).toHaveBeenCalledWith('g1', 0, 'a');
    });

    it('onHydrated runs once the room is loaded, not when the game is gone', async () => {
      localStorage.setItem('fake:tokens:g1', JSON.stringify({ 0: 'tok' }));
      const onHydrated = vi.fn();
      await mountAtRoom('g1', fakeStore(async () => 'ok'), { onHydrated });
      expect(onHydrated).toHaveBeenCalledTimes(1);
      const gone = vi.fn();
      await mountAtRoom('g1', fakeStore(async () => 'gone'), { onHydrated: gone });
      expect(gone).not.toHaveBeenCalled();
    });

    it('onUnmount runs when the screen goes away', async () => {
      const onUnmount = vi.fn();
      const router = createAppRouter(createMemoryHistory());
      await router.push('/bullshit/room/g1');
      await router.isReady();
      const Probe = defineComponent({
        setup() { useSeatGameBootstrap(game, 'g1', PREFIX, fakeStore(async () => 'ok'), { onUnmount }); return () => h('div'); },
      });
      const wrapper = mount(Probe, { global: { plugins: [router] } });
      await flushPromises();
      wrapper.unmount();
      expect(onUnmount).toHaveBeenCalledTimes(1);
    });
  });

  it('when the player left before the answer came, a gone game does not pull them back to the game home', async () => {
    localStorage.setItem('fake:tokens:g1', JSON.stringify({ 0: 'tok' }));
    let answer!: (result: HydrateResult) => void;
    const store = fakeStore(() => new Promise<HydrateResult>((resolve) => { answer = resolve; }));
    const onHydrated = vi.fn();
    const router = createAppRouter(createMemoryHistory());
    await router.push('/bullshit/room/g1');
    await router.isReady();
    const Probe = defineComponent({
      setup() { useSeatGameBootstrap(game, 'g1', PREFIX, store, { onHydrated }); return () => h('div'); },
    });
    const wrapper = mount(Probe, { global: { plugins: [router] } });
    await flushPromises();

    await router.push('/');
    wrapper.unmount();
    answer('gone');
    await flushPromises();

    expect(router.currentRoute.value.path).toBe('/');
    expect(onHydrated).not.toHaveBeenCalled();
  });

  describe('on unavailable', () => {
    beforeEach(() => { vi.useFakeTimers(); });
    afterEach(() => { vi.useRealTimers(); });

    it('tries again and lands on the room once the server answers', async () => {
      localStorage.setItem('fake:tokens:g1', JSON.stringify({ 0: 'tok' }));
      const answers: HydrateResult[] = ['unavailable', 'unavailable', 'ok'];
      const store = fakeStore(async () => answers.shift() ?? 'ok');
      const router = await mountAtRoom('g1', store);
      expect(store.hydrate).toHaveBeenCalledTimes(1);
      await vi.advanceTimersByTimeAsync(3000);
      expect(store.hydrate).toHaveBeenCalledTimes(3);
      expect(router.currentRoute.value.path).toBe('/bullshit/room/g1');
    });

    it('gives up after four attempts, keeping the token and the room page', async () => {
      localStorage.setItem('fake:tokens:g1', JSON.stringify({ 0: 'tok' }));
      const store = fakeStore(async () => 'unavailable');
      const router = await mountAtRoom('g1', store);
      await vi.advanceTimersByTimeAsync(60_000);
      expect(store.hydrate).toHaveBeenCalledTimes(4);
      expect(router.currentRoute.value.path).toBe('/bullshit/room/g1');
      expect(localStorage.getItem('fake:tokens:g1')).not.toBeNull();
    });

    it('still leaves for the game home when a retry learns the game is gone', async () => {
      localStorage.setItem('fake:tokens:g1', JSON.stringify({ 0: 'tok' }));
      const answers: HydrateResult[] = ['unavailable', 'gone'];
      const router = await mountAtRoom('g1', fakeStore(async () => answers.shift() ?? 'gone'));
      await vi.advanceTimersByTimeAsync(1000);
      expect(router.currentRoute.value.query.gone).toBe('1');
      expect(localStorage.getItem('fake:tokens:g1')).toBeNull();
    });
  });
});
