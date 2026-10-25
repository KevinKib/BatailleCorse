import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, RouterView } from 'vue-router';
import { defineComponent, h } from 'vue';
import PrimeVue from 'primevue/config';
import { createAppRouter } from '../../router';
import { useBullshitStore } from '../../state/Bullshit.store';
import type { BullshitState } from '../../model/bullshit/BullshitState';
import type { LobbyView } from '../../model/lobby/LobbyView';
import webSocketService from '../../service/WebSocketService';

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

function lobby(): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: 'Bob', joined: true }],
    hostSeat: 0, mySeat: 1, minPlayers: 2, maxPlayers: 6, canStart: false, lobbyVersion: 1,
  };
}

// Real app routes: the destination of every exit is asserted on the resolved route.
async function mountAt(finished: boolean, inLobby = false) {
  const store = useBullshitStore();
  store.applyEvent({ type: 'seat-change', seat: inLobby ? 1 : 0 });
  store.applyEvent({ type: 'state-update', state: inLobby ? lobby() : state(finished) });
  localStorage.setItem('bullshit:tokens:g1', JSON.stringify({ 0: 'tok' }));
  vi.spyOn(store, 'forfeit').mockImplementation(() => {});
  const router = createAppRouter(createMemoryHistory());
  await router.push('/bullshit/room/g1');
  await router.isReady();
  // Rendered through RouterView so the route-leave guard is registered like in the app.
  const Root = defineComponent({ render: () => h(RouterView) });
  const wrapper = mount(Root, { global: { plugins: [router, PrimeVue] } });
  await flushPromises();
  return { router, wrapper };
}

describe('BullshitGameScreen exit paths lead to the game picker (/)', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))); setActivePinia(createPinia()); localStorage.clear(); });

  it('givenFinishedGame_whenBackToHome_thenGoesToGamePicker', async () => {
    const { router, wrapper } = await mountAt(true);
    await wrapper.get('.end-home-button').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
  });

  it('givenMidGame_whenBackConfirmed_thenGoesToGamePicker', async () => {
    const { router, wrapper } = await mountAt(false);
    window.confirm = () => true;
    await wrapper.get('[data-test="leave"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
  });

  it('givenMidGame_whenBackDeclined_thenStaysOnTable', async () => {
    const { router, wrapper } = await mountAt(false);
    window.confirm = () => false;
    await wrapper.get('[data-test="leave"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/bullshit/room/g1');
  });

  it('givenLobby_whenLeave_thenTheSeatIsGivenUpAndItGoesToGamePicker', async () => {
    const { router, wrapper } = await mountAt(false, true);
    const store = useBullshitStore();
    const leave = vi.spyOn(store, 'leave').mockReturnValue(true);
    await wrapper.get('[data-test="leave-lobby"]').trigger('click');
    await flushPromises();
    expect(leave).toHaveBeenCalledTimes(1);
    expect(router.currentRoute.value.fullPath).toBe('/');
  });

  it('givenLobbyAndTheLeaveCouldNotBeSent_whenLeave_thenStaysInTheLobbyWithTheError', async () => {
    const { router, wrapper } = await mountAt(false, true);
    const store = useBullshitStore();
    store.restore('g1', 1, 'tok');
    vi.spyOn(webSocketService, 'publish').mockImplementation(() => { throw new TypeError('no underlying STOMP connection'); });
    await wrapper.get('[data-test="leave-lobby"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/bullshit/room/g1');
    expect(wrapper.get('[data-test="lobby-error"]').exists()).toBe(true);
  });

  it('givenFinishedGame_whenPlayAgain_thenLandsInTheLobbyOfTheSameRoom', async () => {
    const { router, wrapper } = await mountAt(true);
    const ok = (body: unknown) => Promise.resolve({ ok: true, status: 200, json: async () => body });
    vi.stubGlobal('fetch', vi.fn()
      .mockImplementationOnce(() => ok({ playerId: 0, token: 'tok2' }))
      .mockImplementation(() => ok({ ...lobby(), mySeat: 0, lobbyVersion: 1 })));
    vi.spyOn(webSocketService, 'subscribeToSeat').mockImplementation(() => {});
    vi.spyOn(webSocketService, 'setPresence').mockImplementation(() => {});
    await wrapper.get('[data-cy="play-again"]').trigger('click');
    await flushPromises();
    expect(wrapper.find('[data-test="lobby"]').exists()).toBe(true);
    expect(router.currentRoute.value.fullPath).toBe('/bullshit/room/g1');
  });
});
