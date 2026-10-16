import { describe, it, expect, beforeEach, vi } from 'vitest';
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
  afterPass: ['reenter', 'out'], firstLead: ['kingOfHearts', 'lowestClub', 'queenOfHearts', 'random'],
  exchange: ['two', 'one', 'scaled', 'none'], giveBack: ['free', 'lowest'], nextLead: ['scum', 'president'],
  scoring: ['rank', 'titles', 'zeroSum'], length: ['rounds-5', 'rounds-3', 'rounds-10', 'points-11', 'points-21', 'endless'],
}).map(([key, values]) => ({
  key, type: 'CHOICE' as const, values, defaultValue: CLASSIC_PRESIDENT_OPTIONS[key], labelKey: `options.president.${key}`,
}));

function lobby(joined: number, mySeat = 0): LobbyView {
  return {
    started: false, gameId: 'p1',
    players: Array.from({ length: joined }, (_, seat) => ({ seat, name: `P${seat}`, joined: true })),
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

  it('givenALobby_thenShowsTheSharedLobbyWithTheClassicCardAndNoAddBot', async () => {
    const { wrapper } = await mountRoom(room(lobby(2) as unknown as PresidentState));
    expect(wrapper.get('[data-test="lobby"]').text()).toContain('President');
    expect(wrapper.get('[data-test="player-count"]').text()).toContain('2 / 8');
    expect(wrapper.get('[data-test="preset-title"]').text()).toBe('Classic');
    expect(wrapper.find('[data-test="add-bot"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="start"]').attributes('disabled')).toBeDefined();
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
    const rows = wrapper.findAll('[data-test="standing-row"]');
    expect(rows.map(r => r.get('[data-test="standing-name"]').text())).toEqual(['P2', 'You', 'P1']);
    expect(rows.map(r => r.get('[data-test="standing-points"]').text())).toEqual(['9', '6', '2']);
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
    expect(wrapper.findAll('[data-test="end-extra"] [data-test="standing-row"]')).toHaveLength(3);
  });

  it('givenSeveralWinners_thenTheyAreAllNamedAndIAmOneOfThem', async () => {
    const { wrapper } = await mountRoom(room(playing({
      phase: 'FINISHED', availableActions: [], legalPlays: [],
      standings: [{ seat: 0, points: 10 }, { seat: 2, points: 10 }, { seat: 1, points: 3 }],
      outcome: { status: 'FINISHED', winners: [0, 2], reason: 'ROUNDS' },
    })));
    expect(wrapper.get('[data-test="end-winner"]').text()).toBe('Winners: You, P2');
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
});
