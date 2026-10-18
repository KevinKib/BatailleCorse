import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, RouterView } from 'vue-router';
import { defineComponent, h } from 'vue';
import PrimeVue from 'primevue/config';
import { createAppRouter } from '../../router';
import { usePresidentStore } from '../../state/President.store';
import type { LobbyView } from '../../model/lobby/LobbyView';
import type { PresidentState } from '../../model/president/PresidentState';
import {
  buildPresidentHand, buildPresidentPlayer, buildPresidentState, buildPresidentTrick, CLASSIC_PRESIDENT_OPTIONS,
} from '../../model/fixtures/president';
import { messagesEn } from '../../locales/en';

const DESCRIPTORS = Object.entries({
  afterPass: ['reenter', 'out'],
  exchange: ['two', 'one', 'scaled', 'none'], giveBack: ['free', 'lowest'], nextLead: ['scum', 'president'],
  scoring: ['rank', 'titles', 'zeroSum'], length: ['rounds-5', 'rounds-3', 'rounds-10', 'points-11', 'points-21', 'endless'],
}).map(([key, values]) => ({
  key, type: 'CHOICE' as const, values, defaultValue: CLASSIC_PRESIDENT_OPTIONS[key], labelKey: `options.president.${key}`,
}));

const LEVEL = {
  key: 'level', type: 'CHOICE' as const, values: ['easy', 'medium', 'hard'], defaultValue: 'medium', labelKey: 'options.president.level',
};

function lobby(joined: number, mySeat = 0, bots: number[] = []): LobbyView {
  return {
    started: false, gameId: 'p1',
    players: Array.from({ length: joined }, (_, seat) => bots.includes(seat)
      ? { seat, name: 'Bot', joined: true, bot: true, seatOptions: { level: 'medium' } }
      : { seat, name: `P${seat}`, joined: true }),
    seatOptionDescriptors: [LEVEL], removableBotSeats: bots,
    hostSeat: 0, mySeat, minPlayers: 3, maxPlayers: 8, canStart: joined >= 3, lobbyVersion: 1,
    optionDescriptors: DESCRIPTORS, options: { ...CLASSIC_PRESIDENT_OPTIONS }, canEditOptions: true,
  };
}

async function mountRoom(setup: (store: ReturnType<typeof usePresidentStore>) => void) {
  const store = usePresidentStore();
  localStorage.setItem('president:tokens:p1', JSON.stringify({ 0: 'tok' }));
  setup(store);
  vi.spyOn(store, 'hydrate').mockImplementation(() => {});
  vi.spyOn(store, 'restore').mockImplementation(() => {});
  const router = createAppRouter(createMemoryHistory());
  await router.push('/games/president/room/p1');
  await router.isReady();
  const Root = defineComponent({ render: () => h(RouterView) });
  const wrapper = mount(Root, { global: { plugins: [router, PrimeVue] } });
  await flushPromises();
  return { store, router, wrapper };
}

const seatsOf = (n: number, overrides: Record<number, Partial<ReturnType<typeof buildPresidentPlayer>>> = {}) =>
  Array.from({ length: n }, (_, seat) => buildPresidentPlayer(seat, { name: `P${seat}`, ...overrides[seat] }));

function playing(overrides: Partial<PresidentState> = {}): PresidentState {
  return buildPresidentState({
    players: seatsOf(3),
    hand: buildPresidentHand(['3', 'CLUB'], ['9', 'HEART'], ['9', 'SPADE'], ['KING', 'HEART']),
    legalPlays: [{ kind: 'SET', rank: '3', count: 1 }, { kind: 'SET', rank: '9', count: 1 }, { kind: 'SET', rank: '9', count: 2 },
      { kind: 'SET', rank: 'KING', count: 1 }],
    availableActions: ['PLAY'],
    ...overrides,
  });
}

const room = (state: PresidentState, seat = 0) => (s: ReturnType<typeof usePresidentStore>) => {
  s.applyEvent({ type: 'seat-change', seat });
  s.applyEvent({ type: 'state-update', state });
};

