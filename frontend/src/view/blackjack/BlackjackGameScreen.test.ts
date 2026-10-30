import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import PrimeVue from 'primevue/config';

import BlackjackGameScreen from './BlackjackGameScreen.vue';
import { useBlackjackStore } from '../../state/Blackjack.store';
import { messagesEn } from '../../locales/en';
import {
  blackjackBettingState, blackjackPlayingState, blackjackSoloEndState, blackjackTournamentEndState,
} from '../../model/blackjack/fixtures';
import type { BlackjackState } from '../../model/blackjack/BlackjackState';
import type { LobbyView } from '../../model/lobby/LobbyView';

const DESCRIPTORS = [
  { key: 'hands', type: 'CHOICE' as const, values: ['10', '5', '15', '20'], defaultValue: '10', labelKey: 'options.blackjack.hands' },
  { key: 'maxBet', type: 'CHOICE' as const, values: ['50', '25', '100'], defaultValue: '50', labelKey: 'options.blackjack.maxBet' },
];

function lobby(joined: number): LobbyView {
  return {
    started: false, gameId: 'b1',
    players: Array.from({ length: joined }, (_, seat) => ({ seat, name: `P${seat}`, joined: true })),
    seatOptionDescriptors: [], removableBotSeats: [],
    hostSeat: 0, mySeat: 0, minPlayers: 1, maxPlayers: 6, canStart: true, lobbyVersion: 1,
    optionDescriptors: DESCRIPTORS, options: { hands: '10', maxBet: '50' }, canEditOptions: true,
  };
}

async function mountRoom(view: BlackjackState | LobbyView | null) {
  const store = useBlackjackStore();
  localStorage.setItem('blackjack:tokens:b1', JSON.stringify({ 0: 'tok' }));
  if (view) {
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: view as never });
  }
  vi.spyOn(store, 'hydrate').mockImplementation(async () => 'ok');
  vi.spyOn(store, 'restore').mockImplementation(() => {});
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', name: 'games', component: { render: () => null } }, { path: '/:rest(.*)*', component: { render: () => null } }],
  });
  await router.push('/blackjack/room/b1');
  await router.isReady();
  const wrapper = mount(BlackjackGameScreen, { props: { gameId: 'b1' }, global: { plugins: [router, PrimeVue] } });
  await flushPromises();
  return { store, wrapper };
}

