import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import PrimeVue from 'primevue/config';
import BullshitGameScreen from './BullshitGameScreen.vue';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import OpponentSeat from '../../components/bullshit/OpponentSeat.vue';
import { useBullshitStore } from '../../state/Bullshit.store';
import type { BullshitState } from '../../model/bullshit/BullshitState';
import type { LobbyView } from '../../model/bullshit/LobbyView';
import { setConnectionOnline } from '../../composables/useConnectionStatus';

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
    { path: '/', component: { template: '<div/>' } },
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
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

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
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

    expect(wrapper.find('[data-test="reveal"]').exists()).toBe(true);
  });

  it('shows a BLUFF verdict and a flip face per revealed card on a false claim', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState() });
    store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', message: '',
      eventData: { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0,
        revealedCards: [{ rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' }, { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }] } });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

    expect(wrapper.get('[data-test="verdict"]').text()).toBe('BLUFF');
    expect(wrapper.findAll('.flip-card')).toHaveLength(2);
    expect(wrapper.findAll('.flip-front').length).toBe(2);
    expect(wrapper.findAll('.flip-back').length).toBe(2);
  });

  it('shows a TRUTHFUL verdict on a true claim', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState() });
    store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', message: '',
      eventData: { callerSeat: 1, claimantSeat: 0, truthful: true, pickerSeat: 1, revealedCards: [{ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }] } });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

    expect(wrapper.get('[data-test="verdict"]').text()).toBe('TRUTHFUL');
  });

  it('shows the claim badge and a last-play caption only when a claim is on the table', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState({
      currentTarget: { label: 'QUEEN' },
      table: { state: 'CLAIM', claimantId: '1', count: 3 },
      discardPileSize: 7,
    }) });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

    expect(wrapper.get('[data-test="claim-badge"]').text()).toContain('QUEEN');
    expect(wrapper.get('[data-test="last-play"]').text()).toContain('Player 2');  // claimantId 1 -> "Player 2"
    expect(wrapper.get('[data-test="last-play"]').text()).toContain('3');
  });

  it('shows the suit symbol next to a suit claim, keeping the word', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState({ currentTarget: { label: 'HEART' } }) });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

    const suit = wrapper.get('[data-test="claim-suit"]');
    expect(suit.text()).toBe('♥');
    expect(suit.classes()).toContain('claim-suit--red');
    expect(wrapper.get('[data-test="claim-badge"]').text()).toContain('HEART');
  });

  it('shows a black suit symbol for spades and none for a rank claim', async () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState({ currentTarget: { label: 'SPADE' } }) });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });
    expect(wrapper.get('[data-test="claim-suit"]').text()).toBe('♠');
    expect(wrapper.get('[data-test="claim-suit"]').classes()).not.toContain('claim-suit--red');

    store.applyEvent({ type: 'state-update', state: playingState({ currentTarget: { label: 'ACE' } }) });
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-test="claim-suit"]').exists()).toBe(false);
  });

  it('shows my own card count next to the You tag, with an accessible name', async () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState({
      myHand: [
        { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' },
        { rank: '2', suit: 'CLUB', name: 'CLUB_2' },
      ],
    }) });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

    const count = wrapper.get('[data-test="my-count"]');
    expect(count.text()).toBe('2');
    expect(count.attributes('aria-label')).toBe('2 cards in your hand');
    expect(wrapper.get('.my-tag').text()).toBe('You');
  });

  it('hides the last-play caption when there is no claim', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState({ table: { state: 'NO_CLAIM' } }) });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

    expect(wrapper.find('[data-test="last-play"]').exists()).toBe(false);
  });

  it('renders the lobby panel with joined players', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: lobbyView() });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

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
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

    expect(wrapper.get('[data-test="player-count"]').text()).toContain('1 / 6');
    expect(wrapper.get('[data-test="start-hint"]').text()).toContain('1 more player');
  });

  it('shows a host Start button that is disabled until canStart and calls startGame on click', async () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: lobbyView({ canStart: false }) });
    const startGame = vi.spyOn(store, 'startGame').mockImplementation(() => {});
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

    const startBtn = wrapper.get('[data-test="start"]');
    expect((startBtn.element as HTMLButtonElement).disabled).toBe(true);

    store.applyEvent({ type: 'state-update', state: lobbyView({ canStart: true }) });
    await wrapper.vm.$nextTick();
    expect((startBtn.element as HTMLButtonElement).disabled).toBe(false);

    await startBtn.trigger('click');
    expect(startGame).toHaveBeenCalled();
  });

  it('renders one opponent seat per opponent, positioned around the table', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState({
      players: [
        { id: '0', handCount: 5, isCurrentPlayer: false },
        { id: '1', handCount: 4, isCurrentPlayer: true },
        { id: '2', handCount: 3, isCurrentPlayer: false },
        { id: '3', handCount: 2, isCurrentPlayer: false },
      ],
    }) });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

    const seats = wrapper.findAllComponents(OpponentSeat);
    expect(seats).toHaveLength(3);                       // 4 players, minus me (seat 0)
    expect(seats.some(s => s.props('active') === true)).toBe(true);  // seat 1 is current
  });

  it('renders the end-game overlay when finished and play-again calls playAgain', async () => {
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

    const spy = vi.spyOn(store, 'playAgain').mockResolvedValue(undefined);
    await overlay.vm.$emit('playAgain');
    expect(spy).toHaveBeenCalled();
  });

  describe('opponent presence', () => {
    beforeEach(() => vi.useFakeTimers({ now: 1_000_000 }));
    afterEach(() => vi.useRealTimers());

    it('shows a disconnect badge with countdown on a dropped opponent seat', () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: playingState() });   // players 0 (me) + 1
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: Date.now() + 30_000 }, message: '' });

      const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

      const badge = wrapper.get('[data-test="seat-disconnect-badge"]');
      expect(badge.text()).toContain('30');
    });

    it('shows a forfeit banner naming the forfeiting player', async () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: playingState() });
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', eventData: { loserSeat: 1 }, message: '' });

      const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

      expect(wrapper.get('[data-test="forfeit-banner"]').text()).toBe('Player 2 forfeited');
    });
  });

  it('renders a Back-to-home control during play (leave guard handles forfeit-on-leave)', () => {
    const store = useBullshitStore();
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: playingState() });
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

    expect(wrapper.find('[data-test="leave"]').exists()).toBe(true);
  });
  describe('player names', () => {
    const named = () => playingState({
      players: [
        { id: '0', handCount: 5, isCurrentPlayer: false, name: 'Alice' },
        { id: '1', handCount: 4, isCurrentPlayer: true, name: 'Bobby' },
        { id: '2', handCount: 3, isCurrentPlayer: false, name: null },
        { id: '3', handCount: 2, isCurrentPlayer: false, bot: true },
      ],
      table: { state: 'CLAIM', claimantId: '1', count: 2 },
    });
    const mountNamed = () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: named() });
      return { store, wrapper: mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } }) };
    };

    it('labels opponent seats with names, then Player N, then Bot N', () => {
      const { wrapper } = mountNamed();
      const labels = wrapper.findAll('[data-test="seat-label"]').map(l => l.text());
      expect(labels).toEqual(['Bobby', 'Player 3', 'Bot 1']);
      expect(wrapper.get('.my-tag').text()).toBe('You');
    });

    it('uses names in the last-play line, the reveal caption and the forfeit banner', () => {
      const { store, wrapper } = mountNamed();
      expect(wrapper.get('[data-test="last-play"]').text()).toContain('Bobby');
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', eventData: { loserSeat: 1 }, message: '' });
      store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', message: '',
        eventData: { callerSeat: 0, claimantSeat: 1, truthful: false, pickerSeat: 1, revealedCards: [] } });
      return wrapper.vm.$nextTick().then(() => {
        expect(wrapper.get('[data-test="forfeit-banner"]').text()).toBe('Bobby forfeited');
        const caption = wrapper.get('.reveal-caption').text();
        expect(caption).toContain('Alice');
        expect(caption).toContain('Bobby');
      });
    });

    it('still names a forfeiting player who has already left the table', async () => {
      const { store, wrapper } = mountNamed();
      store.applyEvent({ type: 'state-update', state: playingState({
        players: [
          { id: '0', handCount: 5, isCurrentPlayer: false, name: 'Alice' },
          { id: '2', handCount: 3, isCurrentPlayer: true, name: null },
        ],
      }) });
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', eventData: { loserSeat: 1 }, message: '' });
      await wrapper.vm.$nextTick();
      expect(wrapper.get('[data-test="forfeit-banner"]').text()).toBe('Bobby forfeited');
    });

    it('renders a markup-looking name as plain text', () => {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: playingState({
        players: [
          { id: '0', handCount: 5, isCurrentPlayer: false },
          { id: '1', handCount: 4, isCurrentPlayer: true, name: '<img src=x onerror=alert(1)>' },
        ],
      }) });
      const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });
      expect(wrapper.get('[data-test="seat-label"]').text()).toBe('<img src=x onerror=alert(1)>');
      expect(wrapper.find('img[src="x"]').exists()).toBe(false);
    });
  });

  describe('turn cue', () => {
    function mountPlaying(isMine: boolean) {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: playingState({
        players: [
          { id: '0', handCount: 1, isCurrentPlayer: isMine },
          { id: '1', handCount: 3, isCurrentPlayer: !isMine },
        ],
        availableActions: isMine ? ['DISCARD'] : [],
      }) });
      return mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });
    }

    it('says it is my turn, in words, only on my turn', () => {
      expect(mountPlaying(true).get('[data-test="turn-hint"]').text()).toBe('Your turn');
      expect(mountPlaying(false).find('[data-test="turn-hint"]').exists()).toBe(false);
    });

    it('pulses Discard only when it is my turn and a card is selected', async () => {
      const wrapper = mountPlaying(true);
      const discard = wrapper.get('[data-test="discard"]');
      expect(discard.classes()).not.toContain('discard--pulse');
      await wrapper.get('[data-test="hand-card-0"]').trigger('click');
      expect(discard.classes()).toContain('discard--pulse');
    });

    it('does not pulse Discard when it is not my turn', async () => {
      const wrapper = mountPlaying(false);
      await wrapper.get('[data-test="hand-card-0"]').trigger('click');
      expect(wrapper.get('[data-test="discard"]').classes()).not.toContain('discard--pulse');
    });
  });

  describe('verdict announcement', () => {
    function mountPlaying() {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: playingState() });
      return { store, wrapper: mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } }) };
    }

    it('keeps a polite live region on the table before any reveal, so the first one is announced', () => {
      const live = mountPlaying().wrapper.get('[data-test="verdict-live"]');
      expect(live.attributes('aria-live')).toBe('polite');
      expect(live.text()).toBe('');
    });

    it('announces the verdict and who takes the pile, and hides the visual copy from screen readers', async () => {
      const { store, wrapper } = mountPlaying();
      store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', message: '',
        eventData: { callerSeat: 1, claimantSeat: 0, truthful: false, pickerSeat: 0, revealedCards: [{ rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' }] } });
      await wrapper.vm.$nextTick();
      expect(wrapper.get('[data-test="verdict-live"]').text())
        .toBe('BLUFF. Player 2 called bullshit on Player 1 — Player 1 takes the pile');
      expect(wrapper.get('[data-test="reveal"]').attributes('aria-hidden')).toBe('true');
    });
  });

  describe('my own connection', () => {
    afterEach(() => setConnectionOnline(true));

    function mountPlaying() {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: playingState({ availableActions: ['DISCARD', 'CALL_BULLSHIT'] }) });
      return mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });
    }

    it('shows nothing while connected', () => {
      expect(mountPlaying().find('[data-test="offline"]').exists()).toBe(false);
    });

    it('announces the lost connection and disables the actions until it is back', async () => {
      const wrapper = mountPlaying();
      await wrapper.get('[data-test="hand-card-0"]').trigger('click');
      expect((wrapper.get('[data-test="discard"]').element as HTMLButtonElement).disabled).toBe(false);

      setConnectionOnline(false);
      await wrapper.vm.$nextTick();
      const banner = wrapper.get('[data-test="offline"]');
      expect(banner.attributes('role')).toBe('alert');
      expect(banner.text()).toBe('Connection lost. Reconnecting…');
      expect((wrapper.get('[data-test="discard"]').element as HTMLButtonElement).disabled).toBe(true);
      expect((wrapper.get('[data-test="call"]').element as HTMLButtonElement).disabled).toBe(true);

      setConnectionOnline(true);
      await wrapper.vm.$nextTick();
      expect(wrapper.find('[data-test="offline"]').exists()).toBe(false);
      expect((wrapper.get('[data-test="discard"]').element as HTMLButtonElement).disabled).toBe(false);
    });

    it('re-fetches the state once the connection is back, since events may have been missed', async () => {
      const wrapper = mountPlaying();
      const hydrate = vi.spyOn(useBullshitStore(), 'hydrate').mockResolvedValue(undefined);
      setConnectionOnline(false);
      await wrapper.vm.$nextTick();
      expect(hydrate).not.toHaveBeenCalled();
      setConnectionOnline(true);
      await wrapper.vm.$nextTick();
      expect(hydrate).toHaveBeenCalledTimes(1);
    });
  });

  describe('end of game', () => {
    function mountFinished(opts: { winner: string; hand?: number; forfeit?: { loserSeat: number; reason?: string } }) {
      const store = useBullshitStore();
      store.applyEvent({ type: 'seat-change', seat: 0 });
      store.applyEvent({ type: 'state-update', state: playingState({
        myHand: opts.hand === 0 ? [] : playingState().myHand,
        players: opts.winner === '0'
          ? [{ id: '0', handCount: opts.hand ?? 1, isCurrentPlayer: false }]
          : [{ id: '1', handCount: 2, isCurrentPlayer: false }],
        outcome: { status: 'FINISHED', winnerId: opts.winner },
      }) });
      if (opts.forfeit) {
        store.applyEvent({ type: 'event', eventType: 'FORFEIT', eventData: opts.forfeit, message: '' });
      }
      return { store, wrapper: mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue], stubs: { RouterLink: true } } }) };
    }
    const sub = (m: ReturnType<typeof mountFinished>) => m.wrapper.get('.end-sub').text();

    it('says the opponent resigned when the win comes from a resignation', () => {
      expect(sub(mountFinished({ winner: '0', forfeit: { loserSeat: 1, reason: 'RESIGNED' } }))).toBe('Player 2 resigned.');
    });

    it('says the opponent disconnected when the win comes from a timeout', () => {
      expect(sub(mountFinished({ winner: '0', forfeit: { loserSeat: 1, reason: 'DISCONNECTED' } }))).toBe('Player 2 disconnected.');
    });

    it('falls back to a neutral line when I won with cards left but the reason is unknown (after a reload)', () => {
      expect(sub(mountFinished({ winner: '0', hand: 1 }))).toBe('The other players left the game.');
    });

    it('keeps the normal line when I emptied my hand', () => {
      expect(sub(mountFinished({ winner: '0', hand: 0 }))).toBe('You emptied your hand first.');
    });

    it('keeps the normal line for a loss', () => {
      expect(sub(mountFinished({ winner: '1' }))).toBe('Another player emptied their hand first.');
    });

    it('shows the rematch as pending, then as refused with a retry', async () => {
      const { store, wrapper } = mountFinished({ winner: '0', hand: 0 });
      const button = () => wrapper.get('[data-cy="play-again"]');
      expect(button().text()).toBe('Play again');
      expect(wrapper.find('[data-test="rematch-error"]').exists()).toBe(false);

      store.rematch = 'pending';
      await wrapper.vm.$nextTick();
      expect(button().text()).toBe('Joining the new table…');
      expect((button().element as HTMLButtonElement).disabled).toBe(true);

      store.rematch = 'failed';
      await wrapper.vm.$nextTick();
      expect(button().text()).toBe('Play again');
      expect((button().element as HTMLButtonElement).disabled).toBe(false);
      expect(wrapper.get('[data-test="rematch-error"]').attributes('role')).toBe('alert');
    });
  });

  it('shows a neutral loading status, and neither the table nor the actions, while connecting', () => {
    const store = useBullshitStore();
    expect(store.phase).toBe('connecting');
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });

    const status = wrapper.get('[data-test="connecting"]');
    expect(status.attributes('role')).toBe('status');
    expect(status.text()).toContain('Joining the table');
    for (const sel of ['.table-frame', '.my-zone', '[data-test="discard"]', '[data-test="call"]',
      '[data-test="claim-badge"]', '[data-test="lobby"]', '[data-test="end"]']) {
      expect(wrapper.find(sel).exists(), sel).toBe(false);
    }
  });

  it('leaves the loading status once the lobby state arrives', async () => {
    const store = useBullshitStore();
    const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });
    expect(wrapper.find('[data-test="connecting"]').exists()).toBe(true);

    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: lobbyView() });
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-test="connecting"]').exists()).toBe(false);
  });
});
