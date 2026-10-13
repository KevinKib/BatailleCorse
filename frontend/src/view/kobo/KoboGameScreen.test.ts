import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, RouterView } from 'vue-router';
import { defineComponent, h } from 'vue';
import PrimeVue from 'primevue/config';
import { createAppRouter } from '../../router';
import { useKoboStore } from '../../state/Kobo.store';
import type { LobbyView } from '../../model/lobby/LobbyView';
import { buildKoboCard, buildKoboPlayer, buildKoboSlot, buildKoboState } from '../../model/fixtures/kobo';
import { setConnectionOnline } from '../../composables/useConnectionStatus';
import { messagesEn } from '../../locales/en';

function lobby(joined: number, mySeat = 0): LobbyView {
  return {
    started: false, gameId: 'k1',
    players: Array.from({ length: joined }, (_, seat) => ({ seat, name: `P${seat}`, joined: true })),
    hostSeat: 0, mySeat, minPlayers: 2, maxPlayers: 6, canStart: joined >= 2, lobbyVersion: 1,
  };
}

async function mountRoom(setup: (store: ReturnType<typeof useKoboStore>) => void) {
  const store = useKoboStore();
  localStorage.setItem('kobo:tokens:k1', JSON.stringify({ 0: 'tok' }));
  setup(store);
  vi.spyOn(store, 'hydrate').mockImplementation(() => {});
  vi.spyOn(store, 'restore').mockImplementation(() => {});
  const router = createAppRouter(createMemoryHistory());
  await router.push('/games/kobo/room/k1');
  await router.isReady();
  const Root = defineComponent({ render: () => h(RouterView) });
  const wrapper = mount(Root, { global: { plugins: [router, PrimeVue] } });
  await flushPromises();
  return { store, router, wrapper };
}

