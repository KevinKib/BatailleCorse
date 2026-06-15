import { describe, it, expect, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import BullshitGameScreen from './BullshitGameScreen.vue';
import { useBullshitStore } from '../../state/Bullshit.store';
import type { BullshitState } from '../../model/bullshit/BullshitState';

function playingState(overrides: Partial<BullshitState> = {}): BullshitState {
  return {
    id: 'g1', gameType: 'bullshit',
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
    outcome: { status: 'ONGOING' },
    ...overrides,
  };
}

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/games/bullshit/create', component: { template: '<div/>' } },
    { path: '/games/bullshit/room/:id', component: { template: '<div/>' } },
  ],
});

describe('BullshitGameScreen', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); });

  it('disables Discard until a card is selected, then enables it on my turn', async () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState() });
    store.waiting = false;
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router] } });

    const discardBtn = wrapper.get('[data-test="discard"]');
    expect((discardBtn.element as HTMLButtonElement).disabled).toBe(true);

    await wrapper.get('[data-test="hand-card-0"]').trigger('click');
    expect((discardBtn.element as HTMLButtonElement).disabled).toBe(false);
  });

  it('shows the reveal panel after a CALL_BULLSHIT event', async () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState() });
    store.waiting = false;
    store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', message: '',
      eventData: { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0, revealedCards: [{ rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' }] } });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router] } });

    expect(wrapper.find('[data-test="reveal"]').exists()).toBe(true);
  });
});
