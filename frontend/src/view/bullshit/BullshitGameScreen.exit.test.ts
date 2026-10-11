import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, RouterView } from 'vue-router';
import { defineComponent, h } from 'vue';
import PrimeVue from 'primevue/config';
import { createAppRouter } from '../../router';
import { useBullshitStore } from '../../state/Bullshit.store';
import type { BullshitState } from '../../model/bullshit/BullshitState';

function state(finished: boolean): BullshitState {
  return {
    started: true, id: 'g1', gameType: 'bullshit',
    myHand: [{ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }],
    availableActions: ['DISCARD'],
    players: [
      { id: '0', handCount: 1, isCurrentPlayer: true },
      { id: '1', handCount: 3, isCurrentPlayer: false },
    ],
    currentTarget: { label: 'ACE' },
    discardPileSize: 0,
    table: { state: 'NO_CLAIM' },
    pendingWinner: { state: 'NONE' },
    outcome: finished ? { status: 'FINISHED', winnerId: '0' } : { status: 'ONGOING' },
  };
}

// Real app routes: the destination of every exit is asserted on the resolved route.
async function mountAt(finished: boolean) {
  const store = useBullshitStore();
  store.applyEvent({ type: 'seat-change', seat: 0 });
  store.applyEvent({ type: 'state-update', state: state(finished) });
  localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 0: 'tok' }));
  vi.spyOn(store, 'forfeit').mockImplementation(() => {});
  const router = createAppRouter(createMemoryHistory());
  await router.push('/games/bullshit/room/g1');
  await router.isReady();
  // Rendered through RouterView so the route-leave guard is registered like in the app.
  const Root = defineComponent({ render: () => h(RouterView) });
  const wrapper = mount(Root, { global: { plugins: [router, PrimeVue] } });
  await flushPromises();
  return { router, wrapper };
}

describe('BullshitGameScreen exit paths lead to /games', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))); setActivePinia(createPinia()); localStorage.clear(); });

  it('givenFinishedGame_whenBackToHome_thenGoesToGamePicker', async () => {
    const { router, wrapper } = await mountAt(true);
    await wrapper.get('.end-home-button').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/games');
  });

  it('givenMidGame_whenBackConfirmed_thenGoesToGamePicker', async () => {
    const { router, wrapper } = await mountAt(false);
    window.confirm = () => true;
    await wrapper.get('[data-test="leave"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/games');
  });

  it('givenMidGame_whenBackDeclined_thenStaysOnTable', async () => {
    const { router, wrapper } = await mountAt(false);
    window.confirm = () => false;
    await wrapper.get('[data-test="leave"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/games/bullshit/room/g1');
  });
});
