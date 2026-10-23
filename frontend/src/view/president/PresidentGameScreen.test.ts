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

    describe('the exchange view (the table waits for every human to see the exchange)', () => {
      const phaseLine = (wrapper: { get: (s: string) => { text: () => string } }) => wrapper.get('[data-test="phase-text"]').text();
      const viewing = (overrides: Partial<PresidentState> = {}) => exchanging({
        phase: 'EXCHANGE_VIEW', availableActions: ['SEE_EXCHANGE'], toGiveBack: 0, currentSeat: 2,
        exchange: [{ high: 0, low: 2, count: 2, done: true }],
        received: buildPresidentHand(['KING', 'HEART'], ['ACE', 'CLUB']), given: buildPresidentHand(['3', 'CLUB'], ['9', 'HEART']),
        ...overrides,
      });
      const ui = messagesEn.presidentUi.exchangeView;
      const nobodySaw = () => seatsOf(3, { 0: { title: 'PRESIDENT' }, 1: { title: 'NEUTRAL' }, 2: { title: 'SCUM' } });
      const iSaw = (...others: number[]) => seatsOf(3, {
        0: { title: 'PRESIDENT', sawExchange: true }, 1: { title: 'NEUTRAL', sawExchange: others.includes(1) },
        2: { title: 'SCUM', sawExchange: others.includes(2) },
      });
      const spyAck = (store: ReturnType<typeof usePresidentStore>) =>
        vi.spyOn(store, 'seeExchange').mockImplementation(() => true);
      const afterTimers = async (ms: number) => { vi.advanceTimersByTime(ms); await flushPromises(); };

      beforeEach(() => { vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] }); });
      afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {}))); });

      it('givenIHaveNotSeenItYet_thenThePhaseLineSaysTheExchange_andPlayAndPassAreDisabled', async () => {
        const { wrapper } = await mountRoom(room(viewing({ players: nobodySaw() })));
        expect(phaseLine(wrapper)).toBe(ui.watching);
        expect(wrapper.get('[data-test="play"]').attributes('disabled')).toBeDefined();
        expect(wrapper.get('[data-test="pass"]').attributes('disabled')).toBeDefined();
        expect(wrapper.find('[data-test="exchange-panel"]').exists()).toBe(false);
      });

      it('givenIHaveSeenItAndOneHumanIsLeft_thenThePhaseLineNamesThem', async () => {
        const { wrapper } = await mountRoom(room(viewing({ players: iSaw(2), availableActions: [] })));
        expect(phaseLine(wrapper)).toBe('Waiting for P1 to see the exchange');
      });

      it('givenTwoHumansLeftOrMore_thenThePhaseLineNamesThemOrCountsThem', async () => {
        const five = (...saw: number[]) => Array.from({ length: 5 }, (_, seat) =>
          buildPresidentPlayer(seat, { name: `P${seat}`, sawExchange: saw.includes(seat), bot: seat === 4 ? true : undefined }));
        const two = await mountRoom(room(viewing({ players: five(0, 3), availableActions: [] })));
        expect(phaseLine(two.wrapper)).toBe('Waiting for P1 and P2');
        two.wrapper.unmount();
        const many = await mountRoom(room(viewing({ players: five(0), availableActions: [] })));
        expect(phaseLine(many.wrapper)).toBe('Waiting for 3 players');
      });

      it('givenABotThatHasNotAcknowledged_thenNobodyWaitsForIt', async () => {
        const players = [
          buildPresidentPlayer(0, { name: 'P0', sawExchange: true }), buildPresidentPlayer(1, { name: 'P1', bot: true }),
          buildPresidentPlayer(2, { name: 'P2', bot: true }),
        ];
        const { wrapper } = await mountRoom(room(viewing({ players, availableActions: [] })));
        expect(phaseLine(wrapper)).toBe(ui.starting);
      });

      it('givenAReload_thenTheSummaryShowsAtOnce_andTheAcknowledgementComesAfterThreeSeconds', async () => {
        const { wrapper, store } = await mountRoom(room(viewing({ players: nobodySaw() })));
        const ack = spyAck(store);
        expect(wrapper.find('[data-test="exchange-recap"]').exists()).toBe(true);
        await afterTimers(2900);
        expect(ack).not.toHaveBeenCalled();
        await afterTimers(200);
        expect(ack).toHaveBeenCalledTimes(1);
      });

      it('givenAReloadAtFast_thenTheAcknowledgementComesAfterOneAndAHalfSeconds', async () => {
        localStorage.setItem('animationSpeed', 'fast');
        const { store } = await mountRoom(room(viewing({ players: nobodySaw() })));
        const ack = spyAck(store);
        await afterTimers(1400);
        expect(ack).not.toHaveBeenCalled();
        await afterTimers(200);
        expect(ack).toHaveBeenCalledTimes(1);
      });

      it('givenInstant_thenItAcknowledgesAtOnce', async () => {
        localStorage.setItem('animationSpeed', 'instant');
        const spy = vi.spyOn(usePresidentStore(), 'seeExchange').mockImplementation(() => true);
        await mountRoom(room(viewing({ players: nobodySaw() })));
        await afterTimers(0);
        expect(spy).toHaveBeenCalledTimes(1);
      });

      it('whenTheSummaryCrossIsPressed_thenItAcknowledgesAtOnce_andOnlyOnce', async () => {
        const { wrapper, store } = await mountRoom(room(viewing({ players: nobodySaw() })));
        const ack = spyAck(store);
        await wrapper.get('[data-test="recap-close"]').trigger('click');
        expect(ack).toHaveBeenCalledTimes(1);
        expect(wrapper.find('[data-test="exchange-recap"]').exists()).toBe(false);
        await afterTimers(5000);
        expect(ack).toHaveBeenCalledTimes(1);
      });

      describe('after the flights of the state that opened the view', () => {
        const over = () => playing({ round: 1, phase: 'ROUND_OVER', legalPlays: [], availableActions: ['READY'] });

        it('givenNormalSpeed_thenItAcknowledgesWhenTheFlightsHaveLandedAndTheSummaryHasShownThreeSeconds', async () => {
          const { store } = await mountRoom(room(over()));
          const ack = spyAck(store);
          store.applyEvent({ type: 'state-update', state: viewing({ version: 2, players: nobodySaw() }) });
          await flushPromises();
          await afterTimers(4000);
          expect(ack).not.toHaveBeenCalled();
          await afterTimers(1000);
          expect(ack).toHaveBeenCalledTimes(1);
        });

        it('givenFast_thenTheSummaryIsReadInHalfTheTime', async () => {
          localStorage.setItem('animationSpeed', 'fast');
          const { store } = await mountRoom(room(over()));
          const ack = spyAck(store);
          store.applyEvent({ type: 'state-update', state: viewing({ version: 2, players: nobodySaw() }) });
          await flushPromises();
          await afterTimers(2500);
          expect(ack).not.toHaveBeenCalled();
          await afterTimers(1000);
          expect(ack).toHaveBeenCalledTimes(1);
        });

        it('givenInstant_thenItAcknowledgesAtOnceEvenOnTheChangeThatOpensTheView', async () => {
          localStorage.setItem('animationSpeed', 'instant');
          const { store } = await mountRoom(room(over()));
          const ack = spyAck(store);
          store.applyEvent({ type: 'state-update', state: viewing({ version: 2, players: nobodySaw() }) });
          await flushPromises();
          expect(ack).toHaveBeenCalledTimes(1);
        });

        it('givenReducedMotionAtNormal_thenTheRingsStillCountAsTheFlightsThenThreeSeconds', async () => {
          localStorage.setItem('animationSpeed', 'normal');
          vi.stubGlobal('matchMedia', (query: string) => ({
            matches: query.includes('prefers-reduced-motion'), media: query, addEventListener: () => {}, removeEventListener: () => {},
          }));
          const { store } = await mountRoom(room(over()));
          const ack = spyAck(store);
          store.applyEvent({ type: 'state-update', state: viewing({ version: 2, players: nobodySaw() }) });
          await flushPromises();
          await afterTimers(4000);
          expect(ack).not.toHaveBeenCalled();
          await afterTimers(1000);
          expect(ack).toHaveBeenCalledTimes(1);
        });

        it('givenMoreStatesInTheView_thenTheTimerIsNotRearmedNorSentTwice', async () => {
          const { store } = await mountRoom(room(over()));
          const ack = spyAck(store);
          store.applyEvent({ type: 'state-update', state: viewing({ version: 2, players: nobodySaw() }) });
          await flushPromises();
          await afterTimers(3000);
          store.applyEvent({ type: 'state-update', state: viewing({ version: 3, players: seatsOf(3, { 1: { sawExchange: true } }) }) });
          await flushPromises();
          await afterTimers(2000);
          expect(ack).toHaveBeenCalledTimes(1);
          await afterTimers(10000);
          expect(ack).toHaveBeenCalledTimes(1);
        });

        it('givenThePlayStartsBeforeTheTimer_thenNothingIsSent', async () => {
          const { store } = await mountRoom(room(over()));
          const ack = spyAck(store);
          store.applyEvent({ type: 'state-update', state: viewing({ version: 2, players: nobodySaw() }) });
          await flushPromises();
          await afterTimers(1000);
          store.applyEvent({ type: 'state-update', state: viewing({ version: 3, phase: 'PLAYING', availableActions: ['PLAY'] }) });
          await flushPromises();
          await afterTimers(10000);
          expect(ack).not.toHaveBeenCalled();
        });

        it('givenTheScreenIsLeft_thenTheTimerIsCleared', async () => {
          const { store, wrapper } = await mountRoom(room(over()));
          const ack = spyAck(store);
          store.applyEvent({ type: 'state-update', state: viewing({ version: 2, players: nobodySaw() }) });
          await flushPromises();
          wrapper.unmount();
          await afterTimers(10000);
          expect(ack).not.toHaveBeenCalled();
        });
      });
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

    it('givenACutWithATwo_thenThePileSaysClosedByATwoAndWhoLeads_onceTheTwoHasLanded', async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] });
      const { wrapper, store } = await mountRoom(room(playing({
        currentSeat: 1, trick: buildPresidentTrick({ leader: 0, plays: [{ seat: 0, cards: [{ rank: 'KING', suit: 'HEART', name: 'HEART_KING' }] }] }),
      })));
      store.applyEvent({ type: 'state-update', state: playing({ version: 2, currentSeat: 1 }) });
      store.applyEvent(event(1, 'PLAY', { trickWinner: 1, trickEndReason: 'CUT' }));
      await flushPromises();
      // The 2 is still flying: the pile still says who played the king.
      expect(wrapper.find('[data-test="trick-closed"]').exists()).toBe(false);
      expect(wrapper.get('[data-test="trick-by"]').text()).toBe('You played');
      await vi.advanceTimersByTimeAsync(900);
      await flushPromises();
      vi.useRealTimers();
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
        // The 2 has landed: the badge says why the trick closed.
        await advance(900);
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

    describe('the end of a round (chunk 4): the table finishes, then the round sheet, then the final scores', () => {
      const advance = async (ms: number) => { await vi.advanceTimersByTimeAsync(ms); await flushPromises(); };
      const finalPlay = { rank: '3', suit: 'CLUB', name: 'CLUB_3' };
      const places = { 0: { place: 3, title: 'PRESIDENT' as const }, 1: { place: 1, title: 'SCUM' as const }, 2: { place: 2, title: 'NEUTRAL' as const } };
      const scored = (round: number) => ({
        round, finishingOrder: [1, 2, 0],
        players: [
          { seat: 1, place: 1, title: 'PRESIDENT' as const, points: 3, total: 5, forbiddenFinish: false },
          { seat: 2, place: 2, title: 'NEUTRAL' as const, points: 2, total: 4, forbiddenFinish: false },
          { seat: 0, place: 3, title: 'SCUM' as const, points: 1, total: 3, forbiddenFinish: false },
        ],
      });
      const roundOverAfterAPlay = (over: Partial<PresidentState> = {}) => playing({
        version: 2, round: 2, phase: 'ROUND_OVER', availableActions: ['READY'], legalPlays: [],
        players: seatsOf(3, places), lastRound: scored(2), ...over,
      });
      /** A table in round 2 where seat 1 is about to play the last card of the round. */
      const midRound = () => mountRoom(room(playing({ round: 2, currentSeat: 1, players: seatsOf(3, { 1: { handSize: 1 } }) })));
      const lastPlay = (store: ReturnType<typeof usePresidentStore>, state: PresidentState) => {
        store.applyEvent({ type: 'state-update', state });
        store.applyEvent(event(1, 'PLAY', { cards: [finalPlay], outPlace: 1 }));
      };

      beforeEach(() => { vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] }); });
      afterEach(() => { vi.useRealTimers(); });

      it('givenTheLastPlayEndsTheRound_thenTheSheetWaitsForTheFlightAndTheTableToSettle_andNothingElseAsksForAnAction', async () => {
        const { wrapper, store } = await midRound();
        lastPlay(store, roundOverAfterAPlay());
        await flushPromises();
        expect(wrapper.find('[data-test="round-over"]').exists()).toBe(false);
        expect(wrapper.find('[data-test="action-bar"]').exists()).toBe(false);
        await advance(1500);
        expect(wrapper.find('[data-test="round-over"]').exists()).toBe(false);
        await advance(1500);
        expect(wrapper.get('[data-test="round-over-title"]').text()).toBe('Round 2 is over');
        expect(wrapper.find('[data-test="ready"]').exists()).toBe(true);
      });

      it('givenTheLastPlayClosesATrick_thenTheSheetWaitsForTheBadgeAndTheSweepToo', async () => {
        const king = { rank: 'KING', suit: 'HEART', name: 'HEART_KING' };
        const { wrapper, store } = await mountRoom(room(playing({
          round: 2, currentSeat: 1, players: seatsOf(3, { 1: { handSize: 1 } }),
          trick: buildPresidentTrick({ leader: 0, plays: [{ seat: 0, cards: [king] }] }),
        })));
        store.applyEvent({ type: 'state-update', state: roundOverAfterAPlay() });
        store.applyEvent(event(1, 'PLAY', { cards: [finalPlay], outPlace: 1, trickWinner: 1, trickEndReason: 'CUT' }));
        await flushPromises();
        await advance(2500);
        expect(wrapper.find('[data-test="round-over"]').exists()).toBe(false);
        await advance(4000);
        expect(wrapper.find('[data-test="round-over"]').exists()).toBe(true);
      });

      it('givenTheInstantSpeed_thenTheSheetComesRightAfterTheLastPlay', async () => {
        localStorage.setItem('animationSpeed', 'instant');
        const { wrapper, store } = await midRound();
        lastPlay(store, roundOverAfterAPlay());
        await flushPromises();
        await advance(50);
        expect(wrapper.find('[data-test="round-over"]').exists()).toBe(true);
      });

      it('givenTheLastRoundOfTheMatch_thenTheRoundSheetShowsFirstAndTheFinalScoresOnlyOnceContinued', async () => {
        const { wrapper, store } = await midRound();
        lastPlay(store, roundOverAfterAPlay({
          phase: 'FINISHED', availableActions: [], outcome: { status: 'FINISHED', winners: [1], reason: 'POINTS' },
          standings: [{ seat: 1, points: 12 }, { seat: 0, points: 8 }, { seat: 2, points: 5 }],
        }));
        await flushPromises();
        expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
        expect(wrapper.find('[data-test="round-over"]').exists()).toBe(false);
        await advance(4000);
        expect(wrapper.find('[data-test="round-over"]').exists()).toBe(true);
        expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
        expect(wrapper.find('[data-test="ready"]').exists()).toBe(false);
        // The players read their round: the final scores do not come by themselves.
        await advance(60_000);
        expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
        await wrapper.get('[data-test="final-scores"]').trigger('click');
        await flushPromises();
        expect(wrapper.get('[data-test="end-winner"]').text()).toBe('Winner: P1');
        expect(wrapper.findAll('[data-test="end-extra"] [data-test^="final-row-"]')).toHaveLength(3);
      });

      it('givenTheMatchEndsByAForfeit_thenTheFinalScoresComeAfterTheTableSettles_withoutARoundSheet', async () => {
        const { wrapper, store } = await midRound();
        store.applyEvent({ type: 'state-update', state: playing({
          version: 2, round: 2, phase: 'FINISHED', availableActions: [], legalPlays: [],
          outcome: { status: 'FINISHED', winners: [0], reason: 'FORFEIT' },
        }) });
        await flushPromises();
        expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
        await advance(2000);
        expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
        expect(wrapper.find('[data-test="round-over"]').exists()).toBe(false);
      });

      it('givenTheNextRoundStarts_thenTheSheetGoesAndThePlayBarComesBack', async () => {
        const { wrapper, store } = await midRound();
        lastPlay(store, roundOverAfterAPlay());
        await advance(5000);
        expect(wrapper.find('[data-test="round-over"]').exists()).toBe(true);
        store.applyEvent({ type: 'state-update', state: playing({ version: 3, round: 3 }) });
        await flushPromises();
        expect(wrapper.find('[data-test="round-over"]').exists()).toBe(false);
        expect(wrapper.find('[data-test="action-bar"]').exists()).toBe(true);
      });
    });

    describe('the texts follow the pictures (the displayed state, never the latest state)', () => {
      const advance = async (ms: number) => { await vi.advanceTimersByTimeAsync(ms); await flushPromises(); };
      const card = (rank: string, suit: string) => ({ rank, suit, name: `${suit}_${rank}` });
      const seven = card('7', 'CLUB');
      const nine = card('9', 'HEART');
      const nineAgain = card('9', 'SPADE');
      const four = (over: Record<number, Partial<ReturnType<typeof buildPresidentPlayer>>> = {}) => seatsOf(4, over);
      const bindingText = (wrapper: Awaited<ReturnType<typeof mountRoom>>['wrapper']) =>
        wrapper.find('[data-test="binding"]').exists() ? wrapper.get('[data-test="binding"]').text() : null;
      // Three bots play 7, 9, 9 in a burst (the state of each play comes at once): only the second 9 binds the trick.
      const sevenNineNine = (store: ReturnType<typeof usePresidentStore>) => {
        store.applyEvent({ type: 'state-update', state: playing({
          version: 2, currentSeat: 2, players: four({ 1: { handSize: 7 } }),
          trick: buildPresidentTrick({ leader: 1, plays: [{ seat: 1, cards: [seven] }], top: { kind: 'SET', rank: '7', count: 1 }, constraint: 'BEAT' }),
        }) });
        store.applyEvent(event(1, 'PLAY', { cards: [seven] }));
        store.applyEvent({ type: 'state-update', state: playing({
          version: 3, currentSeat: 3, players: four({ 1: { handSize: 7 }, 2: { handSize: 7 } }),
          trick: buildPresidentTrick({ leader: 1, plays: [{ seat: 1, cards: [seven] }, { seat: 2, cards: [nine] }], top: { kind: 'SET', rank: '9', count: 1 }, constraint: 'BEAT' }),
        }) });
        store.applyEvent(event(2, 'PLAY', { cards: [nine] }));
        store.applyEvent({ type: 'state-update', state: playing({
          version: 4, currentSeat: 0, players: four({ 1: { handSize: 7 }, 2: { handSize: 7 }, 3: { handSize: 7 } }),
          trick: buildPresidentTrick({
            leader: 1, plays: [{ seat: 1, cards: [seven] }, { seat: 2, cards: [nine] }, { seat: 3, cards: [nineAgain] }],
            top: { kind: 'SET', rank: '9', count: 1 }, constraint: 'MATCH_OR_PASS', requiredRank: '9', requiredCount: 1,
            chain: { rank: '9', cards: [nine, nineAgain], plays: 2 },
          }),
        }) });
        store.applyEvent(event(3, 'PLAY', { cards: [nineAgain] }));
      };
      const opponentLands = 180 + 700;

      beforeEach(() => { vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] }); });
      afterEach(() => { vi.useRealTimers(); });

      it('givenSevenNineNine_thenTheBannerComesOnlyOnceTheSecondNineHasLanded', async () => {
        const { wrapper, store } = await mountRoom(room(playing({ currentSeat: 1, players: four() })));
        sevenNineNine(store);
        await flushPromises();
        expect(bindingText(wrapper)).toBeNull();
        expect(wrapper.get('[data-test="phase-text"]').text()).toBe('P1 is playing');
        await advance(opponentLands + 20);
        // The 7 has landed: the table says so, nothing more.
        expect(bindingText(wrapper)).toBeNull();
        expect(wrapper.get('[data-test="trick-by"]').text()).toBe('P1 played');
        expect(wrapper.get('[data-test="phase-text"]').text()).toBe('P2 is playing');
        expect(wrapper.get('[data-seat="1"] [data-test="seat-cards"]').text()).toBe('7 cards');
        expect(wrapper.get('[data-seat="2"] [data-test="seat-cards"]').text()).toBe('8 cards');
        await advance(opponentLands);
        expect(bindingText(wrapper)).toBeNull();
        expect(wrapper.get('[data-test="trick-by"]').text()).toBe('P2 played');
        expect(wrapper.get('[data-test="phase-text"]').text()).toBe('P3 is playing');
        await advance(opponentLands);
        expect(bindingText(wrapper)).toContain('9');
        expect(bindingText(wrapper)).toContain('or nothing');
        expect(wrapper.get('[data-test="trick-by"]').text()).toBe('P3 played');
        expect(wrapper.get('[data-test="phase-text"]').text()).toBe('Your turn');
        expect(wrapper.get('[data-seat="3"] [data-test="seat-cards"]').text()).toBe('7 cards');
      });

      it('givenIPlay_thenMyTurnEndsAtOnce_whileMyCardsFly', async () => {
        const { wrapper, store } = await mountRoom(room(playing({ currentSeat: 0, players: four() })));
        expect(wrapper.get('[data-test="phase-text"]').text()).toBe('Your turn');
        const three = card('3', 'CLUB');
        store.applyEvent({ type: 'state-update', state: playing({
          version: 2, currentSeat: 1, players: four({ 0: { handSize: 7 } }), availableActions: [],
          trick: buildPresidentTrick({ leader: 0, plays: [{ seat: 0, cards: [three] }], top: { kind: 'SET', rank: '3', count: 1 }, constraint: 'BEAT' }),
        }) });
        store.applyEvent(event(0, 'PLAY', { cards: [three] }));
        await flushPromises();
        // "Your turn" always means my cards can be played: it goes with my click, the next seat is told instead.
        expect(wrapper.get('[data-test="phase-text"]').text()).toBe('P1 is playing');
        // The pile still says what the table has shown (my card is in the air).
        expect(wrapper.find('[data-test="trick-by"]').exists()).toBe(false);
        await advance(1000);
        expect(wrapper.get('[data-test="trick-by"]').text()).toBe('You played');
        expect(wrapper.get('[data-test="phase-text"]').text()).toBe('P1 is playing');
      });

      it('givenAnOpponentsLonePairInFlight_thenCompleteShowsAtOnce_beforeTheTextsTellThePlay', async () => {
        const { wrapper, store } = await mountRoom(room(playing({ currentSeat: 1, players: four() })));
        const pair = [card('4', 'SPADE'), card('4', 'HEART')];
        store.applyEvent({ type: 'state-update', state: playing({
          version: 2, currentSeat: 2, players: four({ 1: { handSize: 6 } }), availableActions: ['COMPLETE'],
          completion: [card('4', 'DIAMOND'), card('4', 'CLUB')],
          trick: buildPresidentTrick({ leader: 1, plays: [{ seat: 1, cards: pair }], top: { kind: 'SET', rank: '4', count: 2 },
            constraint: 'BEAT', chain: { rank: '4', cards: pair, plays: 1 } }),
        }) });
        store.applyEvent(event(1, 'PLAY', { cards: pair }));
        await flushPromises();
        // The pair is still flying (the texts still say P1 is playing), but the button follows the latest state.
        expect(wrapper.get('[data-test="phase-text"]').text()).toBe('P1 is playing');
        expect(wrapper.find('[data-test="complete"]').exists()).toBe(true);
      });

      it('givenTheInstantSpeed_thenTheTextsAreTheLatestStateAtOnce', async () => {
        localStorage.setItem('animationSpeed', 'instant');
        const { wrapper, store } = await mountRoom(room(playing({ currentSeat: 1, players: four() })));
        sevenNineNine(store);
        await flushPromises();
        expect(bindingText(wrapper)).toContain('or nothing');
        expect(wrapper.get('[data-test="trick-by"]').text()).toBe('P3 played');
        expect(wrapper.get('[data-test="phase-text"]').text()).toBe('Your turn');
      });

      it('givenABotWinsTheTrickThenLeads_thenItsCountNeverGoesDownThenUp', async () => {
        const king = card('KING', 'HEART');
        const lead = card('3', 'CLUB');
        const { wrapper, store } = await mountRoom(room(playing({
          currentSeat: 2, players: seatsOf(3, { 0: { passed: true }, 1: { handSize: 5 } }),
          trick: buildPresidentTrick({ leader: 1, plays: [{ seat: 1, cards: [king] }], top: { kind: 'SET', rank: 'KING', count: 1 }, constraint: 'BEAT', passed: [0] }),
        })));
        // Seat 2 passes: everyone passed, seat 1 sets the trick aside, then leads at once.
        store.applyEvent({ type: 'state-update', state: playing({ version: 2, currentSeat: 1, players: seatsOf(3, { 1: { handSize: 5 } }) }) });
        store.applyEvent(event(2, 'PASS', { trickWinner: 1, trickEndReason: 'ALL_PASSED' }));
        store.applyEvent({ type: 'state-update', state: playing({
          version: 3, currentSeat: 2, players: seatsOf(3, { 1: { handSize: 4 } }),
          trick: buildPresidentTrick({ leader: 1, plays: [{ seat: 1, cards: [lead] }], top: { kind: 'SET', rank: '3', count: 1 } }),
        }) });
        store.applyEvent(event(1, 'PLAY', { cards: [lead] }));
        await flushPromises();
        const counts: number[] = [];
        for (let t = 0; t < 8000; t += 50) {
          counts.push(Number(wrapper.get('[data-seat="1"] [data-test="seat-cards"]').text().split(' ')[0]));
          await advance(50);
        }
        expect(counts[0]).toBe(5);
        expect(counts[counts.length - 1]).toBe(4);
        // Never down then up: the count only ever drops.
        counts.forEach((n, i) => { if (i > 0) expect(n).toBeLessThanOrEqual(counts[i - 1]); });
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