describe('BlackjackGameScreen', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))); setActivePinia(createPinia()); localStorage.clear(); });

  it('shows the connecting screen before the first state', async () => {
    const { wrapper } = await mountRoom(null);
    expect(wrapper.get('[data-test="connecting"]').text()).toContain(messagesEn.blackjackUi.connecting);
  });

  it('lobby: options, fixed line and the solo hint when alone', async () => {
    const { wrapper } = await mountRoom(lobby(1));
    expect(wrapper.get('[data-test="option-hands"]').exists()).toBe(true);
    expect(wrapper.get('[data-test="fixed-line"]').exists()).toBe(true);
    expect(wrapper.get('[data-test="solo-hint"]').exists()).toBe(true);
  });

  it('lobby: no solo hint with two players', async () => {
    const { wrapper } = await mountRoom(lobby(2));
    expect(wrapper.find('[data-test="solo-hint"]').exists()).toBe(false);
  });

  it('betting: header, summary, plates, my seat, and one tap sends the last bet', async () => {
    const { wrapper, store } = await mountRoom(blackjackBettingState);
    expect(wrapper.get('[data-test="hand-number"]').text()).toBe('Hand 3 / 10');
    expect(wrapper.find('[data-test="hand-summary"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-test="seat-plate"]')).toHaveLength(3);
    expect(wrapper.find('[data-test="my-seat"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="action-bar"]').exists()).toBe(false);
    const bet = vi.spyOn(store, 'bet').mockReturnValue(true);
    expect(wrapper.get('[data-test="bet-place"]').text()).toBe('Bet 10');
    await wrapper.get('[data-test="bet-place"]').trigger('click');
    expect(bet).toHaveBeenCalledWith(10);
  });

  it('playing, my turn: only the available actions are buttons and they go through the store', async () => {
    const { wrapper, store } = await mountRoom(blackjackPlayingState);
    expect(wrapper.find('[data-test="bet-panel"]').exists()).toBe(false);
    expect(wrapper.findAll('[data-test="action-bar"] button')).toHaveLength(3);
    expect(wrapper.find('[data-test="action-split"]').exists()).toBe(false);
    const hit = vi.spyOn(store, 'hit').mockReturnValue(true);
    await wrapper.get('[data-test="action-hit"]').trigger('click');
    expect(hit).toHaveBeenCalled();
    expect(wrapper.get('[data-test="hole-card"]').exists()).toBe(true);
  });

  it('playing, not my turn: a waiting line names the player', async () => {
    const waiting = { ...blackjackPlayingState, currentSeat: 1, availableActions: [] };
    const { wrapper } = await mountRoom(waiting);
    expect(wrapper.find('[data-test="action-bar"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="status"]').text()).toBe('Waiting for Bob…');
    // The bar keeps its place, dimmed, so nothing jumps when my turn comes; Bob's plate shows the turn.
    expect(wrapper.findAll('[data-test="action-bar-idle"] button')).toHaveLength(4);
    expect(wrapper.get('[data-test="seat-plate"][data-seat="1"]').attributes('data-current')).toBe('true');
  });

  it('puts the permanent dock inside my seat, with the actions, and no glow class on the bar', async () => {
    const { wrapper } = await mountRoom(blackjackPlayingState);
    expect(wrapper.find('[data-test="my-seat"] [data-test="dock"] [data-test="action-bar"]').exists()).toBe(true);
    expect(wrapper.get('[data-test="action-bar"]').classes()).not.toContain('turn-bar');
    expect(wrapper.find('[data-test="bottom-bar"] [data-test="controls"]').exists()).toBe(false);
  });

  it('keeps the dock in the same place, disabled, when it is not my turn', async () => {
    const { wrapper } = await mountRoom({ ...blackjackPlayingState, currentSeat: 1, availableActions: [] });
    const idle = wrapper.findAll('[data-test="my-seat"] [data-test="dock"] [data-test="action-bar-idle"] button');
    expect(idle).toHaveLength(4);
    expect(idle.every(b => b.attributes('disabled') !== undefined)).toBe(true);
  });

  it('leaves no empty band for a player who is out: no dock', async () => {
    const out = { ...blackjackPlayingState, currentSeat: 1, availableActions: [],
      players: blackjackPlayingState.players.map(p => p.seat === 0 ? { ...p, eliminated: true } : p) };
    const { wrapper } = await mountRoom(out);
    expect(wrapper.find('[data-test="controls"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="dock"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="status"]').exists()).toBe(true);
  });

  it('keeps the cards of the last hand (dealer and mine) on the table through the next betting', async () => {
    const { wrapper } = await mountRoom(blackjackBettingState);
    expect(wrapper.findAll('[data-test="dealer-cards"] .playing_card')).toHaveLength(2);
    expect(wrapper.findAll('[data-test="my-seat"] [data-test="hand-cards"] .playing_card')).toHaveLength(2);
  });

  it('shows the shoe, the hole card face down and the felt from the deal', async () => {
    const { wrapper } = await mountRoom(blackjackPlayingState);
    expect(wrapper.get('[data-anchor="shoe"]').exists()).toBe(true);
    expect(wrapper.get('[data-test="hole-card"]').exists()).toBe(true);
    expect(wrapper.find('.bj-felt').exists()).toBe(true);
  });

  it('shows a bot action as a tag over its plate, from the event', async () => {
    const { wrapper, store } = await mountRoom(blackjackPlayingState);
    expect(wrapper.find('[data-test="seat-tag"]').exists()).toBe(false);
    store.applyEvent({ type: 'event', eventType: 'STAND', eventData: {
      seat: 2, handIndex: 0, card: null, value: null, cards: null, holeCard: null, handNumber: null,
      button: null, bets: null, nets: null, stacks: null, bestSeats: null,
    } });
    await flushPromises();
    // No pictures in this environment: the last action of the seat is still told, on its plate.
    expect(wrapper.get('[data-test="seat-plate"][data-seat="2"] [data-test="seat-last"]').text()).toBe('Stands');
  });

  it('live region: my own bust is said with "You bust", and with its total', async () => {
    const { wrapper, store } = await mountRoom(blackjackPlayingState);
    store.applyEvent({ type: 'event', eventType: 'BUST', eventData: {
      seat: 0, handIndex: 0, card: null, value: 24, cards: null, holeCard: null, handNumber: null,
      button: null, bets: null, nets: null, stacks: null, bestSeats: null,
    } });
    await flushPromises();
    expect(wrapper.get('[data-test="blackjack-live"]').text()).toBe('You bust with 24');
  });

  it('tournament end: shared places', async () => {
    const { wrapper } = await mountRoom(blackjackTournamentEndState);
    expect(wrapper.get('[data-test="end"]').exists()).toBe(true);
    expect(wrapper.findAll('[data-test="place"]').map(p => p.text())).toEqual(['1st', '1st', '3rd', '4th']);
    expect(wrapper.get('[data-test="end-winner"]').text()).toContain('Bob');
  });

  it('solo end: the final stack and the net', async () => {
    const { wrapper } = await mountRoom(blackjackSoloEndState);
    expect(wrapper.get('[data-test="solo-final"]').text()).toBe('You finish with 123 points (+23)');
  });
});
