import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { defineComponent, h } from 'vue';
import { createMemoryHistory } from 'vue-router';
import { createAppRouter } from '../router';

vi.mock('../service/WebSocketService', () => ({
  default: { subscribeToGame: vi.fn(), unsubscribeFromGame: vi.fn(), setPresence: vi.fn(), clearPresence: vi.fn(), publish: vi.fn() },
}));

import { useGameBootstrap } from './useGameBootstrap';

async function mountAtRoom(id: string) {
  const router = createAppRouter(createMemoryHistory());
  await router.push(`/games/bataillecorse/room/${id}`);
  await router.isReady();
  const Probe = defineComponent({
    setup() { useGameBootstrap({ revealImmediatelyIfOver: () => {} }); return () => h('div'); },
  });
  mount(Probe, { global: { plugins: [router] } });
  await flushPromises();
  return router;
}

describe('useGameBootstrap restore failures', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });
  afterEach(() => { vi.unstubAllGlobals(); });

  it('givenNoStoredToken_thenGoesToTheJoinFormWithTheIdPrefilled', async () => {
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.fullPath).toBe('/games/bataillecorse/join/g1');
  });

  it('givenStoredTokenButGameGone_thenGoesToTheGameHomeWithTheNotice', async () => {
    localStorage.setItem('tokens:g1', JSON.stringify({ 0: 'tok' }));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }));
    const router = await mountAtRoom('g1');
    expect(router.currentRoute.value.path).toBe('/games/bataillecorse');
    expect(router.currentRoute.value.query.gone).toBe('1');
  });
});
