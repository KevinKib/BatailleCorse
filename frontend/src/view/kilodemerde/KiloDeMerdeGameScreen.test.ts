import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import PrimeVue from 'primevue/config';
import KiloDeMerdeGameScreen from './KiloDeMerdeGameScreen.vue';
import { useKiloDeMerdeStore } from '../../state/KiloDeMerde.store';
import { messagesEn } from '../../locales/en';
import {
  DEFAULT_KILO_DE_MERDE_OPTIONS, kiloDeMerdeFinishedState, kiloDeMerdeRaceState, kiloDeMerdeRoundOverState,
  kiloDeMerdeStepState,
} from '../../model/kilodemerde/fixtures';
import type { GameDefinition } from '../../model/games';
import type { LobbyView } from '../../model/lobby/LobbyView';
import type { KiloDeMerdeState } from '../../model/kilodemerde/KiloDeMerdeState';

// The picker texts of the game arrive with the entry change; the lobby only needs its name here.
vi.mock('../../locales/gameChoice', () => ({
  gameChoice: () => ({ name: 'Kilo de merde', tagline: '', players: '' }),
}));

// The game is registered by the entry change, so the screen is handed its definition here.
const GAME = {
  key: 'kilo-de-merde', serverType: 'kilo-de-merde', gameHomeRoute: '/kilo-de-merde',
  joinRoute: (id?: string) => (id ? `/kilo-de-merde/join/${id}` : '/kilo-de-merde/join'),
  roomRoute: (id: string) => `/kilo-de-merde/room/${id}`,
  supportsBots: true, picker: { icon: 'pi pi-stopwatch', severity: 'warn' },
} as unknown as GameDefinition;

const DESCRIPTORS = [
  { key: 'rounds', type: 'CHOICE' as const, values: ['10', '5', '15', '20'], defaultValue: '10', labelKey: 'options.kiloDeMerde.rounds' },
];

function lobby(joined: number, mySeat = 0): LobbyView {
  return {
    started: false, gameId: 'k1',
    players: Array.from({ length: joined }, (_, seat) => ({ seat, name: `P${seat}`, joined: true })),
    seatOptionDescriptors: [], removableBotSeats: [],
    hostSeat: 0, mySeat, minPlayers: 3, maxPlayers: 10, canStart: joined >= 3, lobbyVersion: 1,
    optionDescriptors: DESCRIPTORS, options: { ...DEFAULT_KILO_DE_MERDE_OPTIONS }, canEditOptions: true,
  };
}

async function mountRoom(setup: (store: ReturnType<typeof useKiloDeMerdeStore>) => void) {
  const store = useKiloDeMerdeStore();
  localStorage.setItem('kilo-de-merde:tokens:k1', JSON.stringify({ 0: 'tok' }));
  setup(store);
  vi.spyOn(store, 'hydrate').mockImplementation(async () => 'ok');
  vi.spyOn(store, 'restore').mockImplementation(() => {});
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', name: 'games', component: { render: () => null } }, { path: '/:rest(.*)*', component: { render: () => null } }],
  });
  await router.push('/kilo-de-merde/room/k1');
  await router.isReady();
  const wrapper = mount(KiloDeMerdeGameScreen, {
    props: { gameId: 'k1', game: GAME }, global: { plugins: [router, PrimeVue] }, attachTo: document.body,
  });
  await flushPromises();
  return { store, router, wrapper };
}

const room = (state: KiloDeMerdeState | LobbyView, seat = 0) => (s: ReturnType<typeof useKiloDeMerdeStore>) => {
  s.applyEvent({ type: 'seat-change', seat });
  s.applyEvent({ type: 'state-update', state: state as never });
};

const names = (wrapper: Awaited<ReturnType<typeof mountRoom>>['wrapper']) => wrapper.findAll('[data-test="seat-name"]').map(n => n.text());
const press = (key: string) => document.dispatchEvent(new KeyboardEvent('keyup', { key }));

