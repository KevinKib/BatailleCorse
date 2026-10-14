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

    it('givenNoActionOnACard_thenNoSlotIsEnabledAndTheDrawnCardIsStillNamed', async () => {
      const { wrapper } = await started({
        phase: 'TURN_END', hasDrawn: true, drawnCard: buildKoboCard('3', 'CLUB'), availableActions: ['END_TURN'],
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

    it('keepsTheActionZoneUnderMyCards', async () => {
      const { wrapper } = await started();
      expect(wrapper.find('[data-test="my-zone"] [data-test="kobo-actions"]').exists()).toBe(true);
    });
  });

  describe('memorisation and turn actions', () => {
    const KING = buildKoboCard('KING', 'HEART');
    const SEVEN = buildKoboCard('7', 'CLUB');

    function playing(overrides = {}, count = 2, mySeat = 0) {
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
    const enabledSlots = (wrapper: ReturnType<typeof mount>) =>
      wrapper.findAll('button[data-test^="slot-"]').filter(b => b.attributes('disabled') === undefined)
        .map(b => b.attributes('data-test'));
    const press = async (wrapper: ReturnType<typeof mount>, id: string) => {
      await wrapper.get(`[data-test="action-${id}"]`).trigger('click');
    };

    it('givenMemorising_thenMyTwoStartingCardsAreFaceUpAndIHaveMemorisedSendsReady', async () => {
      const { wrapper, store } = await playing({
        phase: 'MEMORISING', availableActions: ['READY'],
        startingCards: [{ slot: 2, card: KING }, { slot: 3, card: SEVEN }],
      });
      const mine = wrapper.findAll('[data-test="my-zone"] button[data-test^="slot-"]').map(b => b.attributes('aria-label'));
      expect(mine).toEqual([
        'Your card 1, face down', 'Your card 2, face down', 'Your card 3: king of heart', 'Your card 4: 7 of club',
      ]);
      expect(wrapper.find('[data-test="opponent-seat"] [aria-label$="king of heart"]').exists()).toBe(false);
      const ready = vi.spyOn(store, 'ready').mockReturnValue(true);
      await press(wrapper, 'ready');
      expect(ready).toHaveBeenCalledTimes(1);
    });

    it('givenIHaveMemorised_thenTheCardsAreFaceDownAndNoButtonIsLeft', async () => {
      const { wrapper } = await playing({
        phase: 'MEMORISING', readySeats: [0], availableActions: [],
        startingCards: [{ slot: 2, card: KING }, { slot: 3, card: SEVEN }],
      });
      const mine = wrapper.findAll('[data-test="my-zone"] button[data-test^="slot-"]');
      expect(mine.every(b => b.attributes('aria-label')!.endsWith('face down'))).toBe(true);
      expect(wrapper.find('[data-test="action-ready"]').exists()).toBe(false);
    });

    it('givenMyDrawStep_thenDrawIsTheButtonAndNoCardIsClickable', async () => {
      const { wrapper, store } = await playing({ phase: 'DRAW', availableActions: ['DRAW', 'MATCH_DISCARD'] });
      expect(enabledSlots(wrapper)).toEqual([]);
      expect(wrapper.find('[data-test="action-match"]').exists()).toBe(true);
      const draw = vi.spyOn(store, 'draw').mockReturnValue(true);
      await press(wrapper, 'draw');
      expect(draw).toHaveBeenCalledTimes(1);
    });

    it('givenTheDrawnCard_thenOnlyMyCardsAreClickableAndAClickPicksTheSlot', async () => {
      const { wrapper, store } = await playing({
        phase: 'DECISION', hasDrawn: true, drawnCard: KING, availableActions: ['SWAP_DRAWN', 'DISCARD_DRAWN'],
      });
      expect(enabledSlots(wrapper)).toEqual(['slot-0-0', 'slot-0-1', 'slot-0-2', 'slot-0-3']);
      expect(wrapper.get('[data-test="prompt"]').text()).toBe(messagesEn.koboUi.prompt.swapDrawn);
      const pick = vi.spyOn(store, 'pickSlot').mockImplementation(() => {});
      await wrapper.get('[data-test="slot-0-1"]').trigger('click');
      expect(pick).toHaveBeenCalledWith(0, 1);
      const discard = vi.spyOn(store, 'discardDrawn').mockReturnValue(true);
      await press(wrapper, 'discard');
      expect(discard).toHaveBeenCalledTimes(1);
    });

    it('givenAPeekOtherPower_thenOnlyTheOthersCardsAreClickableAndSkipIsOffered', async () => {
      const { wrapper, store } = await playing({
        phase: 'POWER', pendingPower: 'PEEK_OTHER', availableActions: ['PEEK', 'SKIP_POWER'],
      });
      expect(enabledSlots(wrapper)).toEqual(['slot-1-0', 'slot-1-1', 'slot-1-2', 'slot-1-3']);
      expect(wrapper.get('[data-test="prompt"]').text()).toBe(messagesEn.koboUi.prompt.peekOther);
      const skip = vi.spyOn(store, 'skipPower').mockReturnValue(true);
      await press(wrapper, 'skip');
      expect(skip).toHaveBeenCalledTimes(1);
    });

    it('givenTheTurnEnd_thenEndTurnAndKoboAreButtons', async () => {
      const { wrapper, store } = await playing({ phase: 'TURN_END', availableActions: ['END_TURN', 'ANNOUNCE_KOBO'] });
      const end = vi.spyOn(store, 'endTurn').mockReturnValue(true);
      const announce = vi.spyOn(store, 'announce').mockReturnValue(true);
      await press(wrapper, 'endTurn');
      await press(wrapper, 'announce');
      expect(end).toHaveBeenCalledTimes(1);
      expect(announce).toHaveBeenCalledTimes(1);
    });

    it('givenNotMyTurn_thenNoButtonAndNoClickableCard', async () => {
      const { wrapper } = await playing({ phase: 'DRAW', currentSeat: 1, availableActions: [] }, 2, 0);
      expect(wrapper.findAll('[data-test="kobo-actions"] button')).toHaveLength(0);
      expect(enabledSlots(wrapper)).toEqual([]);
    });

    it('givenABlindSwapFirstClick_thenMySlotIsMarkedCancelIsOfferedAndEscapeDropsIt', async () => {
      const { wrapper, store } = await playing({
        phase: 'POWER', pendingPower: 'BLIND_SWAP', availableActions: ['BLIND_SWAP', 'SKIP_POWER'],
      });
      expect(wrapper.get('[data-test="prompt"]').text()).toBe(messagesEn.koboUi.prompt.blindOwn);
      await wrapper.get('[data-test="slot-0-2"]').trigger('click');
      expect(store.pending).toEqual({ kind: 'blind-own', ownSlot: 2 });
      expect(wrapper.get('[data-test="slot-0-2"]').attributes('aria-pressed')).toBe('true');
      expect(wrapper.get('[data-test="prompt"]').text()).toBe(messagesEn.koboUi.prompt.blindTarget);
      expect(enabledSlots(wrapper)).toEqual(['slot-1-0', 'slot-1-1', 'slot-1-2', 'slot-1-3']);
      expect(wrapper.find('[data-test="action-cancel"]').exists()).toBe(true);

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      await flushPromises();
      expect(store.pending).toEqual({ kind: 'none' });
      expect(wrapper.find('[data-test="action-cancel"]').exists()).toBe(false);
    });

    it('givenCancelIsPressed_thenTheSelectionIsDropped', async () => {
      const { wrapper, store } = await playing({
        phase: 'POWER', pendingPower: 'BLIND_SWAP', availableActions: ['BLIND_SWAP', 'SKIP_POWER'],
      });
      await wrapper.get('[data-test="slot-0-0"]').trigger('click');
      await press(wrapper, 'cancel');
      expect(store.pending).toEqual({ kind: 'none' });
    });

    it('givenACommandInFlight_thenButtonsAndCardsAreDisabled', async () => {
      const { wrapper, store } = await playing({
        phase: 'DECISION', hasDrawn: true, drawnCard: KING, availableActions: ['SWAP_DRAWN', 'DISCARD_DRAWN'],
      });
      store.busy = true;
      await flushPromises();
      expect(enabledSlots(wrapper)).toEqual([]);
      expect(wrapper.get('[data-test="action-discard"]').attributes('disabled')).toBeDefined();
    });

    it('givenARefusedCommand_thenTheNoticeShowsAndTheBarIsEnabledAgain', async () => {
      const { wrapper, store } = await playing({ phase: 'DRAW', availableActions: ['DRAW'] });
      store.busy = true;
      store.applyEvent({ type: 'error', eventType: 'DRAW', message: 'nope' });
      await flushPromises();
      expect(wrapper.get('[data-test="notice"]').text()).toBe(messagesEn.koboUi.notice.moveImpossible);
      expect(wrapper.get('[data-test="action-draw"]').attributes('disabled')).toBeUndefined();
    });

    it('givenAPeekedCard_thenItIsFaceUpUntilHideIsPressed', async () => {
      const { wrapper } = await playing({
        phase: 'TURN_END', availableActions: ['END_TURN'],
        reveal: { seat: 1, slot: 0, revision: 0, card: KING },
      });
      expect(wrapper.get('[data-test="slot-1-0"]').attributes('aria-label')).toContain('king of heart');
      await press(wrapper, 'hide');
      expect(wrapper.get('[data-test="slot-1-0"]').attributes('aria-label')).toContain('face down');
      expect(wrapper.find('[data-test="action-hide"]').exists()).toBe(false);
    });

    describe('matching discard and event log', () => {
      const NINE = buildKoboCard('9', 'SPADE');
      const matchable = (overrides = {}) => playing({
        phase: 'DRAW', currentSeat: 1, availableActions: ['MATCH_DISCARD'], discardTop: NINE, ...overrides,
      });
      const eventData = (overrides: Record<string, unknown>) => ({
        seat: 1, action: 'MATCH', slot: null, targetSeat: null, targetSlot: null, card: null,
        givenSlot: null, success: null, barrierOpened: null, ...overrides,
      });

      it('givenMatchIsListedDuringAnotherPlayersTurn_thenTheMatchButtonIsThereAndNoCardIsClickableYet', async () => {
        const { wrapper } = await matchable();
        expect(wrapper.get('[data-test="action-match"]').text()).toBe(messagesEn.koboUi.actions.match);
        expect(wrapper.get('[data-test="action-match"]').attributes('aria-pressed')).toBe('false');
        expect(enabledSlots(wrapper)).toEqual([]);
      });

      it('givenMatchIsNotListed_thenNoMatchButton', async () => {
        const { wrapper } = await playing({ phase: 'DRAW', availableActions: ['DRAW'] });
        expect(wrapper.find('[data-test="action-match"]').exists()).toBe(false);
      });

      it('givenMatchIsPressed_thenEveryCardOfEveryPlayerCanBePickedAndCancelIsOffered', async () => {
        const { wrapper } = await matchable();
        await press(wrapper, 'match');
        expect(wrapper.get('[data-test="action-match"]').attributes('aria-pressed')).toBe('true');
        expect(wrapper.get('[data-test="prompt"]').text()).toBe(messagesEn.koboUi.prompt.matchPick);
        expect(enabledSlots(wrapper)).toHaveLength(8);
        expect(wrapper.find('[data-test="action-cancel"]').exists()).toBe(true);
      });

      it('givenMatchIsPressedTwice_thenTheModeIsDisarmed', async () => {
        const { wrapper, store } = await matchable();
        await press(wrapper, 'match');
        await press(wrapper, 'match');
        expect(store.pending).toEqual({ kind: 'none' });
        expect(enabledSlots(wrapper)).toEqual([]);
      });

      it('givenAnotherPlayersCardIsPicked_thenOnlyMyCardsCanBePickedToGive', async () => {
        const { wrapper, store } = await matchable();
        await press(wrapper, 'match');
        await wrapper.get('[data-test="slot-1-2"]').trigger('click');
        expect(store.pending.kind).toBe('match-give');
        expect(wrapper.get('[data-test="prompt"]').text()).toBe(messagesEn.koboUi.prompt.matchGive);
        expect(enabledSlots(wrapper)).toEqual(['slot-0-0', 'slot-0-1', 'slot-0-2', 'slot-0-3']);
      });

      it('givenMyCardIsPickedWhileArmed_thenItIsTheStoreThatSendsTheMatch', async () => {
        const { wrapper, store } = await matchable();
        const pick = vi.spyOn(store, 'pickSlot');
        await press(wrapper, 'match');
        await wrapper.get('[data-test="slot-0-1"]').trigger('click');
        expect(pick).toHaveBeenCalledWith(0, 1);
      });

      it('givenEscape_thenTheArmedModeIsDropped', async () => {
        const { wrapper, store } = await matchable();
        await press(wrapper, 'match');
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        await flushPromises();
        expect(store.pending).toEqual({ kind: 'none' });
        expect(wrapper.get('[data-test="action-match"]').attributes('aria-pressed')).toBe('false');
      });

      it('givenACommandInFlight_thenMatchIsStillAvailable', async () => {
        const { wrapper, store } = await matchable({ currentSeat: 0, phase: 'TURN_END', availableActions: ['END_TURN', 'MATCH_DISCARD'] });
        store.busy = true;
        await flushPromises();
        expect(wrapper.get('[data-test="action-endTurn"]').attributes('disabled')).toBeDefined();
        expect(wrapper.get('[data-test="action-match"]').attributes('disabled')).toBeUndefined();
      });

      it('givenARefusedMatch_thenSaysTooLateAndMatchCanBeTriedAgain', async () => {
        const { wrapper, store } = await matchable();
        await press(wrapper, 'match');
        store.applyEvent({ type: 'error', eventType: 'MATCH', message: 'late' });
        await flushPromises();
        expect(wrapper.get('[data-test="notice"]').text()).toBe(messagesEn.koboUi.notice.matchTooLate);
        expect(wrapper.get('[data-test="action-match"]').attributes('disabled')).toBeUndefined();
      });

      it('givenNoEvent_thenTheLogRegionIsThereAndEmpty', async () => {
        const { wrapper } = await matchable();
        expect(wrapper.get('[data-test="event-log-line"]').attributes('aria-live')).toBe('polite');
        expect(wrapper.get('[data-test="event-log-line"]').text()).toBe('');
      });

      it('givenAnOpponentsMatchOnMyCard_thenTheLogSaysItAndTheNamedSlotsAreMarked', async () => {
        const { wrapper, store } = await matchable({
          players: [buildKoboPlayer(0, { name: 'Ann' }), buildKoboPlayer(1, { name: 'Bob', current: true })],
        });
        store.applyEvent({
          type: 'event', eventType: 'MATCH', message: '',
          eventData: eventData({ seat: 1, targetSeat: 0, targetSlot: 2, givenSlot: 0, success: true, card: NINE }),
        });
        await flushPromises();
        expect(wrapper.get('[data-test="event-log-line"]').text()).toBe('Bob matched your card 3 (9 of spade), gave card 1');
        expect(wrapper.get('[data-test="slot-0-2"]').attributes('data-highlighted')).toBe('true');
        // The slot Bob gave from is named too; nothing else is.
        expect(wrapper.get('[data-test="slot-1-0"]').attributes('data-highlighted')).toBe('true');
        expect(wrapper.findAll('[data-highlighted]')).toHaveLength(2);
      });

      it('givenAMissedMatchOfMine_thenTheLogSaysIAmPenalised', async () => {
        const { wrapper, store } = await matchable();
        store.applyEvent({ type: 'event', eventType: 'MATCH', message: '', eventData: eventData({ seat: 0, success: false }) });
        await flushPromises();
        expect(wrapper.get('[data-test="event-log-line"]').text()).toBe('You missed a match and took a penalty card');
        expect(wrapper.get('[data-test="event-log"]').attributes('data-outcome')).toBe('bad');
      });

      it('givenAnEndOfTurnAfterAPeek_thenTheHighlightStaysWithTheLineThatStillSaysIt', async () => {
        const { wrapper, store } = await matchable();
        store.applyEvent({ type: 'event', eventType: 'PEEK', message: '', eventData: eventData({ seat: 1, action: 'PEEK', targetSeat: 0, targetSlot: 1 }) });
        store.applyEvent({ type: 'event', eventType: 'END_TURN', message: '', eventData: eventData({ seat: 1, action: 'END_TURN' }) });
        await flushPromises();
        expect(wrapper.get('[data-test="event-log-line"]').text()).toBe('Player 1 looked at your card 2');
        expect(wrapper.get('[data-test="slot-0-1"]').attributes('data-highlighted')).toBe('true');
      });

      it('givenTheNextEvent_thenItReplacesTheHighlight', async () => {
        const { wrapper, store } = await matchable();
        store.applyEvent({ type: 'event', eventType: 'PEEK', message: '', eventData: eventData({ seat: 1, action: 'PEEK', targetSeat: 0, targetSlot: 1 }) });
        await flushPromises();
        expect(wrapper.get('[data-test="slot-0-1"]').attributes('data-highlighted')).toBe('true');
        store.applyEvent({ type: 'event', eventType: 'SWAP', message: '', eventData: eventData({ seat: 1, action: 'SWAP', slot: 3, card: NINE }) });
        await flushPromises();
        expect(wrapper.find('[data-test="slot-0-1"]').attributes('data-highlighted')).toBeUndefined();
        expect(wrapper.get('[data-test="slot-1-3"]').attributes('data-highlighted')).toBe('true');
      });
    });

    it('stopsListeningToEscapeOnceTheScreenIsGone', async () => {
      const { wrapper, store } = await playing({ phase: 'DRAW', availableActions: ['DRAW'] });
      const cancel = vi.spyOn(store, 'cancelPending');
      wrapper.unmount();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      expect(cancel).not.toHaveBeenCalled();
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