describe('PresidentGameScreen', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))); setActivePinia(createPinia()); localStorage.clear(); });

  it('givenNoStateYet_thenShowsTheConnectingScreen', async () => {
    const { wrapper } = await mountRoom(() => {});
    expect(wrapper.get('[data-test="connecting"]').text()).toContain(messagesEn.presidentUi.connecting);
  });

  it('givenALobby_thenShowsTheSharedLobbyWithTheClassicCardAndAddBot', async () => {
    const { wrapper } = await mountRoom(room(lobby(2) as unknown as PresidentState));
    expect(wrapper.get('[data-test="lobby"]').text()).toContain('President');
    expect(wrapper.get('[data-test="player-count"]').text()).toContain('2 / 8');
    expect(wrapper.get('[data-test="preset-title"]').text()).toBe('Classic');
    expect(wrapper.get('[data-test="add-bot"]').text()).toContain('Add bot');
    expect(wrapper.get('[data-test="start"]').attributes('disabled')).toBeDefined();
  });

  it('whenAddBotIsPressed_thenTheBotIsAskedThroughTheStore', async () => {
    const { wrapper, store } = await mountRoom(room(lobby(2) as unknown as PresidentState));
    const addBot = vi.spyOn(store, 'addBot').mockImplementation(() => {});
    await wrapper.get('[data-test="add-bot"]').trigger('click');
    expect(addBot).toHaveBeenCalled();
  });

  it('givenABotSeat_thenTheHostSeesItNamedBot1WithItsLevelAndCanRemoveIt', async () => {
    const { wrapper, store } = await mountRoom(room(lobby(3, 0, [1, 2]) as unknown as PresidentState));
    // My own row is an editable field; the bots' names are text.
    const names = wrapper.findAll('.players li span.player-name').map(n => n.text());
    expect(names).toEqual(['Bot 1', 'Bot 2']);
    expect(wrapper.findAll('[data-test="badge-bot"]')).toHaveLength(2);
    const level = wrapper.get('[data-test="seat-option-1-level"]');
    expect((level.element as HTMLSelectElement).value).toBe('medium');
    expect(level.attributes('aria-label')).toBe('Level for Bot 1');
    expect(level.findAll('option').map(o => o.text())).toEqual(['Easy', 'Medium', 'Hard']);
    const setSeatOption = vi.spyOn(store, 'setSeatOption').mockImplementation(() => {});
    await level.setValue('hard');
    expect(setSeatOption).toHaveBeenCalledWith(1, 'level', 'hard');
    const removeBot = vi.spyOn(store, 'removeBot').mockImplementation(() => {});
    await wrapper.get('[data-test="remove-bot-2"]').trigger('click');
    expect(removeBot).toHaveBeenCalledWith(2);
  });

  it('givenBotsAtTheTable_thenTheirSeatsAreLabelledBotNInSeatOrderAndHumansKeepTheirNames', async () => {
    const { wrapper } = await mountRoom(room(playing({
      players: seatsOf(4, { 1: { bot: true, name: 'Bot' }, 3: { bot: true, name: 'Bot' } }),
    })));
    const labels = wrapper.findAll('[data-test="opponent-seat"] [data-test="seat-label"]').map(l => l.text());
    expect(labels).toEqual(['Bot 1', 'P2', 'Bot 2']);
  });

  it('givenThreePlayers_thenTheHostCanStart', async () => {
    const { wrapper, store } = await mountRoom(room(lobby(3) as unknown as PresidentState));
    const start = vi.spyOn(store, 'startGame').mockImplementation(() => {});
    await wrapper.get('[data-test="start"]').trigger('click');
    expect(start).toHaveBeenCalled();
  });

  it('givenTheHostCustomises_thenTheOptionGoesThroughTheStore', async () => {
    const { wrapper, store } = await mountRoom(room(lobby(3) as unknown as PresidentState));
    const setOption = vi.spyOn(store, 'setOption').mockImplementation(() => {});
    await wrapper.get('[data-test="customise"]').trigger('click');
    await wrapper.get('[data-test="option-afterPass-out"]').setValue(true);
    expect(setOption).toHaveBeenCalledWith('afterPass', 'out');
  });

  it('givenALobbyLeave_thenGoesToTheGameSelection', async () => {
    const { wrapper, store, router } = await mountRoom(room(lobby(2) as unknown as PresidentState));
    vi.spyOn(store, 'leave').mockReturnValue(true);
    await wrapper.get('[data-test="leave-lobby"]').trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.name).toBe('games');
  });

  it('givenMyTurn_thenTheTableShowsTheSeatsTheHandThePileAndTheRuleChips', async () => {
    const { wrapper } = await mountRoom(room(playing({
      round: 2,
      players: seatsOf(3, { 1: { title: 'PRESIDENT', handSize: 5 }, 2: { title: 'SCUM', passed: true } }),
      trick: buildPresidentTrick({
        leader: 1, plays: [{ seat: 1, cards: buildPresidentHand(['5', 'DIAMOND']) }], top: { kind: 'SET', rank: '5', count: 1 },
      }),
    })));
    expect(wrapper.findAll('[data-test="opponent-seat"]')).toHaveLength(2);
    expect(wrapper.text()).toContain('President');
    expect(wrapper.text()).toContain('Scum');
    expect(wrapper.findAll('[data-test^="hand-card-"]')).toHaveLength(4);
    expect(wrapper.get('[data-test="trick-by"]').text()).toBe('P1 played');
    expect(wrapper.findAll('[data-test="rule-chip"]').map(c => c.text())).toContain('To beat: 1 × 5');
    expect(wrapper.get('[data-test="phase-text"]').text()).toBe('Your turn');
    expect(wrapper.get('[data-test="round"]').text()).toBe('Round 2 of 5');
  });

  it.each([3, 5, 8])('given%dPlayers_thenEveryOpponentHasASeat', async (n) => {
    const { wrapper } = await mountRoom(room(playing({
      players: seatsOf(n),
      standings: seatsOf(n).map(p => ({ seat: p.seat, points: 0 })),
    })));
    expect(wrapper.findAll('[data-test="opponent-seat"]')).toHaveLength(n - 1);
  });

  it('givenNothingSelected_thenPlayIsDisabledWithAHintLine', async () => {
    const { wrapper } = await mountRoom(room(playing()));
    expect(wrapper.get('[data-test="play"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-test="play-hint"]').text()).toBe('Tap the cards you want to play.');
  });

  it('givenALegalSelection_thenPlayIsEnabledAndSendsThePlay', async () => {
    const { wrapper, store } = await mountRoom(room(playing()));
    const play = vi.spyOn(store, 'play').mockReturnValue(true);
    await wrapper.get('[data-card="HEART_9"]').trigger('click');
    await wrapper.get('[data-card="SPADE_9"]').trigger('click');
    expect(wrapper.get('[data-card="HEART_9"]').attributes('aria-label')).toBe('9 of hearts, selected');
    expect(wrapper.get('[data-test="play"]').attributes('disabled')).toBeUndefined();
    expect(wrapper.get('[data-test="play-hint"]').text()).toBe('');
    await wrapper.get('[data-test="play"]').trigger('click');
    expect(play).toHaveBeenCalled();
  });

  it('givenAnIllegalSelection_thenPlayStaysDisabledAndTheHintSaysWhy', async () => {
    const { wrapper } = await mountRoom(room(playing()));
    await wrapper.get('[data-card="CLUB_3"]').trigger('click');
    await wrapper.get('[data-card="HEART_KING"]').trigger('click');
    expect(wrapper.get('[data-test="play"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-test="play-hint"]').text()).toBe('Cards of one rank only.');
  });

  describe('equal play', () => {
    const equal = (overrides: Partial<PresidentState> = {}) => playing({
      trick: buildPresidentTrick({
        leader: 1, top: { kind: 'SET', rank: '9', count: 2 }, constraint: 'MATCH_OR_PASS', requiredRank: '9', requiredCount: 2,
      }),
      legalPlays: [{ kind: 'SET', rank: '9', count: 2 }],
      availableActions: ['PLAY', 'PASS'],
      ...overrides,
    });

    it.each([
      ['a higher rank', ['HEART_KING']],
      ['a lower rank', ['CLUB_3']],
      ['the right rank in the wrong number', ['HEART_9']],
    ])('givenAnIllegalSelection_%s_thenTheHintSaysToMatchOrNothing', async (_name, cards) => {
      const { wrapper } = await mountRoom(room(equal()));
      for (const card of cards) await wrapper.get(`[data-card="${card}"]`).trigger('click');
      expect(wrapper.get('[data-test="play-hint"]').text()).toBe('Play 2 9s or nothing.');
      expect(wrapper.get('[data-test="play-hint"]').text()).not.toContain('higher');
    });

    it('givenNoCardOfTheRequiredRank_thenTheHintTellsMeToPass', async () => {
      const { wrapper } = await mountRoom(room(equal({ legalPlays: [], availableActions: ['PASS'] })));
      await wrapper.get('[data-card="HEART_KING"]').trigger('click');
      expect(wrapper.get('[data-test="play"]').attributes('disabled')).toBeDefined();
      expect(wrapper.get('[data-test="play-hint"]').text()).toBe('Nothing you hold can play: pass.');
    });
  });

  it('whenClearIsPressed_thenTheSelectionIsEmpty', async () => {
    const { wrapper } = await mountRoom(room(playing()));
    await wrapper.get('[data-card="CLUB_3"]').trigger('click');
    await wrapper.get('[data-test="clear"]').trigger('click');
    expect(wrapper.get('[data-card="CLUB_3"]').attributes('aria-pressed')).toBe('false');
  });

  it('whenPassIsPressedOnMyTurn_thenThePassGoesThroughTheStore', async () => {
    const { wrapper, store } = await mountRoom(room(playing({ availableActions: ['PLAY', 'PASS'] })));
    const pass = vi.spyOn(store, 'pass').mockReturnValue(true);
    await wrapper.get('[data-test="pass"]').trigger('click');
    expect(pass).toHaveBeenCalled();
  });

  it('givenNotMyTurn_thenTheScreenNamesWhoPlaysAndNothingCanBePlayed', async () => {
    const { wrapper } = await mountRoom(room(playing({ currentSeat: 2, availableActions: [], legalPlays: [] })));
    expect(wrapper.get('[data-test="phase-text"]').text()).toBe('P2 is playing');
    expect(wrapper.get('[data-test="play"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-test="pass"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-test="play-hint"]').text()).toBe('Wait for your turn.');
  });

  describe('exchange', () => {
    const exchanging = (overrides: Partial<PresidentState> = {}) => playing({
      round: 2, phase: 'EXCHANGE', legalPlays: [],
      players: seatsOf(3, { 0: { title: 'PRESIDENT' }, 1: { title: 'NEUTRAL' }, 2: { title: 'SCUM' } }),
      exchange: [{ high: 0, low: 2, count: 2, done: false }],
      availableActions: ['GIVE_BACK'], toGiveBack: 2,
      received: buildPresidentHand(['KING', 'HEART']),
      ...overrides,
    });

    it('givenIOweCards_thenThePanelAsksForTheExactCountAndTheReceivedCardsAreTagged', async () => {
      const { wrapper, store } = await mountRoom(room(exchanging()));
      expect(wrapper.get('[data-test="exchange-role"]').text()).toBe('You are the President');
      expect(wrapper.get('[data-test="exchange-instruction"]').text()).toBe('Choose 2 cards to give to P2.');
      expect(wrapper.get('[data-test="exchange-give"]').text()).toBe('Give 2 cards to P2');
      expect(wrapper.get('[data-test="exchange-give"]').attributes('disabled')).toBeDefined();
      expect(wrapper.find('[data-test="action-bar"]').exists()).toBe(false);
      expect(wrapper.get('[data-card="HEART_KING"]').attributes('data-received')).toBe('true');
      expect(wrapper.findAll('[data-received="true"]')).toHaveLength(1);
      const giveBack = vi.spyOn(store, 'giveBack').mockReturnValue(true);
      await wrapper.get('[data-card="CLUB_3"]').trigger('click');
      await wrapper.get('[data-card="HEART_9"]').trigger('click');
      await wrapper.get('[data-test="exchange-give"]').trigger('click');
      expect(giveBack).toHaveBeenCalled();
    });

    it('whenSuggestIsPressed_thenTheLowestCardsThatBreakNoPairAreSelected', async () => {
      const { wrapper } = await mountRoom(room(exchanging()));
      await wrapper.get('[data-test="exchange-suggest"]').trigger('click');
      // Hand: 3 of clubs, 9 of hearts, 9 of spades, king of hearts; the pair of nines stays, the king goes.
      expect(wrapper.get('[data-card="CLUB_3"]').attributes('aria-pressed')).toBe('true');
      expect(wrapper.get('[data-card="HEART_KING"]').attributes('aria-pressed')).toBe('true');
      expect(wrapper.get('[data-card="HEART_9"]').attributes('aria-pressed')).toBe('false');
      expect(wrapper.get('[data-test="exchange-give"]').attributes('disabled')).toBeUndefined();
    });

    it('givenIAmTheScum_thenThePanelWaitsForTheReturn', async () => {
      const { wrapper } = await mountRoom(room(exchanging({
        availableActions: [], toGiveBack: 0, received: [],
      }), 2));
      expect(wrapper.get('[data-test="exchange-role"]').text()).toBe('You are the Scum');
      expect(wrapper.get('[data-test="exchange-instruction"]').text()).toBe('Your 2 best cards went to P0. Waiting for them to give 2 back.');
      expect(wrapper.find('[data-test="exchange-give"]').exists()).toBe(false);
    });

    it('givenTheDealOfAnExchangeRound_thenTheCardsIReceiveWaitInFlightBeforeShowingInMyHand', async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
      try {
        const over = playing({ round: 1, phase: 'ROUND_OVER', legalPlays: [], availableActions: ['READY'] });
        const { wrapper, store } = await mountRoom(room(over));
        store.applyEvent({ type: 'state-update', state: exchanging({ version: 2 }) });
        await flushPromises();
        expect(wrapper.get('[data-card="HEART_KING"]').classes()).toContain('landing');
        expect(wrapper.get('[data-card="CLUB_3"]').classes()).not.toContain('landing');
        vi.advanceTimersByTime(2500);
        await flushPromises();
        expect(wrapper.get('[data-card="HEART_KING"]').classes()).not.toContain('landing');
      } finally {
        vi.useRealTimers();
      }
    });

    it('givenTheLastGiveBack_thenThePlayBeginsAndTheSummaryTellsWhoGaveWhatToWhom_untilItIsClosed', async () => {
      const done = exchanging({
        phase: 'PLAYING', availableActions: ['PLAY'], toGiveBack: 0, exchange: [{ high: 0, low: 2, count: 2, done: true }],
        received: buildPresidentHand(['KING', 'HEART'], ['ACE', 'CLUB']), given: buildPresidentHand(['3', 'CLUB'], ['9', 'HEART']),
      });
      const { wrapper } = await mountRoom(room(done));
      expect(wrapper.find('[data-test="exchange-panel"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="action-bar"]').exists()).toBe(true);
      const lines = wrapper.findAll('[data-test="recap-line"]');
      expect(lines).toHaveLength(1);
      expect(lines[0].get('[data-test="recap-given"]').text()).toBe('P2 gave P0:');
      expect(lines[0].get('[data-test="recap-given-cards"]').findAll('img').map(i => i.attributes('alt'))).toEqual(['King of hearts', 'Ace of clubs']);
      expect(lines[0].get('[data-test="recap-returned-cards"]').findAll('img').map(i => i.attributes('alt'))).toEqual(['3 of clubs', '9 of hearts']);
      await wrapper.get('[data-test="recap-close"]').trigger('click');
      expect(wrapper.find('[data-test="exchange-recap"]').exists()).toBe(false);
    });

    it('givenTheLastGiveBackJustLanded_thenTheSummaryWaitsForTheFlightsToEnd', async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
      try {
        const { wrapper, store } = await mountRoom(room(exchanging({ availableActions: [], toGiveBack: 0 }), 2));
        store.applyEvent({
          type: 'state-update',
          state: exchanging({
            version: 2, phase: 'PLAYING', availableActions: [], toGiveBack: 0, exchange: [{ high: 0, low: 2, count: 2, done: true }],
            received: buildPresidentHand(['3', 'CLUB'], ['9', 'HEART']), given: buildPresidentHand(['KING', 'HEART'], ['ACE', 'CLUB']),
          }),
        });
        await flushPromises();
        expect(wrapper.find('[data-test="exchange-recap"]').exists()).toBe(false);
        vi.advanceTimersByTime(4000);
        await flushPromises();
        expect(wrapper.find('[data-test="exchange-recap"]').exists()).toBe(true);
      } finally {
        vi.useRealTimers();
      }
    });

    it('givenAPhone_thenTheSummaryBannerGoesByItselfAfterAFewSeconds', async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
      vi.stubGlobal('matchMedia', (query: string) => ({
        matches: query.includes('max-width'), media: query, addEventListener: () => {}, removeEventListener: () => {},
      }));
      try {
        const done = exchanging({
          phase: 'PLAYING', availableActions: ['PLAY'], toGiveBack: 0, exchange: [{ high: 0, low: 2, count: 2, done: true }],
          received: buildPresidentHand(['KING', 'HEART'], ['ACE', 'CLUB']), given: buildPresidentHand(['3', 'CLUB'], ['9', 'HEART']),
        });
        const { wrapper } = await mountRoom(room(done));
        expect(wrapper.find('[data-test="exchange-recap"]').exists()).toBe(true);
        vi.advanceTimersByTime(8500);
        await flushPromises();
        expect(wrapper.find('[data-test="exchange-recap"]').exists()).toBe(false);
      } finally {
        vi.unstubAllGlobals();
        vi.useRealTimers();
      }
    });

    it('givenNoExchangeThisRound_thenThereIsNoSummary', async () => {
      const { wrapper } = await mountRoom(room(playing()));
      expect(wrapper.find('[data-test="exchange-recap"]').exists()).toBe(false);
    });
  });

  const roundOverState = () => playing({
    round: 2, phase: 'ROUND_OVER', availableActions: ['READY'], legalPlays: [],
    players: seatsOf(3, { 0: { place: 3, title: 'PRESIDENT' }, 1: { place: 1, title: 'SCUM' }, 2: { place: 2, title: 'NEUTRAL' } }),
    lastRound: {
      round: 2, finishingOrder: [1, 2, 0],
      players: [
        { seat: 1, place: 1, title: 'PRESIDENT', points: 3, total: 5 },
        { seat: 2, place: 2, title: 'NEUTRAL', points: 2, total: 4 },
        { seat: 0, place: 3, title: 'SCUM', points: 1, total: 3 },
      ],
    },
  });

  it('givenTheRoundIsOver_thenTheSummaryShowsOrderTitlesAndPointsAndReadyGoesThroughTheStore', async () => {
    const { wrapper, store } = await mountRoom(room(roundOverState()));
    expect(wrapper.get('[data-test="round-over-title"]').text()).toBe('Round 2 is over');
    const rows = wrapper.findAll('[data-test="round-over-place"]');
    expect(rows.map(r => r.get('[data-test="place-label"]').text())).toEqual(['1. P1', '2. P2', '3. P0']);
    expect(rows.map(r => r.get('[data-test="place-points"]').text())).toEqual(['+3', '+2', '+1']);
    expect(rows[0].get('[data-test="place-change"]').attributes('data-change')).toBe('up');
    const ready = vi.spyOn(store, 'ready').mockReturnValue(true);
    await wrapper.get('[data-test="ready"]').trigger('click');
    expect(ready).toHaveBeenCalled();
  });

  it('givenTheScoresButton_thenTheStandingsOpenWithTheRunningTotals', async () => {
    const { wrapper } = await mountRoom(room(playing({
      round: 4, rounds: null, options: { ...CLASSIC_PRESIDENT_OPTIONS, length: 'endless' },
      standings: [{ seat: 2, points: 9 }, { seat: 0, points: 6 }, { seat: 1, points: 2 }],
    })));
    expect(wrapper.find('[data-test="scores-panel"]').exists()).toBe(false);
    await wrapper.get('[data-test="scores"]').trigger('click');
    const rows = wrapper.findAll('[data-test^="final-row-"]');
    expect(rows.map(r => r.get('[data-test="final-name"]').text())).toEqual(['P2', 'You', 'P1']);
    expect(rows.map(r => r.get('[data-test="final-total"]').text())).toEqual(['9', '6', '2']);
  });

  it('givenTheMatchIsFinished_thenTheEndScreenNamesTheWinnerAndShowsTheFinalStandings', async () => {
    const { wrapper } = await mountRoom(room(playing({
      phase: 'FINISHED', availableActions: [], legalPlays: [],
      standings: [{ seat: 1, points: 12 }, { seat: 0, points: 8 }, { seat: 2, points: 5 }],
      outcome: { status: 'FINISHED', winners: [1], reason: 'ROUNDS' },
    })));
    expect(wrapper.get('[data-test="end-winner"]').text()).toBe('Winner: P1');
    expect(wrapper.get('[data-test="end-sub"]').text()).toBe('12 points');
    expect(wrapper.get('.end-title').text()).toBe(messagesEn.endScreen.defeat);
    expect(wrapper.findAll('[data-test="end-extra"] [data-test^="final-row-"]')).toHaveLength(3);
  });

  it('givenSeveralWinners_thenTheyAreAllNamedAndIAmOneOfThem', async () => {
    const { wrapper } = await mountRoom(room(playing({
      phase: 'FINISHED', availableActions: [], legalPlays: [],
      standings: [{ seat: 0, points: 10 }, { seat: 2, points: 10 }, { seat: 1, points: 3 }],
      outcome: { status: 'FINISHED', winners: [0, 2], reason: 'ROUNDS' },
    })));
    expect(wrapper.get('[data-test="end-winner"]').text()).toBe('Winners: P0, P2');
    expect(wrapper.get('.end-title').text()).toBe(messagesEn.endScreen.victory);
    expect(wrapper.get('[data-test="end-sub"]').text()).toBe('10 points');
  });

  it('givenAForfeitEnd_thenTheSubtitleSaysWhoLeft', async () => {
    const { wrapper, store } = await mountRoom(room(playing({
      phase: 'FINISHED', availableActions: [], legalPlays: [],
      outcome: { status: 'FINISHED', winners: [0], reason: 'FORFEIT' },
    })));
    store.lastForfeit = { seat: 1, reason: 'RESIGNED' } as typeof store.lastForfeit;
    await flushPromises();
    expect(wrapper.get('[data-test="end-sub"]').text()).toBe('P1 left. You win by default.');
  });

  it('givenMyConnectionIsDown_thenTheTableSaysSo', async () => {
    const { setConnectionOnline } = await import('../../composables/useConnectionStatus');
    const { wrapper } = await mountRoom(room(playing()));
    setConnectionOnline(false);
    await flushPromises();
    expect(wrapper.get('[data-test="offline"]').text()).toContain(messagesEn.presidentUi.table.offline);
    setConnectionOnline(true);
  });

  it('whenBackIsPressed_thenGoesToTheGameSelection', async () => {
    // Leaving a running game asks first, then forfeits.
    window.confirm = vi.fn(() => true);
    const { wrapper, router, store } = await mountRoom(room(playing()));
    const forfeit = vi.spyOn(store, 'forfeit').mockImplementation(() => {});
    await wrapper.get('[data-test="back"]').trigger('click');
    expect(forfeit).toHaveBeenCalled();
    await flushPromises();
    expect(router.currentRoute.value.name).toBe('games');
  });

  describe('the pictures of the table (spec §11 decision 7)', () => {
    const event = (seat: number, action: 'PLAY' | 'PASS', over: Record<string, unknown> = {}) => ({
      type: 'event' as const, eventType: action, message: '',
      eventData: {
        seat, action, cards: action === 'PLAY' ? [{ rank: '2', suit: 'CLUB', name: 'CLUB_2' }] : null, count: null,
        outPlace: null, outForbidden: null, trickWinner: null, trickEndReason: null, barrierOpened: null, ...over,
      },
    });

    it('showsEveryOpponentsHandAsAFanWithItsCount_andTheMotionLayer', async () => {
      const { wrapper } = await mountRoom(room(playing({ players: seatsOf(3, { 1: { handSize: 4 } }) })));
      const fans = wrapper.findAll('[data-test="opponent-seat"] [data-test="seat-fan"]');
      expect(fans).toHaveLength(2);
      expect(wrapper.get('[data-seat="1"] [data-test="seat-cards"]').text()).toBe('4 cards');
      expect(wrapper.findAll('[data-seat="1"] [data-test="fan-back"]')).toHaveLength(4);
      expect(wrapper.find('[data-test="my-zone"] [data-test="seat-fan"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="motion-layer"]').exists()).toBe(true);
    });

    it('givenAPass_thenAPassBubblePopsOverTheSeat', async () => {
      const { wrapper, store } = await mountRoom(room(playing({ currentSeat: 1 })));
      store.applyEvent({ type: 'state-update', state: playing({ version: 2, currentSeat: 2, players: seatsOf(3, { 1: { passed: true } }) }) });
      store.applyEvent(event(1, 'PASS'));
      await flushPromises();
      const bubble = wrapper.get('[data-seat="1"] [data-test="seat-bubble"]');
      expect(bubble.text()).toBe('Pass');
      expect(wrapper.get('.seat-slot[data-seat="1"] .president-seat').classes()).toContain('president-seat--passed');
    });

    it('givenACutWithATwo_thenThePileSaysClosedByATwoAndWhoLeads', async () => {
      const { wrapper, store } = await mountRoom(room(playing({
        currentSeat: 1, trick: buildPresidentTrick({ leader: 0, plays: [{ seat: 0, cards: [{ rank: 'KING', suit: 'HEART', name: 'HEART_KING' }] }] }),
      })));
      store.applyEvent({ type: 'state-update', state: playing({ version: 2, currentSeat: 1 }) });
      store.applyEvent(event(1, 'PLAY', { trickWinner: 1, trickEndReason: 'CUT' }));
      await flushPromises();
      expect(wrapper.get('[data-test="trick-closed-reason"]').text()).toBe('Closed by a 2');
      expect(wrapper.get('[data-test="trick-closed-leader"]').text()).toBe('P1 leads');
      expect(wrapper.findAll('[data-test="closed-trick-card"]')).toHaveLength(1);
      expect(wrapper.get('[data-test="trick-count"]').text()).toBe('2 cards in the trick');
      expect(wrapper.get('[data-test="trick-note"]').text()).toBe('P1 closed the trick with a 2.');
    });

    describe('the sweep of a closed trick (chunk 3)', () => {
      const king = { rank: 'KING', suit: 'HEART', name: 'HEART_KING' };
      const cutAfterAKing = async () => {
        const room3 = await mountRoom(room(playing({
          currentSeat: 1, trick: buildPresidentTrick({ leader: 0, plays: [{ seat: 0, cards: [king] }] }),
        })));
        room3.store.applyEvent({ type: 'state-update', state: playing({ version: 2, currentSeat: 1 }) });
        room3.store.applyEvent(event(1, 'PLAY', { trickWinner: 1, trickEndReason: 'CUT' }));
        await flushPromises();
        return room3;
      };
      const advance = async (ms: number) => { await vi.advanceTimersByTimeAsync(ms); await flushPromises(); };

      beforeEach(() => { vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] }); });
      afterEach(() => { vi.useRealTimers(); });

      it('keepsTheClosingPlayOnThePileForTheBadgeHold_thenSweepsItAwayLeavingTheBadgeAndTheCount', async () => {
        const { wrapper } = await cutAfterAKing();
        expect(wrapper.get('[data-test="trick-cards"]').classes()).not.toContain('trick-cards--swept');
        expect(wrapper.findAll('[data-test="closed-trick-card"]')).toHaveLength(1);
        await advance(4000);
        expect(wrapper.get('[data-test="trick-cards"]').classes()).toContain('trick-cards--swept');
        expect(wrapper.get('[data-test="trick-closed-reason"]').text()).toBe('Closed by a 2');
        expect(wrapper.get('[data-test="trick-count"]').text()).toBe('2 cards in the trick');
      });

      it('givenTheWinnerLeadsAtOnce_thenTheClosedPileStaysUntilTheSweepIsOver_andThenTheNewPlayShows', async () => {
        const { wrapper, store } = await cutAfterAKing();
        const lead = { rank: '3', suit: 'CLUB', name: 'CLUB_3' };
        store.applyEvent({ type: 'state-update', state: playing({
          version: 3, currentSeat: 2, trick: buildPresidentTrick({ leader: 1, plays: [{ seat: 1, cards: [lead] }] }),
        }) });
        store.applyEvent(event(1, 'PLAY', { cards: [lead] }));
        await flushPromises();
        // The state already has the new play; the picture still shows the trick that closed.
        expect(wrapper.find('[data-test="trick-closed"]').exists()).toBe(true);
        expect(wrapper.find('[data-test="trick-card"]').exists()).toBe(false);
        await advance(6000);
        expect(wrapper.find('[data-test="trick-closed"]').exists()).toBe(false);
        expect(wrapper.findAll('[data-test="trick-card"]')).toHaveLength(1);
        expect(wrapper.get('[data-test="trick-cards"]').classes()).not.toContain('trick-cards--swept');
      });

      it('givenManyTricksClosingInABurst_thenThePictureNeverFallsFarBehindTheState', async () => {
        const { wrapper, store } = await cutAfterAKing();
        // Five more tricks closed at once, then an open play: past the queue's bounds the sweeps are skipped.
        for (let version = 3; version < 8; version++) {
          store.applyEvent({ type: 'state-update', state: playing({ version, currentSeat: 1 }) });
          store.applyEvent(event(1, 'PLAY', { trickWinner: 1, trickEndReason: 'QUAD' }));
        }
        const lead = { rank: '3', suit: 'CLUB', name: 'CLUB_3' };
        store.applyEvent({ type: 'state-update', state: playing({
          version: 9, currentSeat: 2, trick: buildPresidentTrick({ leader: 1, plays: [{ seat: 1, cards: [lead] }] }),
        }) });
        store.applyEvent(event(1, 'PLAY', { cards: [lead] }));
        await flushPromises();
        await advance(9000);
        expect(wrapper.find('[data-test="trick-closed"]').exists()).toBe(false);
        expect(wrapper.findAll('[data-test="trick-card"]')).toHaveLength(1);
      });

      it('givenTheInstantSpeed_thenNoSweepAndTheClosingPlayStaysUntilTheNextPlay', async () => {
        localStorage.setItem('animationSpeed', 'instant');
        const { wrapper } = await cutAfterAKing();
        await advance(6000);
        expect(wrapper.get('[data-test="trick-cards"]').classes()).not.toContain('trick-cards--swept');
        expect(wrapper.findAll('[data-test="closed-trick-card"]')).toHaveLength(1);
      });
    });

    it('hasTheSharedSpeedButton_NormalByDefault_cyclingToFast', async () => {
      const { wrapper } = await mountRoom(room(playing()));
      const button = wrapper.get('[data-test="table-footer"] [data-test="speed-button"]');
      expect(button.attributes('aria-label')).toBe('Animation speed: Normal');
      await button.trigger('click');
      expect(wrapper.get('[data-test="speed-button"]').attributes('aria-label')).toBe('Animation speed: Fast');
    });
  });
});