describe('KiloDeMerdeGameScreen', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))); setActivePinia(createPinia()); localStorage.clear(); });
  afterEach(() => { document.body.innerHTML = ''; vi.unstubAllGlobals(); });

  it('givenNoStateYet_thenShowsTheConnectingScreen', async () => {
    const { wrapper } = await mountRoom(() => {});
    expect(wrapper.get('[data-test="connecting"]').text()).toContain(messagesEn.kiloDeMerdeUi.connecting);
  });

  it('givenAStep_thenTheRulesPanelOpensWithTheRulesOfKiloDeMerde', async () => {
    const { wrapper } = await mountRoom(room(kiloDeMerdeStepState));
    await wrapper.get('[data-cy="rules-toggle"]').trigger('click');
    expect(wrapper.get('[data-cy="rules-panel"]').text()).toContain(messagesEn.kiloDeMerde.sections[0].title);
  });

  describe('lobby', () => {
    it('givenALobby_thenTheSharedLobbyShowsTheRoundsOptionAndTheSeats', async () => {
      const { wrapper } = await mountRoom(room(lobby(4)));
      expect(wrapper.get('[data-test="player-count"]').text()).toContain('4 / 10');
      expect(wrapper.get('[data-test="option-rounds"]').text()).toContain('Rounds');
    });

    it('givenTheHostPicksRounds_thenTheOptionGoesThroughTheStore', async () => {
      const { wrapper, store } = await mountRoom(room(lobby(4)));
      const setOption = vi.spyOn(store, 'setOption').mockImplementation(() => {});
      await wrapper.get('[data-test="option-rounds-15"]').setValue(true);
      expect(setOption).toHaveBeenCalledWith('rounds', '15');
    });
  });

  describe('a pass step', () => {
    it('givenAStep_thenEverythingTheTableKnowsIsOnScreen', async () => {
      const { wrapper } = await mountRoom(room(kiloDeMerdeStepState));
      expect(names(wrapper)).toEqual(['You', 'Player 1', 'Player 2']);
      expect(wrapper.get('[data-test="round-heading"]').text()).toBe('Round 2 of 10 · pass 3');
      expect(wrapper.get('[data-test="pile-count"]').text()).toBe('38');
      expect(wrapper.get('[data-test="seat-plate"][data-seat="2"] [data-test="seat-kilos"]').text()).toBe('13 kg');
      expect(wrapper.findAll('[data-test="seat-ready"]')).toHaveLength(1);
      expect(wrapper.get('[data-test="countdown"]').exists()).toBe(true);
      expect(wrapper.find('[data-cy="game-timer"]').exists()).toBe(true);
      expect(wrapper.findAll('[data-test="hand"] button')).toHaveLength(4);
    });

    it('givenATapOnACard_thenTheCardIsPassedThroughTheStore', async () => {
      const { wrapper, store } = await mountRoom(room(kiloDeMerdeStepState));
      const pass = vi.spyOn(store, 'pass').mockReturnValue(true);
      await wrapper.get('[data-card="DIAMOND_QUEEN"]').trigger('click');
      expect(pass).toHaveBeenCalledWith(kiloDeMerdeStepState.hand[2]);
    });

    it('givenIAlreadyChose_thenMyCardsAreDisabledAndSayPassing', async () => {
      const state: KiloDeMerdeState = { ...kiloDeMerdeStepState, myChoice: kiloDeMerdeStepState.hand[0], availableActions: ['SLAP'] };
      const { wrapper } = await mountRoom(room(state));
      expect(wrapper.get('[data-test="passing"]').text()).toBe('Passing…');
      expect(wrapper.findAll('[data-test="hand"] button').every(b => b.attributes('disabled') !== undefined)).toBe(true);
    });

    it('givenThePileIsTheSlapTarget_thenItSlapsThroughTheStore', async () => {
      const { wrapper, store } = await mountRoom(room(kiloDeMerdeStepState));
      const slap = vi.spyOn(store, 'slap').mockReturnValue(true);
      await wrapper.get('[data-test="pile"]').trigger('click');
      expect(slap).toHaveBeenCalled();
    });

    it('givenNoSlapAvailable_thenThePileIsDisabledButKeepsItsName', async () => {
      const state: KiloDeMerdeState = { ...kiloDeMerdeStepState, availableActions: ['PASS'] };
      const { wrapper } = await mountRoom(room(state));
      expect(wrapper.get('[data-test="pile"]').attributes('disabled')).toBeDefined();
      expect(wrapper.get('[data-test="pile"]').attributes('aria-label')).toContain('Slap the pile');
    });
  });

  describe('hotkeys', () => {
    it('givenSpace_thenThePileIsSlapped', async () => {
      const { store } = await mountRoom(room(kiloDeMerdeStepState));
      const slap = vi.spyOn(store, 'slap').mockReturnValue(true);
      press(' ');
      expect(slap).toHaveBeenCalledTimes(1);
    });

    it('givenDigitsOneToFour_thenTheCardInThatSlotIsPassed', async () => {
      const { store } = await mountRoom(room(kiloDeMerdeStepState));
      const pass = vi.spyOn(store, 'pass').mockReturnValue(true);
      press('1');
      press('4');
      expect(pass).toHaveBeenNthCalledWith(1, kiloDeMerdeStepState.hand[0]);
      expect(pass).toHaveBeenNthCalledWith(2, kiloDeMerdeStepState.hand[3]);
    });

    it('givenSlotsBeyondMyHandOrActionsOff_thenTheKeysDoNothing', async () => {
      const state: KiloDeMerdeState = { ...kiloDeMerdeStepState, hand: kiloDeMerdeStepState.hand.slice(0, 2), availableActions: [] };
      const { store } = await mountRoom(room(state));
      const pass = vi.spyOn(store, 'pass').mockReturnValue(true);
      const slap = vi.spyOn(store, 'slap').mockReturnValue(true);
      press('4');
      press('1');
      press(' ');
      expect(pass).not.toHaveBeenCalled();
      expect(slap).not.toHaveBeenCalled();
    });

    it('givenTheLobby_thenTheKeysDoNothing', async () => {
      const { store } = await mountRoom(room(lobby(4)));
      const slap = vi.spyOn(store, 'slap').mockReturnValue(true);
      press(' ');
      expect(slap).not.toHaveBeenCalled();
    });
  });

  describe('a race', () => {
    it('givenARace_thenTheBannerNamesTheFirstSlapperAndThePileInvitesMeToSlap', async () => {
      const { wrapper } = await mountRoom(room(kiloDeMerdeRaceState));
      expect(wrapper.get('[data-test="race-banner"]').text()).toContain('Player 3 slapped! Slap!');
      expect(wrapper.findAll('[data-test="seat-rank"]').map(r => r.text()).sort()).toEqual(['1', '2', '3']);
      expect(wrapper.get('[data-test="pile"]').classes()).toContain('pile--racing');
      expect(wrapper.get('[data-test="pile"]').attributes('disabled')).toBeUndefined();
    });

    it('givenIAlreadySlapped_thenThePileNoLongerPulses', async () => {
      const players = kiloDeMerdeRaceState.players.map(p => p.seat === 0 ? { ...p, slapRank: 4 } : p);
      const state: KiloDeMerdeState = { ...kiloDeMerdeRaceState, players, availableActions: [] };
      const { wrapper } = await mountRoom(room(state));
      expect(wrapper.get('[data-test="pile"]').classes()).not.toContain('pile--racing');
      expect(wrapper.get('[data-test="pile"]').attributes('disabled')).toBeDefined();
    });
  });

  describe('a round over', () => {
    it('givenARoundOver_thenTheOutcomeAndEveryHandAreShown', async () => {
      const { wrapper } = await mountRoom(room(kiloDeMerdeRoundOverState));
      expect(wrapper.get('[data-test="round-line"]').text()).toContain('Flush! Player 2 drops all their kilos');
      expect(wrapper.findAll('[data-test="seat-hand"]')).toHaveLength(4);
      expect(wrapper.get('[data-test="seat-plate"][data-seat="2"] [data-test="seat-last"]').exists()).toBe(true);
    });

    it('givenAStepOrARace_thenNoHandIsRevealed', async () => {
      expect((await mountRoom(room(kiloDeMerdeStepState))).wrapper.findAll('[data-test="seat-hand"]')).toHaveLength(0);
    });
  });

  describe('the end', () => {
    it('givenAFinishedGame_thenTheEndScreenListsSharedPlacesAndKilos', async () => {
      const { wrapper } = await mountRoom(room(kiloDeMerdeFinishedState));
      expect(wrapper.get('[data-test="end"]').exists()).toBe(true);
      expect(wrapper.get('[data-test="end-sub"]').text()).toContain('All 5 rounds have been played');
      expect(wrapper.get('[data-test="end-winner"]').text()).toBe('Winners: You and Player 2');
      expect(wrapper.findAll('[data-test="final-rank"]').map(r => r.text())).toEqual(['1', '1', '3', '4']);
      expect(wrapper.findAll('[data-test="final-total"]').map(r => r.text())).toEqual(['0', '0', '24', '113']);
    });
  });

  describe('live regions', () => {
    it('givenASlapEvent_thenTheAssertiveRegionSaysWhoSlapped', async () => {
      const { wrapper, store } = await mountRoom(room(kiloDeMerdeRaceState));
      store.applyEvent({ type: 'event', eventType: 'SLAPPED', eventData: { seat: 3, round: null, step: null, card: null, kilosAfter: null, rank: 1, returnedCount: null } });
      await flushPromises();
      expect(wrapper.get('[data-test="kilo-live-assertive"]').text()).toBe('Player 3 slapped! Slap!');
      expect(wrapper.get('[data-test="kilo-live-assertive"]').attributes('aria-live')).toBe('assertive');
      expect(wrapper.get('[data-test="kilo-live-polite"]').attributes('aria-live')).toBe('polite');
    });

    it('givenAStepEvent_thenThePoliteRegionSaysWhatIReceived', async () => {
      const { wrapper, store } = await mountRoom(room(kiloDeMerdeStepState));
      store.applyEvent({ type: 'event', eventType: 'STEP', eventData: { seat: null, round: 2, step: 3, card: null, kilosAfter: null, rank: null, returnedCount: null } });
      await flushPromises();
      expect(wrapper.get('[data-test="kilo-live-polite"]').text()).toBe('You received J♥ from Player 2');
    });
  });
});
