import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import PrimeVue from 'primevue/config';
import NinetyNineGameScreen from './NinetyNineGameScreen.vue';
import { useNinetyNineStore } from '../../state/NinetyNine.store';
import { messagesEn } from '../../locales/en';
import {
  buildNinetyNineBust, buildNinetyNineCard, buildNinetyNineFourSeatRound, buildNinetyNineQuickEnd,
  buildNinetyNineTwelveSeatRound, DEFAULT_NINETY_NINE_OPTIONS,
} from '../../model/fixtures/ninetynine';
import type { LobbyView } from '../../model/lobby/LobbyView';
import type { NinetyNineState } from '../../model/ninetynine/NinetyNineState';

const DESCRIPTORS = [
  { key: 'lives', type: 'CHOICE' as const, values: ['3', '2', '4'], defaultValue: '3', labelKey: 'options.ninetyNine.lives' },
  { key: 'quick', type: 'CHOICE' as const, values: ['off', 'on'], defaultValue: 'off', labelKey: 'options.ninetyNine.quick' },
];

function lobby(joined: number, mySeat = 0): LobbyView {
  return {
    started: false, gameId: 'n1',
    players: Array.from({ length: joined }, (_, seat) => ({ seat, name: `P${seat}`, joined: true })),
    seatOptionDescriptors: [], removableBotSeats: [],
    hostSeat: 0, mySeat, minPlayers: 2, maxPlayers: 12, canStart: joined >= 2, lobbyVersion: 1,
    optionDescriptors: DESCRIPTORS, options: { ...DEFAULT_NINETY_NINE_OPTIONS }, canEditOptions: true,
  };
}

async function mountRoom(setup: (store: ReturnType<typeof useNinetyNineStore>) => void) {
  const store = useNinetyNineStore();
  localStorage.setItem('ninety-nine:tokens:n1', JSON.stringify({ 0: 'tok' }));
  setup(store);
  vi.spyOn(store, 'hydrate').mockImplementation(async () => 'ok');
  vi.spyOn(store, 'restore').mockImplementation(() => {});
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', name: 'games', component: { render: () => null } }, { path: '/:rest(.*)*', component: { render: () => null } }],
  });
  await router.push('/ninety-nine/room/n1');
  await router.isReady();
  const wrapper = mount(NinetyNineGameScreen, { props: { gameId: 'n1' }, global: { plugins: [router, PrimeVue] } });
  await flushPromises();
  return { store, router, wrapper };
}

const room = (state: NinetyNineState | LobbyView, seat = 0) => (s: ReturnType<typeof useNinetyNineStore>) => {
  s.applyEvent({ type: 'seat-change', seat });
  s.applyEvent({ type: 'state-update', state: state as never });
};

const names = (wrapper: Awaited<ReturnType<typeof mountRoom>>['wrapper']) => wrapper.findAll('[data-test="seat-name"]').map(n => n.text());