describe('KoboGameScreen', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))); setActivePinia(createPinia()); localStorage.clear(); });

  it('givenNoStateYet_thenShowsTheConnectingScreen', async () => {
    const { wrapper } = await mountRoom(() => {});
    expect(wrapper.get('[data-test="connecting"]').text()).toContain(messagesEn.koboUi.connecting);
    expect(wrapper.find('[data-test="lobby"]').exists()).toBe(false);
  });

  it('givenALobby_thenShowsTheSharedLobbyForKoboWithoutAddBot', async () => {
    const { wrapper } = await mountRoom(store => {
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: lobby(2) });
    });
    expect(wrapper.get('[data-test="lobby"]').text()).toContain('Kobo');
    expect(wrapper.get('[data-test="player-count"]').text()).toContain('2 / 6');
    expect(wrapper.find('[data-test="add-bot"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="start"]').attributes('disabled')).toBeUndefined();
  });

  it('givenTheHostPressesStart_thenTheStoreStartsTheGame', async () => {
    const { wrapper, store } = await mountRoom(s => {
      s.applyEvent({ type: 'seat-change', seat: 0 });
      s.applyEvent({ type: 'state-update', state: lobby(3) });
    });
    const start = vi.spyOn(store, 'startGame').mockImplementation(() => {});
    await wrapper.get('[data-test="start"]').trigger('click');
    expect(start).toHaveBeenCalled();
  });

  it('givenALobbyLeave_thenGoesToTheGameSelection', async () => {
    const { wrapper, store, router } = await mountRoom(s => {
      s.applyEvent({ type: 'seat-change', seat: 0 });
      s.applyEvent({ type: 'state-update', state: lobby(2) });
    });
    vi.spyOn(store, 'leave').mockReturnValue(true);
    await wrapper.get('[data-test="leave-lobby"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.name).toBe('games');
  });

  describe('table', () => {
    const seatsOf = (wrapper: ReturnType<typeof mount>) =>
      wrapper.findAll('[data-test="opponent-seat"]').map(w => Number(w.attributes('data-seat')));

    function started(overrides = {}, count = 2, mySeat = 0) {
      return mountRoom(s => {
        s.applyEvent({ type: 'seat-change', seat: mySeat });
        s.applyEvent({
          type: 'state-update',
          state: buildKoboState({
            players: Array.from({ length: count }, (_, seat) => buildKoboPlayer(seat, { current: seat === 0 })),
            ...overrides,
          }),
        });
      });
    }

    it('givenAStartedGame_thenShowsTheTableAndNeitherTheLobbyNorAPlaceholder', async () => {
      const { wrapper } = await started();
      expect(wrapper.find('[data-test="kobo-table"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="kobo-provisional"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="lobby"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="connecting"]').exists()).toBe(false);
    });

    it.each([2, 3, 4, 5, 6])('given%iPlayers_thenEverySeatIsOnTheTableInPlayOrderAfterMe', async (count) => {
      const { wrapper } = await started({}, count, 1);
      const others = [...Array(count).keys()].filter(seat => seat !== 1);
      const rotated = [...others.filter(s => s > 1), ...others.filter(s => s < 1)];
      expect(seatsOf(wrapper)).toEqual(rotated);
      expect(wrapper.findAll('[data-test="opponent-seat"] button')).toHaveLength((count - 1) * 4);
      expect(wrapper.findAll('[data-test="my-zone"] button')).toHaveLength(4);
    });

    it('givenNamesAndNoName_thenTheLabelsFollowTheSharedRule', async () => {
      const { wrapper } = await started({
        players: [buildKoboPlayer(0, { name: 'Alice' }), buildKoboPlayer(1, { name: '  ' }), buildKoboPlayer(2, { name: 'Cleo' })],
      });
      const labels = wrapper.findAll('[data-test="opponent-seat"] [data-test="seat-label"]').map(w => w.text());
      expect(labels).toEqual(['Player 2', 'Cleo']);
      expect(wrapper.get('[data-test="my-zone"] [data-test="seat-label"]').text()).toBe('You');
    });

    it('givenMemorising_thenOnlyMyStartingCardsAreFaceUpAndTheOthersFaceDown', async () => {
      const { wrapper } = await started({
        phase: 'MEMORISING', readySeats: [1],
        startingCards: [{ slot: 2, card: buildKoboCard('KING', 'HEART') }, { slot: 3, card: buildKoboCard('4', 'CLUB') }],
      });
      const mine = wrapper.findAll('[data-test="my-zone"] button').map(b => b.attributes('aria-label'));
      expect(mine).toEqual([
        'Your card 1, face down', 'Your card 2, face down', 'Your card 3: king of heart', 'Your card 4: 4 of club',
      ]);
      expect(wrapper.findAll('[data-test="opponent-seat"] button')
        .every(b => b.attributes('aria-label')!.endsWith('face down'))).toBe(true);
      expect(wrapper.get('[data-test="phase"]').text()).toBe('Memorise your two cards');
    });

    it('givenIAmReady_thenMyStartingCardsAreFaceDownAndMyReadyMarkShows', async () => {
      const { wrapper } = await started({
        phase: 'MEMORISING', readySeats: [0],
        startingCards: [{ slot: 2, card: buildKoboCard('KING', 'HEART') }],
      });
      expect(wrapper.findAll('[data-test="my-zone"] button').every(b => b.attributes('aria-label')!.endsWith('face down'))).toBe(true);
      expect(wrapper.find('[data-test="my-zone"] [data-test="seat-ready"]').exists()).toBe(true);
    });

    it('givenARevealedRound_thenEverySlotIsFaceUpFromTheState', async () => {
      const card = buildKoboCard('9', 'DIAMOND');
      const slots = [0, 1, 2, 3].map(i => buildKoboSlot(i, { card }));
      const { wrapper } = await started({
        phase: 'ROUND_OVER', revealed: true,
        players: [buildKoboPlayer(0, { slots }), buildKoboPlayer(1, { slots })],
      });
      const slotButtons = wrapper.findAll('button[data-test^="slot-"]');
      expect(slotButtons).toHaveLength(8);
      expect(slotButtons.every(b => b.attributes('aria-label')!.includes('9 of diamond'))).toBe(true);
    });

    it('showsTheDiscardTopTheCountsTheRoundAndThePhase', async () => {
      const { wrapper } = await started({ round: 3, drawPileSize: 22, discardSize: 5, discardTop: buildKoboCard('QUEEN', 'SPADE') });
      expect(wrapper.get('[data-test="round"]').text()).toBe('Round 3');
      expect(wrapper.get('[data-test="draw-count"]').text()).toBe('22');
      expect(wrapper.get('[data-test="discard-count"]').text()).toBe('5');
      expect(wrapper.get('[data-test="discard-pile"]').attributes('aria-label')).toContain('queen of spade');
      expect(wrapper.get('[data-test="phase"]').text()).toBe('Your turn: draw a card');
    });

    it('givenItIsNotMyTurn_thenThePhaseNamesThePlayerAndNoYourTurnCueShows', async () => {
      const { wrapper } = await started({
        currentSeat: 1,
        players: [buildKoboPlayer(0), buildKoboPlayer(1, { name: 'Bob', current: true })],
      });
      expect(wrapper.get('[data-test="phase"]').text()).toBe('Bob is drawing');
      expect(wrapper.find('[data-test="turn-hint"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="turn-indicator"]').exists()).toBe(true);
    });

    it('givenItIsMyTurn_thenTheYourTurnCueShows', async () => {
      const { wrapper } = await started();
      expect(wrapper.get('[data-test="turn-hint"]').text()).toContain('Your turn');
    });

    it('givenAnActionTheTableOnlyShows_thenNoSlotIsEnabled', async () => {
      const { wrapper } = await started({
        phase: 'DECISION', hasDrawn: true, drawnCard: buildKoboCard('3', 'CLUB'), availableActions: ['SWAP_DRAWN', 'DISCARD_DRAWN'],
      });
      expect(wrapper.findAll('button[data-test^="slot-"]').every(b => b.attributes('disabled') !== undefined)).toBe(true);
      expect(wrapper.get('[data-test="drawn-zone"]').attributes('aria-label')).toBe('Your drawn card: 3 of club');
    });

    it('givenTheMarkersOfTheState_thenTheSeatsShowThem', async () => {
      const { wrapper } = await started({ announcer: 1, finalTurnsRemaining: [0] });
      expect(wrapper.find('[data-test="opponent-seat"] [data-test="seat-announcer"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="my-zone"] [data-test="seat-final-turn"]').exists()).toBe(true);
    });

    it('givenADisconnectedOpponent_thenItsSeatShowsTheBadge', async () => {
      const { wrapper, store } = await started();
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED', eventData: { disconnectedSeat: 1, deadlineEpochMs: Date.now() + 30000 } });
      await flushPromises();
      expect(wrapper.find('[data-test="opponent-seat"] [data-test="seat-disconnect-badge"]').exists()).toBe(true);
    });

    it('givenTheConnectionIsDown_thenTheOfflineNoticeShows', async () => {
      const { wrapper } = await started();
      expect(wrapper.find('[data-test="offline"]').exists()).toBe(false);
      setConnectionOnline(false);
      await flushPromises();
      expect(wrapper.get('[data-test="offline"]').text()).toContain('Connection lost');
      setConnectionOnline(true);
    });

    it('hasTheRulesTheTimerAndBack', async () => {
      const { wrapper, router, store } = await started();
      expect(wrapper.find('[data-cy="rules-toggle"]').exists()).toBe(true);
      expect(wrapper.find('[data-cy="game-timer"]').exists()).toBe(true);
      window.confirm = () => true;
      vi.spyOn(store, 'forfeit').mockImplementation(() => {});
      await wrapper.get('[data-test="back"]').trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.name).toBe('games');
    });

    it('keepsAZoneForTheActionsOfTheNextStep', async () => {
      const { wrapper } = await started();
      expect(wrapper.find('[data-test="kobo-actions"]').exists()).toBe(true);
    });
  });

  it('givenTheHostRemovedMe_thenLeadsToTheGameSelectionWithTheNotice', async () => {
    const { store, router } = await mountRoom(s => {
      s.applyEvent({ type: 'seat-change', seat: 1 });
      s.applyEvent({ type: 'state-update', state: lobby(2, 1) });
    });
    store.removedByHost = true;
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/games?removed=1');
  });
});
