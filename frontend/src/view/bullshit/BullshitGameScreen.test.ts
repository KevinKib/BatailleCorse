import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import BullshitGameScreen from './BullshitGameScreen.vue';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import { useBullshitStore } from '../../state/Bullshit.store';
import type { BullshitState } from '../../model/bullshit/BullshitState';
import type { LobbyView } from '../../model/bullshit/LobbyView';

function playingState(overrides: Partial<BullshitState> = {}): BullshitState {
  return {
    started: true,
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

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false,
    gameId: 'g1',
    players: [
      { seat: 0, name: 'Alice', joined: true },
      { seat: 1, name: 'Bob', joined: true },
    ],
    hostSeat: 0,
    mySeat: 0,
    minPlayers: 2,
    maxPlayers: 6,
    canStart: false,
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
    store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', message: '',
      eventData: { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0, revealedCards: [{ rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' }] } });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router] } });

    expect(wrapper.find('[data-test="reveal"]').exists()).toBe(true);
  });

  it('renders the lobby panel with joined players', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: lobbyView() });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router] } });

    expect(wrapper.find('[data-test="lobby"]').exists()).toBe(true);
    const items = wrapper.findAll('[data-test="lobby"] .players li');
    expect(items).toHaveLength(2);
    expect(items[0].text()).toContain('Alice');
  });

  it('shows the player count and how many more players are needed when the host cannot start yet', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: lobbyView({
      players: [
        { seat: 0, name: 'Alice', joined: true },
        { seat: 1, name: null, joined: false },
      ],
      canStart: false,
    }) });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router] } });

    expect(wrapper.get('[data-test="player-count"]').text()).toContain('1 / 6');
    expect(wrapper.get('[data-test="start-hint"]').text()).toContain('1 more player');
  });

  it('shows a host Start button that is disabled until canStart and calls startGame on click', async () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: lobbyView({ canStart: false }) });
    const startGame = vi.spyOn(store, 'startGame').mockImplementation(() => {});
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router] } });

    const startBtn = wrapper.get('[data-test="start"]');
    expect((startBtn.element as HTMLButtonElement).disabled).toBe(true);

    store.applyEvent({ type: 'state-update', state: lobbyView({ canStart: true }) });
    await wrapper.vm.$nextTick();
    expect((startBtn.element as HTMLButtonElement).disabled).toBe(false);

    await startBtn.trigger('click');
    expect(startGame).toHaveBeenCalled();
  });

  it('renders the end-game overlay when finished and play-again calls rematch', async () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState({
      outcome: { status: 'FINISHED', winnerId: '0' },
    }) });

    const wrapper = mount(BullshitGameScreen, {
      props: { gameId: 'g1' },
      global: {
        plugins: [router],
        stubs: { RouterLink: true, Button: true },
      },
    });

    const overlay = wrapper.findComponent(EndGameOverlay);
    expect(overlay.exists()).toBe(true);

    const spy = vi.spyOn(store, 'rematch').mockImplementation(async () => {});
    await overlay.vm.$emit('playAgain');
    expect(spy).toHaveBeenCalled();
  });
});