describe('NinetyNineGameScreen', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))); setActivePinia(createPinia()); localStorage.clear(); });

  it('givenARound_thenTheRulesPanelOpensWithTheRulesOf99', async () => {
    const { wrapper } = await mountRoom(room(buildNinetyNineFourSeatRound()));
    expect(wrapper.find('[data-cy="rules-toggle"]').exists()).toBe(true);
    await wrapper.get('[data-cy="rules-toggle"]').trigger('click');
    expect(wrapper.get('[data-cy="rules-panel"]').text()).toContain(messagesEn.ninetyNine.sections[0].title);
  });

  it('givenNoStateYet_thenShowsTheConnectingScreen', async () => {
    const { wrapper } = await mountRoom(() => {});
    expect(wrapper.get('[data-test="connecting"]').text()).toContain(messagesEn.ninetyNineUi.connecting);
  });

  describe('lobby', () => {
    it('givenALobby_thenTheSharedLobbyShowsLivesQuickModeAndTheSeats', async () => {
      const { wrapper } = await mountRoom(room(lobby(12)));
      expect(wrapper.get('[data-test="lobby"]').text()).toContain('99');
      expect(wrapper.get('[data-test="player-count"]').text()).toContain('12 / 12');
      expect(wrapper.get('[data-test="option-lives"]').text()).toContain('Lives');
      expect(wrapper.get('[data-test="option-quick"]').text()).toContain('Quick mode');
      expect(wrapper.get('[data-test="quick-hint"]').exists()).toBe(true);
      expect(wrapper.get('[data-test="start"]').exists()).toBe(true);
    });

    it('givenTheHostPicksLives_thenTheOptionGoesThroughTheStore', async () => {
      const { wrapper, store } = await mountRoom(room(lobby(3)));
      const setOption = vi.spyOn(store, 'setOption').mockImplementation(() => {});
      await wrapper.get('[data-test="option-lives-2"]').setValue(true);
      expect(setOption).toHaveBeenCalledWith('lives', '2');
    });

    it('givenTheHostStarts_thenTheStoreStartsTheGame', async () => {
      const { wrapper, store } = await mountRoom(room(lobby(3)));
      const start = vi.spyOn(store, 'startGame').mockImplementation(() => {});
      await wrapper.get('[data-test="start"]').trigger('click');
      expect(start).toHaveBeenCalled();
    });
  });

  describe('playing', () => {
    it('givenAFourSeatRound_thenEverythingTheTableKnowsIsOnScreen', async () => {
      const { wrapper } = await mountRoom(room(buildNinetyNineFourSeatRound()));
      expect(names(wrapper)).toEqual(['You', 'Player 1', 'Player 2', 'Player 3']);
      expect(wrapper.get('[data-test="total"]').text()).toContain('42');
      expect(wrapper.get('[data-test="pile"]').attributes('aria-label')).toBe('Pile: Jack of diamonds');
      expect(wrapper.get('[data-test="stock-count"]').text()).toBe('37');
      expect(wrapper.get('[data-test="round"]').text()).toBe('Round 1');
      expect(wrapper.find('[data-cy="game-timer"]').exists()).toBe(true);
      expect(wrapper.findAll('[data-test="hand"] button')).toHaveLength(3);
      expect(wrapper.get('[data-test="status"]').text()).toContain('Your turn');
      expect(wrapper.get('[data-test="last-play"]').text()).toBe('Player 3 played Jack of diamonds.');
      expect(wrapper.findAll('[data-test="seat-plate"]')).toHaveLength(4);
      expect(wrapper.findAll('[data-test="heart"][data-full="true"]')).toHaveLength(12);
    });

    it('givenMyTurn_thenAPlainSafeCardIsPlayedThroughTheStore', async () => {
      const { wrapper, store } = await mountRoom(room(buildNinetyNineFourSeatRound()));
      const play = vi.spyOn(store, 'play').mockReturnValue(true);
      await wrapper.get('[data-card="HEART_7"]').trigger('click');
      expect(play).toHaveBeenCalledWith(buildNinetyNineCard('7', 'HEART'));
    });

    it('givenTheAceWithTwoValues_thenTheChooserOpensAndTheChosenValueIsPlayed', async () => {
      const { wrapper, store } = await mountRoom(room(buildNinetyNineFourSeatRound()));
      const play = vi.spyOn(store, 'play').mockReturnValue(true);
      expect(wrapper.find('[data-test="ace-chooser"]').exists()).toBe(false);
      await wrapper.get('[data-card="SPADE_ACE"]').trigger('click');
      expect(play).not.toHaveBeenCalled();
      expect(wrapper.get('[data-test="ace-chooser"]').exists()).toBe(true);
      expect(wrapper.get('[data-card="SPADE_ACE"]').attributes('aria-pressed')).toBe('true');
      await wrapper.get('[data-test="ace-11"]').trigger('click');
      expect(play).toHaveBeenCalledWith(buildNinetyNineCard('ACE', 'SPADE'), 11);
      expect(wrapper.find('[data-test="ace-chooser"]').exists()).toBe(false);
    });

    it('givenTheAceChooser_thenEscapeClosesItWithoutPlaying', async () => {
      const { wrapper, store } = await mountRoom(room(buildNinetyNineFourSeatRound()));
      const play = vi.spyOn(store, 'play').mockReturnValue(true);
      await wrapper.get('[data-card="SPADE_ACE"]').trigger('click');
      await wrapper.get('[data-test="ace-chooser"]').trigger('keydown', { key: 'Escape' });
      expect(wrapper.find('[data-test="ace-chooser"]').exists()).toBe(false);
      expect(play).not.toHaveBeenCalled();
    });

    it('givenAnAceWithOneAllowedValue_thenItIsPlayedAtOnceWithoutAChooser', async () => {
      const state = buildNinetyNineFourSeatRound();
      state.playable = state.playable.map(p => p.card.rank === 'ACE' ? { ...p, aceValues: [1] } : p);
      const { wrapper, store } = await mountRoom(room(state));
      const play = vi.spyOn(store, 'play').mockReturnValue(true);
      await wrapper.get('[data-card="SPADE_ACE"]').trigger('click');
      expect(wrapper.find('[data-test="ace-chooser"]').exists()).toBe(false);
      expect(play).toHaveBeenCalledWith(buildNinetyNineCard('ACE', 'SPADE'));
    });

    it('givenTwelveSeatsAtNinetyFour_thenUnsafeCardsAreDisabledWithTheReasonAndTheDirectionIsReversed', async () => {
      const { wrapper } = await mountRoom(room(buildNinetyNineTwelveSeatRound()));
      expect(wrapper.findAll('[data-test="seat-plate"]')).toHaveLength(12);
      expect(wrapper.get('[data-test="reversed"]').text()).toBe('Reversed');
      expect(wrapper.get('[data-card="SPADE_8"]').attributes('disabled')).toBeDefined();
      expect(wrapper.get('[data-card="CLUB_QUEEN"]').attributes('disabled')).toBeDefined();
      expect(wrapper.get('[data-card="HEART_9"]').attributes('disabled')).toBeUndefined();
      expect(wrapper.findAll('[data-test^="over-limit-"]')).toHaveLength(2);
      expect(wrapper.get('[data-test="seat-out"]').text()).toBe('OUT · 12th');
    });

    it('givenAnotherSeatsTurn_thenTheStatusNamesItAndTheHandWaits', async () => {
      const state = buildNinetyNineFourSeatRound();
      state.currentSeat = 2;
      state.availableActions = [];
      const { wrapper } = await mountRoom(room(state));
      expect(wrapper.get('[data-test="status"]').text()).toContain('Waiting for Player 2…');
      expect(wrapper.get('[data-card="HEART_7"]').attributes('disabled')).toBeDefined();
      expect(wrapper.findAll('[data-test="seat-plate"][data-current="true"]').map(p => p.attributes('data-seat'))).toEqual(['2']);
    });

    it('givenBotsAtTheTable_thenTheyAreNamedBotNInSeatOrder', async () => {
      const state = buildNinetyNineFourSeatRound();
      state.players = state.players.map(p => p.seat === 1 || p.seat === 3 ? { ...p, bot: true, name: null } : p);
      const { wrapper } = await mountRoom(room(state));
      expect(names(wrapper)).toEqual(['You', 'Bot 1', 'Player 2', 'Bot 2']);
    });

    it('givenAForfeitNotice_thenItNamesTheSeatThatLeft', async () => {
      const { wrapper, store } = await mountRoom(room(buildNinetyNineFourSeatRound()));
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', eventData: { loserSeat: 2, reason: 'RESIGNED' } } as never);
      await flushPromises();
      expect(wrapper.get('[data-test="forfeit-banner"]').text()).toBe('Player 2 left the game');
    });

    it('givenTheAnimationSpeedButton_thenItIsOnTheTable', async () => {
      const { wrapper } = await mountRoom(room(buildNinetyNineFourSeatRound()));
      expect(wrapper.find('.speed').exists()).toBe(true);
    });
  });

  describe('end of a round', () => {
    it('givenABust_thenTheStuckHandIsRevealedTheSeatIsHitAndNothingCanBePlayed', async () => {
      const { wrapper } = await mountRoom(room(buildNinetyNineBust()));
      expect(wrapper.get('[data-test="bust-title"]').text()).toBe('Player 2 is stuck at 96');
      expect(wrapper.findAll('[data-test="bust-card"]')).toHaveLength(3);
      expect(wrapper.get('[data-test="bust-lives"]').text()).toBe('Loses a life · 2 left');
      expect(wrapper.get('[data-test="bust-next"]').exists()).toBe(true);
      expect(wrapper.get('[data-test="seat-hit"]').text()).toBe('−1');
      expect(wrapper.get('[data-test="round"]').text()).toBe('End of round 1');
      for (const b of wrapper.findAll('[data-test="hand"] button')) expect(b.attributes('disabled')).toBeDefined();
    });

    it('givenMyOwnBust_thenTheBannerSaysYou', async () => {
      const state = buildNinetyNineBust();
      state.lastBust = { ...state.lastBust!, seat: 0 };
      const { wrapper } = await mountRoom(room(state));
      expect(wrapper.get('[data-test="bust-title"]').text()).toBe('You are stuck at 96');
    });

    it('givenTheNextRoundStarts_thenTheBannerIsGone', async () => {
      const { wrapper, store } = await mountRoom(room(buildNinetyNineBust()));
      store.applyEvent({ type: 'state-update', state: { ...buildNinetyNineFourSeatRound(), version: 99 } as never });
      await flushPromises();
      expect(wrapper.find('[data-test="bust"]').exists()).toBe(false);
    });
  });

  describe('end of the game', () => {
    it('givenAQuickModeEnd_thenWinnersSharedPlacesAndTheQuickLineAreShown', async () => {
      const { wrapper } = await mountRoom(room(buildNinetyNineQuickEnd()));
      expect(wrapper.get('[data-test="end"]').exists()).toBe(true);
      expect(wrapper.get('[data-test="end-winner"]').text()).toBe('Winners: You and Player 1');
      expect(wrapper.get('[data-test="end-sub"]').text()).toBe('Quick mode: Player 2 lost the last life. Places by lives left.');
      expect(wrapper.findAll('[data-test="place"]').map(p => p.text())).toEqual(['1st', '1st', '3rd']);
    });

    it('givenAnEndedGame_thenPlayAgainGoesThroughTheStore', async () => {
      const { wrapper, store } = await mountRoom(room(buildNinetyNineQuickEnd()));
      const again = vi.spyOn(store, 'playAgain').mockImplementation(() => {});
      await wrapper.get('[data-cy="play-again"]').trigger('click');
      expect(again).toHaveBeenCalled();
    });

    it('givenTheLastPlayerStanding_thenTheSubtitleSaysSo', async () => {
      const state = buildNinetyNineQuickEnd();
      state.options = { lives: '3', quick: 'off' };
      state.outcome = { status: 'FINISHED', winners: [1], reason: 'LAST_STANDING' };
      const { wrapper } = await mountRoom(room(state));
      expect(wrapper.get('[data-test="end-winner"]').text()).toBe('Winner: Player 1');
      expect(wrapper.get('[data-test="end-sub"]').text()).toBe('Last player standing.');
    });
  });
});
