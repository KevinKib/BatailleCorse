import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, RouterView } from 'vue-router';
import { defineComponent, h } from 'vue';
import PrimeVue from 'primevue/config';
import { createAppRouter } from '../../router';
import { useThreeMenMorrisStore } from '../../state/ThreeMenMorris.store';
import { morrisState } from '../../model/threemenmorris/morrisFixtures';
import type { MorrisState } from '../../model/threemenmorris/MorrisState';
import type { LobbyView } from '../../model/lobby/LobbyView';
import { WINNING_LINE_HOLD_MS } from '../../model/threemenmorris/board';
import { END_SCREEN_DELAY_MS } from '../../composables/useEndScreen';
import webSocketService from '../../service/WebSocketService';
import { setConnectionOnline } from '../../composables/useConnectionStatus';
import { messagesEn } from '../../locales/en';

const N = null;
const ui = messagesEn.threeMenMorrisUi;

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: 'Bob', joined: true }],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 2, canStart: true, lobbyVersion: 1,
    ...overrides,
  };
}

const movementState = (overrides: Partial<MorrisState> = {}) => morrisState({
  phase: 'MOVEMENT',
  board: [0, 0, N, 1, 1, 1, N, N, 0],
  players: [
    { seat: 0, name: 'Alice', current: true, piecesToPlace: 0 },
    { seat: 1, name: 'Bob', current: false, piecesToPlace: 0 },
  ],
  availableActions: ['MOVE'],
  legalMoves: [{ from: 1, to: 2 }, { from: 0, to: 6 }, { from: 8, to: 7 }],
  ...overrides,
});

const finished = (reason: 'LINE' | 'BLOCKED' | 'FORFEIT', winner: number, winningLine: number[] = []) => movementState({
  phase: 'FINISHED', availableActions: [], legalMoves: [], winningLine,
  outcome: { status: 'FINISHED', winner, reason },
  players: [
    { seat: 0, name: 'Alice', current: false, piecesToPlace: 0 },
    { seat: 1, name: 'Bob', current: false, piecesToPlace: 0 },
  ],
});

async function mountScreen(view: MorrisState | LobbyView | null, mySeat = 0) {
  const store = useThreeMenMorrisStore();
  store.applyEvent({ type: 'seat-change', seat: mySeat });
  if (view) store.applyEvent({ type: 'state-update', state: view });
  localStorage.setItem('three-men-morris:tokens:g1', JSON.stringify({ [mySeat]: 'tok' }));
  const router = createAppRouter(createMemoryHistory());
  await router.push('/games/three-men-morris/room/g1');
  await router.isReady();
  // Rendered through RouterView so the route-leave guard is registered like in the app.
  const Root = defineComponent({ render: () => h(RouterView) });
  const wrapper = mount(Root, { global: { plugins: [router, PrimeVue] }, attachTo: document.body });
  await flushPromises();
  return { store, router, wrapper };
}

const point = (w: VueWrapper, n: number) => w.get(`[data-test="point-${n}"]`);

describe('ThreeMenMorrisGameScreen', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})));
    setActivePinia(createPinia());
    localStorage.clear();
    setConnectionOnline(true);
  });
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); document.body.innerHTML = ''; });

  describe('before the game', () => {
    it('waits on a neutral screen until the first server state arrives', async () => {
      const { wrapper } = await mountScreen(null);
      expect(wrapper.get('[data-test="connecting"]').text()).toBe(ui.connecting);
      expect(wrapper.find('[data-test="morris-board"]').exists()).toBe(false);
    });

    it('shows the shared lobby for two players, with Add bot for the host', async () => {
      const { wrapper } = await mountScreen(lobbyView({ players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: null, joined: false }] }));
      expect(wrapper.find('[data-test="lobby"]').exists()).toBe(true);
      expect(wrapper.text()).toContain('Morris');
      expect(wrapper.find('[data-test="add-bot"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="morris-board"]').exists()).toBe(false);
    });

    it('adds a bot through the store, and the host sets its level on the seat (Easy, Medium, Expert)', async () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const { wrapper, store } = await mountScreen(lobbyView({
        players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: 'Bot 1', joined: true, bot: true, seatOptions: { level: 'greedy' } }],
        removableBotSeats: [1],
        seatOptionDescriptors: [
          { key: 'level', type: 'CHOICE', values: ['random', 'greedy', 'perfect'], defaultValue: 'greedy', labelKey: 'options.threeMenMorris.level' },
        ],
      }));
      store.restore('g1', 0, 'tok');
      const level = wrapper.get('[data-test="seat-option-1-level"]');
      expect(level.attributes('aria-label')).toBe('Level for Bot 1');
      expect(level.findAll('option').map(o => o.text())).toEqual(['Easy', 'Medium', 'Expert']);
      expect((level.element as HTMLSelectElement).value).toBe('greedy');
      await level.setValue('perfect');
      expect(publish).toHaveBeenCalledWith('/app/lobby/setSeatOption', expect.stringContaining('"value":"perfect"'));
    });

    it('sends Add bot to the server', async () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const { wrapper, store } = await mountScreen(lobbyView({ players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: null, joined: false }] }));
      store.restore('g1', 0, 'tok');
      await wrapper.get('[data-test="add-bot"]').trigger('click');
      expect(publish).toHaveBeenCalledWith('/app/lobby/addBot', expect.stringContaining('"gameId":"g1"'));
    });

    it('starts the game from the lobby through the store', async () => {
      const { wrapper, store } = await mountScreen(lobbyView());
      const start = vi.spyOn(store, 'startGame').mockImplementation(() => {});
      await wrapper.get('[data-test="start"]').trigger('click');
      expect(start).toHaveBeenCalled();
    });
  });

  describe('the table', () => {
    it('shows both players with their colour, the board and the chrome of the other tables', async () => {
      const { wrapper } = await mountScreen(morrisState());
      const tags = wrapper.findAll('[data-test="player-tag"]');
      expect(tags).toHaveLength(2);
      expect(tags.map(t => t.get('[data-test="tag-name"]').text())).toEqual(['Bob', 'Alice']); // opponent above, me below
      expect(tags.map(t => t.get('[data-test="tag-colour"]').text())).toEqual(['Black', 'White']);
      expect(wrapper.find('[data-test="morris-board"]').exists()).toBe(true);
      expect(wrapper.findAll('[data-test^="point-"]')).toHaveLength(9);
      expect(wrapper.find('[data-cy="game-timer"]').exists()).toBe(true);
      expect(wrapper.find('[data-cy="rules-toggle"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="leave"]').exists()).toBe(true);
    });

    it('has neither a shortcut gear nor a sound toggle nor a bot', async () => {
      const { wrapper } = await mountScreen(morrisState());
      expect(wrapper.find('[data-test="hotkeys-button"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="sound-toggle"]').exists()).toBe(false);
    });

    it('seats the opponent at the top whichever seat I have', async () => {
      const { wrapper } = await mountScreen(morrisState(), 1);
      const names = wrapper.findAll('[data-test="player-tag"]').map(t => t.get('[data-test="tag-name"]').text());
      expect(names).toEqual(['Alice', 'Bob']);
      expect(wrapper.findAll('[data-test="tag-colour"]').map(t => t.text())).toEqual(['White', 'Black']);
    });

    it('names a player without a typed name by seat', async () => {
      const { wrapper } = await mountScreen(morrisState({
        players: [
          { seat: 0, name: null, current: true, piecesToPlace: 3 },
          { seat: 1, name: '  ', current: false, piecesToPlace: 3 },
        ],
      }));
      expect(wrapper.findAll('[data-test="tag-name"]').map(t => t.text())).toEqual(['Player 2', 'Player 1']);
    });

    it('shows the pieces each player still has to place', async () => {
      const { wrapper } = await mountScreen(morrisState({
        board: [0, N, N, N, 1, N, N, N, N],
        players: [
          { seat: 0, name: 'Alice', current: false, piecesToPlace: 2 },
          { seat: 1, name: 'Bob', current: true, piecesToPlace: 1 },
        ],
      }));
      expect(wrapper.findAll('[data-test="tag-reserve"]').map(r => r.attributes('aria-label')))
        .toEqual(['1 piece left to place', '2 pieces left to place']);
    });

    it('says which colour I play and who starts, until the first piece is down', async () => {
      const { wrapper, store } = await mountScreen(morrisState());
      expect(wrapper.get('[data-test="colour-hint"]').text()).toBe('You play White. White starts.');
      store.applyEvent({ type: 'state-update', state: morrisState({ board: [N, N, N, N, 0, N, N, N, N], currentSeat: 1 }) });
      await flushPromises();
      expect(wrapper.find('[data-test="colour-hint"]').exists()).toBe(false);
    });
  });

  describe('variants and colours', () => {
    const hostBlack = (overrides: Partial<MorrisState> = {}) => morrisState({
      whiteSeat: 1, currentSeat: 1,
      players: [
        { seat: 0, name: 'Alice', current: false, piecesToPlace: 3, colour: 'BLACK' },
        { seat: 1, name: 'Bob', current: true, piecesToPlace: 3, colour: 'WHITE' },
      ],
      board: [N, N, N, N, N, N, N, N, N],
      ...overrides,
    });

    it('names the variant in the top bar, Tapatan when the state carries none', async () => {
      const { wrapper } = await mountScreen(morrisState());
      expect(wrapper.get('[data-test="variant-name"]').text()).toBe('Tapatan');
      expect(wrapper.get('[data-test="morris-board"]').attributes('data-variant')).toBe('tapatan');
    });

    it('has no padlock, no closed-centre hint and no marks on a Tapatan table', async () => {
      const { wrapper } = await mountScreen(movementState());
      expect(wrapper.find('[data-test="centre-closed"]').exists()).toBe(false);
      expect(wrapper.find('[data-locked]').exists()).toBe(false);
      expect(wrapper.find('[data-test="lock"]').exists()).toBe(false);
      expect(wrapper.findAll('[data-test="piece-mark"]')).toHaveLength(0);
      expect(wrapper.findAll('[data-test="tag-mark"]')).toHaveLength(0);
      expect(wrapper.findAll('[data-test^="point-"]')).toHaveLength(9);
    });

    it('draws the colour of each player from the state when the host plays Black', async () => {
      const { wrapper } = await mountScreen(hostBlack(), 0);
      const tags = wrapper.findAll('[data-test="player-tag"]');
      // The opponent is on top, then me.
      expect(tags[0].attributes('data-colour')).toBe('white');
      expect(tags[1].attributes('data-colour')).toBe('black');
      expect(wrapper.get('[data-test="colour-hint"]').text()).toContain('You play Black');
      expect(wrapper.get('[data-test="phase-hint"]').text()).toContain('Waiting for Bob');
    });

    it('colours the pieces by the state: the guest, White, places next to a Black host piece', async () => {
      const { wrapper } = await mountScreen(hostBlack({ board: [N, N, N, N, 0, N, N, N, N] }), 1);
      expect(point(wrapper, 4).attributes('data-owner')).toBe('black');
      expect(wrapper.get('[data-test="phase-hint"]').text()).toBe('Place a piece');
    });

    it('rules of the variant: Achi says four pieces', async () => {
      const achi = await mountScreen(morrisState({ variant: 'achi' }));
      expect(achi.wrapper.get('[data-cy="rules-panel"]').text()).toContain('four pieces');
    });

    it('marks the points of the opponent last move on the board, not mine', async () => {
      const { store, wrapper } = await mountScreen(movementState());
      store.applyEvent({ type: 'event', eventType: 'MOVE', eventData: { seat: 1, action: 'MOVE', point: null, from: 5, to: 2 } });
      await flushPromises();
      expect(point(wrapper, 5).attributes('data-last')).toBe('true');
      expect(point(wrapper, 2).attributes('data-last')).toBe('true');
      store.applyEvent({ type: 'event', eventType: 'MOVE', eventData: { seat: 0, action: 'MOVE', point: null, from: 1, to: 2 } });
      await flushPromises();
      expect(point(wrapper, 5).attributes('data-last')).toBeUndefined();
    });

    it('lets the host pick the variant and the colour in the lobby, through the store', async () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const { wrapper } = await mountScreen(lobbyView({
        canEditOptions: true,
        options: { variant: 'tapatan', hostColour: 'white' },
        optionDescriptors: [
          { key: 'variant', type: 'CHOICE', values: ['tapatan', 'achi'], defaultValue: 'tapatan', labelKey: 'options.threeMenMorris.variant' },
          { key: 'hostColour', type: 'CHOICE', values: ['white', 'black', 'random'], defaultValue: 'random', labelKey: 'options.threeMenMorris.hostColour' },
        ],
      }));
      await wrapper.get('[data-test="option-variant-achi"]').setValue(true);
      expect(publish).toHaveBeenCalledWith('/app/lobby/setOptions', expect.stringContaining('"variant":"achi"'));
      await wrapper.get('[data-test="option-hostColour-black"]').setValue(true);
      expect(publish).toHaveBeenCalledWith('/app/lobby/setOptions', expect.stringContaining('"hostColour":"black"'));
    });

    it('shows the guest the same choices without controls', async () => {
      const { wrapper } = await mountScreen(lobbyView({
        mySeat: 1, canEditOptions: false,
        options: { variant: 'achi', hostColour: 'black' },
        optionDescriptors: [
          { key: 'variant', type: 'CHOICE', values: ['tapatan', 'achi'], defaultValue: 'tapatan', labelKey: 'options.threeMenMorris.variant' },
          { key: 'hostColour', type: 'CHOICE', values: ['white', 'black', 'random'], defaultValue: 'random', labelKey: 'options.threeMenMorris.hostColour' },
        ],
      }), 1);
      expect(wrapper.get('[data-test="option-variant"]').text()).toContain('Achi');
      expect(wrapper.get('[data-test="option-hostColour"]').text()).toContain('Black');
      expect(wrapper.find('[data-test="option-variant-achi"]').exists()).toBe(false);
    });
  });

  describe('whose turn it is', () => {
    it('on my turn shows "Your turn" and the placement hint, and the free points can be played', async () => {
      const { wrapper } = await mountScreen(morrisState());
      expect(wrapper.get('[data-test="turn-hint"]').text()).toContain('Your turn');
      expect(wrapper.get('[data-test="phase-hint"]').text()).toBe('Place a piece');
      expect(point(wrapper, 4).attributes('data-state')).toBe('highlighted');
      expect(wrapper.get('[data-test="player-tag"][data-colour="white"]').attributes('data-active')).toBe('true');
    });

    it('in the movement phase says to move a piece and highlights only my movable pieces', async () => {
      const { wrapper } = await mountScreen(movementState());
      expect(wrapper.get('[data-test="phase-hint"]').text()).toBe('Move a piece');
      expect([0, 1, 8].map(n => point(wrapper, n).attributes('data-state'))).toEqual(['highlighted', 'highlighted', 'highlighted']);
      expect(point(wrapper, 3).attributes('data-state')).toBe('idle');
      expect(point(wrapper, 2).attributes('data-state')).toBe('idle');
    });

    it('when it is not my turn waits for the opponent: no cue, nothing highlighted, board inert', async () => {
      const { wrapper } = await mountScreen(morrisState({
        currentSeat: 1, availableActions: [],
        players: [
          { seat: 0, name: 'Alice', current: false, piecesToPlace: 3 },
          { seat: 1, name: 'Bob', current: true, piecesToPlace: 3 },
        ],
      }));
      expect(wrapper.find('[data-test="turn-hint"]').exists()).toBe(false);
      expect(wrapper.get('[data-test="phase-hint"]').text()).toBe('Waiting for Bob…');
      expect(point(wrapper, 4).attributes('data-state')).toBe('idle');
      expect(point(wrapper, 4).attributes('aria-disabled')).toBe('true');
      expect(wrapper.get('[data-test="player-tag"][data-colour="black"]').attributes('data-active')).toBe('true');
      expect(wrapper.get('[data-test="player-tag"][data-colour="white"]').attributes('data-active')).toBe('false');
    });
  });

  describe('playing', () => {
    it('a click on a free point publishes the placement', async () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const { wrapper, store } = await mountScreen(morrisState());
      store.restore('g1', 0, 'tok');
      await point(wrapper, 4).trigger('click');
      expect(publish).toHaveBeenCalledWith('/app/three-men-morris/place', JSON.stringify({ gameId: 'g1', token: 'tok', point: 4 }));
    });

    it('selecting a piece highlights its destinations, a click on one publishes the move', async () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const { wrapper, store } = await mountScreen(movementState());
      store.restore('g1', 0, 'tok');
      await point(wrapper, 1).trigger('click');
      expect(point(wrapper, 1).attributes('data-state')).toBe('selected');
      expect(point(wrapper, 2).attributes('data-state')).toBe('highlighted');
      expect(publish).not.toHaveBeenCalled();
      await point(wrapper, 2).trigger('click');
      expect(publish).toHaveBeenCalledWith('/app/three-men-morris/move', JSON.stringify({ gameId: 'g1', token: 'tok', from: 1, to: 2 }));
    });

    it('Escape on the board drops the selection', async () => {
      const { wrapper } = await mountScreen(movementState());
      await point(wrapper, 1).trigger('click');
      await point(wrapper, 1).trigger('keydown', { key: 'Escape' });
      expect(point(wrapper, 1).attributes('data-state')).toBe('highlighted');
    });

    it('is inert while my connection is down, and says so', async () => {
      const { wrapper } = await mountScreen(morrisState());
      setConnectionOnline(false);
      await flushPromises();
      expect(wrapper.get('[data-test="offline"]').text()).toBe(ui.table.offline);
      expect(point(wrapper, 4).attributes('aria-disabled')).toBe('true');
      setConnectionOnline(true);
      await flushPromises();
      expect(wrapper.find('[data-test="offline"]').exists()).toBe(false);
    });

    it('announces the opponent move to screen readers', async () => {
      const { wrapper, store } = await mountScreen(morrisState());
      store.applyEvent({ type: 'event', eventType: 'PLACE', message: '',
        eventData: { seat: 1, action: 'PLACE', point: 4, from: null, to: null } });
      await flushPromises();
      expect(wrapper.get('[data-test="board-live"]').text()).toBe('Bob placed a piece on Centre.');
      store.applyEvent({ type: 'event', eventType: 'MOVE', message: '',
        eventData: { seat: 0, action: 'MOVE', point: null, from: 1, to: 2 } });
      await flushPromises();
      expect(wrapper.get('[data-test="board-live"]').text()).toBe('Alice moved from Top middle to Top right.');
    });

    it('says when the opponent is disconnected, with the countdown', async () => {
      const { wrapper, store } = await mountScreen(morrisState());
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED', message: '',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: Date.now() + 30_000 } });
      await flushPromises();
      expect(wrapper.get('[data-cy="disconnect-overlay"]').text()).toContain('Bob disconnected');
    });

    it('names a player who forfeited in a banner', async () => {
      const { wrapper, store } = await mountScreen(morrisState());
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '',
        eventData: { loserSeat: 1, reason: 'RESIGNED' } });
      await flushPromises();
      expect(wrapper.get('[data-test="forfeit-banner"]').text()).toBe('Bob forfeited');
    });

    it('opens the rules of the game, blocked rule included', async () => {
      const { wrapper } = await mountScreen(morrisState());
      await wrapper.get('[data-cy="rules-toggle"]').trigger('click');
      const panel = wrapper.get('[data-cy="rules-panel"]').text();
      expect(panel).toContain(messagesEn.threeMenMorris.panelTitle);
      expect(panel).toContain('cannot move');
    });
  });

  describe('leaving', () => {
    it('Back during the game asks first, and forfeits when confirmed', async () => {
      const { wrapper, store, router } = await mountScreen(morrisState());
      const forfeit = vi.spyOn(store, 'forfeit').mockImplementation(() => {});
      window.confirm = () => true;
      await wrapper.get('[data-test="leave"]').trigger('click');
      await flushPromises();
      expect(forfeit).toHaveBeenCalled();
      expect(router.currentRoute.value.fullPath).toBe('/games');
    });

    it('Back declined stays on the table', async () => {
      const { wrapper, store, router } = await mountScreen(morrisState());
      const forfeit = vi.spyOn(store, 'forfeit').mockImplementation(() => {});
      window.confirm = () => false;
      await wrapper.get('[data-test="leave"]').trigger('click');
      await flushPromises();
      expect(forfeit).not.toHaveBeenCalled();
      expect(router.currentRoute.value.fullPath).toBe('/games/three-men-morris/room/g1');
    });

    it('Back once the game is over does not ask, even before the end screen shows', async () => {
      vi.useFakeTimers();
      const { wrapper, store, router } = await mountScreen(movementState());
      store.applyEvent({ type: 'state-update', state: finished('LINE', 0, [0, 1, 2]) });
      await flushPromises();
      const confirm = vi.fn(() => true);
      window.confirm = confirm;
      await wrapper.get('[data-test="leave"]').trigger('click');
      await flushPromises();
      expect(confirm).not.toHaveBeenCalled();
      expect(router.currentRoute.value.fullPath).toBe('/games');
    });

    it('leaving the lobby frees the seat and goes to the Game selection', async () => {
      const { wrapper, store, router } = await mountScreen(lobbyView());
      vi.spyOn(store, 'leave').mockReturnValue(true);
      await wrapper.get('[data-test="leave-lobby"]').trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.name).toBe('games');
    });
  });

  describe('draws', () => {
    const draw = messagesEn.threeMenMorrisUi.draw;
    const incoming = { drawOffer: { seat: 1 }, drawActions: ['ACCEPT_DRAW', 'DECLINE_DRAW'] };

    it('placement phase: no draw control at all', async () => {
      const { wrapper } = await mountScreen(morrisState());
      expect(wrapper.find('[data-test="draw-offer"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="draw-incoming"]').exists()).toBe(false);
    });

    it('movement phase with drawActions absent: an Offer a draw button, no banner', async () => {
      const { wrapper, store } = await mountScreen(movementState());
      const button = wrapper.get('[data-test="draw-offer"]');
      expect(button.text()).toBe(draw.offer);
      expect(button.attributes('disabled')).toBeUndefined();
      expect(wrapper.find('[data-test="draw-incoming"]').exists()).toBe(false);
      const offer = vi.spyOn(store, 'offerDraw').mockImplementation(() => {});
      await button.trigger('click');
      expect(offer).toHaveBeenCalledTimes(1);
    });

    it('my offer is pending: the button reads Draw offered and is disabled', async () => {
      const { wrapper } = await mountScreen(movementState({ drawOffer: { seat: 0 }, drawActions: [] }));
      const button = wrapper.get('[data-test="draw-offer"]');
      expect(button.text()).toBe(draw.offered);
      expect(button.attributes('disabled')).toBeDefined();
    });

    it('already offered since my last move (drawActions empty, no offer): the button is disabled', async () => {
      const { wrapper } = await mountScreen(movementState({ drawActions: [] }));
      expect(wrapper.get('[data-test="draw-offer"]').attributes('disabled')).toBeDefined();
      expect(wrapper.get('[data-test="draw-offer"]').text()).toBe(draw.offer);
    });

    it('an incoming offer: an alertdialog names the offerer, with Accept and Decline', async () => {
      const { wrapper, store } = await mountScreen(movementState(incoming));
      const banner = wrapper.get('[data-test="draw-incoming"]');
      expect(banner.attributes('role')).toBe('alertdialog');
      expect(banner.attributes('aria-modal')).toBeUndefined();
      expect(banner.text()).toContain('Bob offers a draw');
      expect(banner.text()).toContain(draw.incomingHint);
      expect(wrapper.get('[data-test="draw-offer"]').attributes('disabled')).toBeDefined();
      const accept = vi.spyOn(store, 'acceptDraw').mockImplementation(() => {});
      const decline = vi.spyOn(store, 'declineDraw').mockImplementation(() => {});
      await wrapper.get('[data-test="draw-accept"]').trigger('click');
      await wrapper.get('[data-test="draw-decline"]').trigger('click');
      expect(accept).toHaveBeenCalledTimes(1);
      expect(decline).toHaveBeenCalledTimes(1);
    });

    it('the banner leaves when the next state carries no offer (a move declines it)', async () => {
      const { wrapper, store } = await mountScreen(movementState(incoming));
      store.applyEvent({ type: 'state-update', state: movementState({ drawOffer: null }) });
      await flushPromises();
      expect(wrapper.find('[data-test="draw-incoming"]').exists()).toBe(false);
      expect(wrapper.get('[data-test="draw-offer"]').attributes('disabled')).toBeUndefined();
    });

    it('the board stays playable while an offer is pending', async () => {
      const { wrapper } = await mountScreen(movementState(incoming));
      expect(point(wrapper, 1).attributes('disabled')).toBeUndefined();
    });

    it('the repetition hint shows only when the position occurred twice', async () => {
      const first = await mountScreen(movementState());
      expect(first.wrapper.find('[data-test="repetition-hint"]').exists()).toBe(false);
      first.wrapper.unmount();
      setActivePinia(createPinia());
      const second = await mountScreen(movementState({ repetitions: 2 }));
      expect(second.wrapper.get('[data-test="repetition-hint"]').text()).toBe(draw.repetition);
    });

    it('announces an offer and a refusal in the live region, by name', async () => {
      const { wrapper, store } = await mountScreen(movementState());
      const live = () => wrapper.get('[data-test="board-live"]').text();
      store.applyEvent({ type: 'event', eventType: 'DRAW_OFFERED', message: '', eventData: { seat: 1, action: 'OFFER_DRAW', point: null, from: null, to: null } });
      await flushPromises();
      expect(live()).toBe('Bob offers a draw.');
      store.applyEvent({ type: 'event', eventType: 'DRAW_DECLINED', message: '', eventData: { seat: 1, action: 'DECLINE_DRAW', point: null, from: null, to: null } });
      await flushPromises();
      expect(live()).toBe('Bob declined the draw.');
    });
  });

  describe('the end', () => {
    const subtitle = (w: VueWrapper) => w.get('.end-sub').text();
    const afterHold = (ms = WINNING_LINE_HOLD_MS + END_SCREEN_DELAY_MS + 50) => vi.advanceTimersByTimeAsync(ms);

    it('a line: draws the winning line at once and keeps it on screen before the overlay', async () => {
      vi.useFakeTimers();
      const { wrapper, store } = await mountScreen(movementState());
      store.applyEvent({ type: 'state-update', state: finished('LINE', 0, [3, 4, 5]) });
      await flushPromises();
      expect(wrapper.find('line[data-winning="true"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
      await vi.advanceTimersByTimeAsync(WINNING_LINE_HOLD_MS + END_SCREEN_DELAY_MS - 1);
      expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
      await vi.advanceTimersByTimeAsync(1);
      expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
    });

    it('I lined up three: victory, the winner and a line subtitle', async () => {
      vi.useFakeTimers();
      const { wrapper, store } = await mountScreen(movementState());
      store.applyEvent({ type: 'state-update', state: finished('LINE', 0, [0, 1, 2]) });
      await afterHold();
      expect(wrapper.get('.end-title').text()).toBe('VICTORY');
      expect(wrapper.get('[data-test="end-winner"]').text()).toBe('Winner: Alice');
      expect(subtitle(wrapper)).toBe('You lined up three.');
    });

    it('the opponent lined up three: defeat, and says who', async () => {
      vi.useFakeTimers();
      const { wrapper, store } = await mountScreen(movementState());
      store.applyEvent({ type: 'state-update', state: finished('LINE', 1, [3, 4, 5]) });
      await afterHold();
      expect(wrapper.get('.end-title').text()).toBe('DEFEAT');
      expect(wrapper.get('[data-test="end-winner"]').text()).toBe('Winner: Bob');
      expect(subtitle(wrapper)).toBe('Bob lined up three.');
    });

    it('blocked: the blocked player is named, without holding any line', async () => {
      vi.useFakeTimers();
      const { wrapper, store } = await mountScreen(movementState());
      store.applyEvent({ type: 'state-update', state: finished('BLOCKED', 0) });
      await flushPromises();
      expect(wrapper.find('line[data-winning="true"]').exists()).toBe(false);
      await afterHold(END_SCREEN_DELAY_MS + 50);
      expect(subtitle(wrapper)).toBe('Bob cannot move.');
    });

    it('blocked and I lose: says that I cannot move and who wins', async () => {
      vi.useFakeTimers();
      const { wrapper, store } = await mountScreen(movementState());
      store.applyEvent({ type: 'state-update', state: finished('BLOCKED', 1) });
      await afterHold(END_SCREEN_DELAY_MS + 50);
      expect(subtitle(wrapper)).toBe('You cannot move: Bob wins.');
    });

    it('forfeit by leaving or by timeout says how the opponent left', async () => {
      vi.useFakeTimers();
      const first = await mountScreen(movementState());
      first.store.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '', eventData: { loserSeat: 1, reason: 'RESIGNED' } });
      first.store.applyEvent({ type: 'state-update', state: finished('FORFEIT', 0) });
      await afterHold(END_SCREEN_DELAY_MS + 50);
      expect(subtitle(first.wrapper)).toBe('Bob resigned.');
      first.wrapper.unmount();

      setActivePinia(createPinia());
      const second = await mountScreen(movementState());
      second.store.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '', eventData: { loserSeat: 1, reason: 'DISCONNECTED' } });
      second.store.applyEvent({ type: 'state-update', state: finished('FORFEIT', 0) });
      await afterHold(END_SCREEN_DELAY_MS + 50);
      expect(subtitle(second.wrapper)).toBe('Bob disconnected.');
    });

    const drawn = (reason: 'DRAW_AGREED' | 'DRAW_REPETITION') => movementState({
      phase: 'FINISHED', availableActions: [], legalMoves: [], drawActions: [],
      outcome: { status: 'FINISHED', winner: null, reason },
    });

    it('a draw by agreement: a DRAW title without trophy or winner, and says it was agreed', async () => {
      const { wrapper } = await mountScreen(drawn('DRAW_AGREED'));
      expect(wrapper.get('.end-title').text()).toBe('DRAW');
      expect(wrapper.find('[data-cy="victory-flourish"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="end-winner"]').exists()).toBe(false);
      expect(subtitle(wrapper)).toBe('Draw agreed.');
    });

    it('a draw by repetition says the position occurred three times, and nothing is held for a line', async () => {
      vi.useFakeTimers();
      const { wrapper, store } = await mountScreen(movementState());
      store.applyEvent({ type: 'state-update', state: drawn('DRAW_REPETITION') });
      await vi.advanceTimersByTimeAsync(END_SCREEN_DELAY_MS + 50);
      expect(wrapper.get('.end-title').text()).toBe('DRAW');
      expect(subtitle(wrapper)).toBe('Draw by repetition: the same position occurred three times.');
    });

    it('a finished game shows no draw control', async () => {
      const { wrapper } = await mountScreen(drawn('DRAW_AGREED'));
      expect(wrapper.find('[data-test="draw-offer"]').exists()).toBe(false);
    });

    it('a forfeit I lost (I left, then came back to the finished game) says that I left', async () => {
      const { wrapper } = await mountScreen(finished('FORFEIT', 1));
      expect(wrapper.get('.end-title').text()).toBe('DEFEAT');
      expect(subtitle(wrapper)).toBe('You left the game.');
    });

    it('forfeit with the reason unknown (after a reload) still reads as a win', async () => {
      const { wrapper } = await mountScreen(finished('FORFEIT', 0));
      expect(subtitle(wrapper)).toBe('Your opponent left the game.');
    });

    it('a game found already over after a reload shows the end screen at once', async () => {
      const { wrapper } = await mountScreen(finished('LINE', 1, [3, 4, 5]));
      expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
    });

    it('Play again goes through the store; a refused rematch says so', async () => {
      const { wrapper, store } = await mountScreen(finished('LINE', 0, [0, 1, 2]));
      const playAgain = vi.spyOn(store, 'playAgain').mockImplementation(() => Promise.resolve());
      await wrapper.get('[data-cy="play-again"]').trigger('click');
      expect(playAgain).toHaveBeenCalled();
      store.rematch = 'failed';
      await flushPromises();
      expect(wrapper.get('[data-test="rematch-error"]').text()).toBe(ui.end.rematchFailed);
    });

    it('the board stays readable and inert while the winning line is held', async () => {
      vi.useFakeTimers();
      const { wrapper, store } = await mountScreen(movementState());
      store.applyEvent({ type: 'state-update', state: finished('LINE', 0, [0, 1, 2]) });
      await flushPromises();
      expect(point(wrapper, 0).attributes('aria-disabled')).toBe('true');
      expect(point(wrapper, 0).attributes('data-owner')).toBe('white');
      expect(wrapper.find('[data-test="turn-hint"]').exists()).toBe(false);
    });
  });

  // Six Men's Morris (design option A): counts in the tags, the removal step, the warnings, the end screens.
  describe("Six Men's Morris", () => {
    const six = (board: (number | null)[], overrides: Partial<MorrisState> = {}) => morrisState({
      variant: 'six',
      phase: 'MOVEMENT',
      board,
      players: [
        { seat: 0, name: 'Alice', current: true, piecesToPlace: 0, lost: 1 },
        { seat: 1, name: 'Bob', current: false, piecesToPlace: 0, lost: 1 },
      ],
      availableActions: ['MOVE'],
      legalMoves: [{ from: 0, to: 7 }],
      ...overrides,
    });
    //  White (Alice) on 0 1 2 6 7; Black (Bob) on 8 9 10 12 14: Black holds a mill on the inner top side.
    const BOARD = [0, 0, 0, N, N, N, 0, 0, 1, 1, 1, N, 1, N, 1, N];
    const removalMine = (overrides: Partial<MorrisState> = {}) => six(BOARD, {
      availableActions: ['REMOVE'], legalMoves: [],
      removal: { seat: 0, mill: [0, 1, 2] }, legalRemovals: [12, 14], ...overrides,
    });
    const removalTheirs = (overrides: Partial<MorrisState> = {}) => six(BOARD, {
      currentSeat: 1, availableActions: [], legalMoves: [],
      players: [
        { seat: 0, name: 'Alice', current: false, piecesToPlace: 0, lost: 1 },
        { seat: 1, name: 'Bob', current: true, piecesToPlace: 0, lost: 1 },
      ],
      removal: { seat: 1, mill: [8, 9, 10] }, ...overrides,
    });
    const tagOf = (w: VueWrapper, i: number) => w.findAll('[data-test="player-tag"]')[i];

    it('names the variant, draws 16 points with the marks on the pieces and on the tags', async () => {
      const { wrapper } = await mountScreen(six(BOARD));
      expect(wrapper.get('[data-test="variant-name"]').text()).toBe("Six Men's Morris");
      expect(wrapper.get('[data-test="morris-board"]').attributes('data-variant')).toBe('six');
      expect(wrapper.findAll('[data-test^="point-"]')).toHaveLength(16);
      expect(wrapper.findAll('[data-test="piece"]')).toHaveLength(10);
      expect(wrapper.findAll('[data-test="piece-mark"]').length).toBe(10);
      expect(wrapper.findAll('[data-test="tag-mark"]')).toHaveLength(2);
    });

    it('shows in each tag the pieces in hand, on the board and lost', async () => {
      const { wrapper } = await mountScreen(six(BOARD, {
        phase: 'PLACEMENT',
        players: [
          { seat: 0, name: 'Alice', current: true, piecesToPlace: 2, lost: 0 },
          { seat: 1, name: 'Bob', current: false, piecesToPlace: 1, lost: 3 },
        ],
      }));
      // The opponent is on top, then me.
      expect(tagOf(wrapper, 0).get('[data-test="tag-on-board"]').text()).toBe('5 on board');
      expect(tagOf(wrapper, 0).get('[data-test="tag-lost"]').text()).toBe('3 lost');
      expect(tagOf(wrapper, 0).findAll('[data-test="reserve-piece"]')).toHaveLength(1);
      expect(tagOf(wrapper, 1).get('[data-test="tag-on-board"]').text()).toBe('5 on board');
      expect(tagOf(wrapper, 1).get('[data-test="tag-lost"]').text()).toBe('0 lost');
      expect(tagOf(wrapper, 1).findAll('[data-test="reserve-piece"]')).toHaveLength(2);
      expect(tagOf(wrapper, 1).attributes('aria-label')).toBe('Alice, White: 5 on board, 2 in hand, 0 lost');
    });

    it('keeps Tapatan and Achi as they were: no counts, no banner, no warning', async () => {
      for (const variant of [undefined, 'achi'] as const) {
        setActivePinia(createPinia());
        document.body.innerHTML = '';
        const { wrapper } = await mountScreen(movementState({ variant }));
        expect(wrapper.find('[data-test="tag-on-board"]').exists()).toBe(false);
        expect(wrapper.find('[data-test="tag-lost"]').exists()).toBe(false);
        expect(wrapper.find('[data-test="removal-banner"]').exists()).toBe(false);
        expect(wrapper.find('[data-test="last-pieces"]').exists()).toBe(false);
        expect(wrapper.find('[data-test="quiet-warning"]').exists()).toBe(false);
        expect(tagOf(wrapper, 1).attributes('aria-label')).toBe('Alice, White');
      }
    });

    describe('my removal', () => {
      it('shows the banner under my tag: the instruction and how many pieces can be taken', async () => {
        const { wrapper } = await mountScreen(removalMine());
        const banner = wrapper.get('[data-test="removal-banner"]');
        expect(banner.attributes('role')).toBe('status');
        expect(banner.get('[data-test="removal-title"]').text()).toBe('Mill! Take one of Bob’s pieces.');
        expect(banner.get('[data-test="removal-can-take"]').text()).toBe('2 can be taken.');
        expect(banner.get('[data-test="removal-protected"]').text()).toBe('Pieces in a mill are safe while Bob has others.');
        // Under my tag, which is the second one, in the notes block (a fixed slot: nothing resizes the board).
        const kids = Array.from(wrapper.get('.table').element.children);
        expect(kids.indexOf(wrapper.get('.lower').element)).toBeGreaterThan(kids.indexOf(tagOf(wrapper, 1).element));
        expect(wrapper.get('[data-test="notes"]').element.contains(banner.element)).toBe(true);
      });

      it('rings the pieces that can be taken and shields those a mill protects', async () => {
        const { wrapper } = await mountScreen(removalMine({ legalRemovals: [12, 14] }));
        expect(wrapper.findAll('[data-test="removal-ring"]')).toHaveLength(2);
        expect(point(wrapper, 12).find('[data-test="removal-ring"]').exists()).toBe(true);
        expect(point(wrapper, 14).find('[data-test="removal-ring"]').exists()).toBe(true);
        expect(wrapper.findAll('[data-test="shield"]')).toHaveLength(3);
        for (const n of [8, 9, 10]) expect(point(wrapper, n).find('[data-test="shield"]').exists()).toBe(true);
        // Not the opponent's pieces: none of mine is ringed or shielded.
        expect(point(wrapper, 0).find('[data-test="shield"]').exists()).toBe(false);
        expect(point(wrapper, 0).find('[data-test="removal-ring"]').exists()).toBe(false);
      });

      it('lights the mill I just closed', async () => {
        const { wrapper } = await mountScreen(removalMine());
        expect(wrapper.findAll('line[data-mill="true"]').map((l) => l.attributes('data-line')).sort()).toEqual(['0-1', '1-2']);
        expect(point(wrapper, 1).attributes('data-mill')).toBe('true');
      });

      it('makes only the listed pieces playable: a protected piece does nothing', async () => {
        const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
        const { wrapper, store } = await mountScreen(removalMine());
        store.restore('g1', 0, 'tok');
        await point(wrapper, 9).trigger('click');
        await point(wrapper, 0).trigger('click');
        await point(wrapper, 5).trigger('click');
        expect(publish).not.toHaveBeenCalled();
        await point(wrapper, 12).trigger('click');
        expect(publish).toHaveBeenCalledWith('/app/three-men-morris/remove', JSON.stringify({ gameId: 'g1', token: 'tok', point: 12 }));
      });

      it('when every piece can be taken (all in mills) there is no shield and no protection sentence', async () => {
        const { wrapper } = await mountScreen(removalMine({ legalRemovals: [8, 9, 10, 12, 14] }));
        expect(wrapper.findAll('[data-test="removal-ring"]')).toHaveLength(5);
        expect(wrapper.findAll('[data-test="shield"]')).toHaveLength(0);
        expect(wrapper.find('[data-test="removal-protected"]').exists()).toBe(false);
        expect(wrapper.get('[data-test="removal-can-take"]').text()).toBe('5 can be taken.');
      });

      it('replaces the place / move hint, and the keyboard reaches the removable pieces', async () => {
        const { wrapper } = await mountScreen(removalMine());
        expect(wrapper.find('[data-test="phase-hint"]').exists()).toBe(false);
        expect(point(wrapper, 12).attributes('tabindex')).toBe('0');
        expect(point(wrapper, 12).attributes('aria-label')).toContain('can be taken');
        expect(point(wrapper, 9).attributes('aria-label')).toContain('protected in a mill');
      });
    });

    describe('the opponent removal', () => {
      it('says that Bob closed a mill and is taking one of my pieces; nothing is ringed or playable', async () => {
        const { wrapper } = await mountScreen(removalTheirs());
        const banner = wrapper.get('[data-test="removal-banner"]');
        expect(banner.get('[data-test="removal-title"]').text()).toBe('Bob closed a mill');
        expect(banner.get('[data-test="removal-sub"]').text()).toBe('and is taking one of your pieces.');
        expect(wrapper.findAll('[data-test="removal-ring"]')).toHaveLength(0);
        expect(wrapper.findAll('[data-test="shield"]')).toHaveLength(0);
        expect(point(wrapper, 0).attributes('aria-disabled')).toBe('true');
        expect(wrapper.find('[data-test="removal-can-take"]').exists()).toBe(false);
      });

      it('lights the mill Bob closed', async () => {
        const { wrapper } = await mountScreen(removalTheirs());
        expect(wrapper.findAll('line[data-mill="true"]')).toHaveLength(2);
        expect(point(wrapper, 9).attributes('data-mill')).toBe('true');
      });

      it('shows the banner in the same slot as mine, under my tag, and no waiting hint', async () => {
        const { wrapper } = await mountScreen(removalTheirs());
        const banner = wrapper.get('[data-test="removal-banner"]');
        const kids = Array.from(wrapper.get('.table').element.children);
        expect(kids.indexOf(wrapper.get('.lower').element)).toBeGreaterThan(kids.indexOf(tagOf(wrapper, 1).element));
        expect(wrapper.get('[data-test="notes"]').element.contains(banner.element)).toBe(true);
        expect(wrapper.find('[data-test="phase-hint"]').exists()).toBe(false);
        expect(wrapper.get('[data-test="removal-banner"]').attributes('role')).toBe('status');
      });
    });

    it('shows no banner without a pending removal', async () => {
      const { wrapper } = await mountScreen(six(BOARD));
      expect(wrapper.find('[data-test="removal-banner"]').exists()).toBe(false);
      expect(wrapper.find('[data-mill="true"]').exists()).toBe(false);
    });

    describe('warnings', () => {
      it('warns at three pieces left that one more loss ends the game, and flags the count', async () => {
        const { wrapper } = await mountScreen(six([0, 0, 0, N, N, N, N, N, 1, 1, 1, N, N, N, N, N], {
          players: [
            { seat: 0, name: 'Alice', current: true, piecesToPlace: 0, lost: 3 },
            { seat: 1, name: 'Bob', current: false, piecesToPlace: 0, lost: 3 },
          ],
        }));
        const warnings = wrapper.findAll('[data-test="last-pieces"]').map((w) => w.text()).sort();
        expect(warnings).toEqual(['One more loss and Alice loses.', 'One more loss and Bob loses.']);
        expect(tagOf(wrapper, 0).get('[data-test="tag-on-board"]').attributes('data-warn')).toBe('true');
      });

      it('counts the pieces in hand: three on the board and one to place is not the last stand', async () => {
        const { wrapper } = await mountScreen(six([0, 0, 0, N, N, N, N, N, 1, 1, 1, N, N, N, N, N], {
          phase: 'PLACEMENT',
          players: [
            { seat: 0, name: 'Alice', current: true, piecesToPlace: 1, lost: 0 },
            { seat: 1, name: 'Bob', current: false, piecesToPlace: 1, lost: 0 },
          ],
        }));
        expect(wrapper.findAll('[data-test="last-pieces"]')).toHaveLength(0);
      });

      it('has no warning with more than three pieces', async () => {
        const { wrapper } = await mountScreen(six(BOARD));
        expect(wrapper.findAll('[data-test="last-pieces"]')).toHaveLength(0);
      });

      it('counts the moves left without a capture, from the first warning', async () => {
        const { wrapper } = await mountScreen(six(BOARD, { quietPlies: 42, quietPlyLimit: 50 }));
        expect(wrapper.get('[data-test="quiet-warning"]').text()).toBe('8 moves without a capture before a draw.');
        expect(wrapper.get('[data-test="quiet-warning"]').attributes('role')).toBe('status');
      });

      it('says "1 move" in the singular', async () => {
        const { wrapper } = await mountScreen(six(BOARD, { quietPlies: 49, quietPlyLimit: 50 }));
        expect(wrapper.get('[data-test="quiet-warning"]').text()).toBe('1 move without a capture before a draw.');
      });

      it('keeps the warnings in the notes block and the draw button row in the page, so the board never resizes', async () => {
        const { wrapper } = await mountScreen(six(BOARD, { quietPlies: 42, quietPlyLimit: 50 }));
        const notes = wrapper.get('[data-test="notes"]').element;
        expect(notes.contains(wrapper.get('[data-test="quiet-warning"]').element)).toBe(true);
        expect(wrapper.get('.board-slot [data-test="morris-board"]').exists()).toBe(true);
        expect(wrapper.find('.table--crowded').exists()).toBe(false);
        // The draw button's row is there before the movement phase too (empty), like the notes block.
        const placing = await mountScreen(six(BOARD, { phase: 'PLACEMENT' }));
        expect(placing.wrapper.get('[data-test="draw-bar"]').exists()).toBe(true);
        expect(placing.wrapper.find('[data-test="draw-offer"]').exists()).toBe(false);
      });

      it('shows no quiet warning while the server sends no count', async () => {
        const { wrapper } = await mountScreen(six(BOARD));
        expect(wrapper.find('[data-test="quiet-warning"]').exists()).toBe(false);
      });

      it('sits next to the repetition hint without replacing it', async () => {
        const { wrapper } = await mountScreen(six(BOARD, { quietPlies: 44, quietPlyLimit: 50, repetitions: 2 }));
        expect(wrapper.find('[data-test="repetition-hint"]').exists()).toBe(true);
        expect(wrapper.find('[data-test="quiet-warning"]').exists()).toBe(true);
      });
    });

    describe('the live region and the capture mark', () => {
      it('announces a mill closed by a move, and the removal that follows', async () => {
        const { wrapper, store } = await mountScreen(six(BOARD));
        store.applyEvent({ type: 'event', eventType: 'MOVE', message: '',
          eventData: { seat: 1, action: 'MOVE', point: null, from: 11, to: 10, mill: [8, 9, 10] } });
        await flushPromises();
        expect(wrapper.get('[data-test="board-live"]').text()).toBe('Bob moved from Inner right to Inner top right. Bob closed a mill.');
        store.applyEvent({ type: 'event', eventType: 'REMOVE', message: '', eventData: { seat: 1, action: 'REMOVE', point: 6 } });
        await flushPromises();
        expect(wrapper.get('[data-test="board-live"]').text()).toBe('Bob took the piece on Outer bottom left.');
      });

      it('announces a mill closed by a placement', async () => {
        const { wrapper, store } = await mountScreen(six(BOARD));
        store.applyEvent({ type: 'event', eventType: 'PLACE', message: '',
          eventData: { seat: 1, action: 'PLACE', point: 10, from: null, to: null, mill: [8, 9, 10] } });
        await flushPromises();
        expect(wrapper.get('[data-test="board-live"]').text()).toBe('Bob placed a piece on Inner top right. Bob closed a mill.');
      });

      it('marks the point of the capture until the next move', async () => {
        const { wrapper, store } = await mountScreen(six(BOARD));
        store.applyEvent({ type: 'event', eventType: 'REMOVE', message: '', eventData: { seat: 1, action: 'REMOVE', point: 5 } });
        await flushPromises();
        expect(point(wrapper, 5).find('[data-test="captured-mark"]').exists()).toBe(true);
        store.applyEvent({ type: 'event', eventType: 'MOVE', message: '',
          eventData: { seat: 0, action: 'MOVE', point: null, from: 0, to: 7 } });
        await flushPromises();
        expect(wrapper.find('[data-test="captured-mark"]').exists()).toBe(false);
      });
    });

    describe('the end', () => {
      const afterHold = () => vi.advanceTimersByTimeAsync(WINNING_LINE_HOLD_MS + END_SCREEN_DELAY_MS + 50);
      const reduced = (winner: number) => six([0, 0, 0, N, N, N, N, N, 1, 1, N, N, N, N, N, N], {
        phase: 'FINISHED', availableActions: [], legalMoves: [],
        outcome: { status: 'FINISHED', winner, reason: 'REDUCED' },
        players: [
          { seat: 0, name: 'Alice', current: false, piecesToPlace: 0, lost: 4 },
          { seat: 1, name: 'Bob', current: false, piecesToPlace: 0, lost: 4 },
        ],
      });

      it('REDUCED, I win: Bob has only two pieces left', async () => {
        vi.useFakeTimers();
        const { wrapper, store } = await mountScreen(six(BOARD));
        store.applyEvent({ type: 'state-update', state: reduced(0) });
        await afterHold();
        expect(wrapper.get('.end-sub').text()).toBe('Bob has only two pieces left.');
        expect(wrapper.get('[data-test="end-winner"]').text()).toBe('Winner: Alice');
      });

      it('REDUCED, I lose: I have only two pieces left and Bob wins', async () => {
        vi.useFakeTimers();
        const { wrapper, store } = await mountScreen(six(BOARD));
        store.applyEvent({ type: 'state-update', state: reduced(1) });
        await afterHold();
        expect(wrapper.get('.end-sub').text()).toBe('You have only two pieces left: Bob wins.');
      });

      it('holds the table with the capture mark for a moment before the end screen', async () => {
        vi.useFakeTimers();
        const { wrapper, store } = await mountScreen(six(BOARD));
        store.applyEvent({ type: 'event', eventType: 'GAME_OVER', message: '', eventData: { seat: 0, action: 'REMOVE', point: 10 } });
        store.applyEvent({ type: 'state-update', state: reduced(0) });
        await flushPromises();
        expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
        expect(point(wrapper, 10).find('[data-test="captured-mark"]').exists()).toBe(true);
        await afterHold();
        expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
      });

      it('DRAW_MOVE_LIMIT: a draw with no winner, said as too many moves without a capture', async () => {
        vi.useFakeTimers();
        const { wrapper, store } = await mountScreen(six(BOARD));
        store.applyEvent({ type: 'state-update', state: six(BOARD, {
          phase: 'FINISHED', availableActions: [], legalMoves: [], drawActions: [],
          outcome: { status: 'FINISHED', winner: null, reason: 'DRAW_MOVE_LIMIT' },
        }) });
        await vi.advanceTimersByTimeAsync(END_SCREEN_DELAY_MS + 50);
        expect(wrapper.get('.end-title').text()).toBe('DRAW');
        expect(wrapper.get('.end-sub').text()).toBe('Draw: too many moves without a capture.');
      });

      it('shows no removal banner nor warning once the game is over', async () => {
        const { wrapper } = await mountScreen(reduced(0));
        expect(wrapper.find('[data-test="removal-banner"]').exists()).toBe(false);
        expect(wrapper.find('[data-test="last-pieces"]').exists()).toBe(false);
        expect(wrapper.find('[data-test="quiet-warning"]').exists()).toBe(false);
      });
    });

    it('opens the rules of Six: mills, no flying, the bots', async () => {
      const { wrapper } = await mountScreen(six(BOARD));
      const text = wrapper.get('[data-cy="rules-panel"]').text();
      expect(text).toContain('mill');
      expect(text).toContain('never jump');
      expect(text).toContain('three moves ahead');
    });

    it('keeps the draw controls of the movement phase', async () => {
      const { wrapper } = await mountScreen(six(BOARD));
      expect(wrapper.find('[data-test="draw-offer"]').exists()).toBe(true);
    });
  });
});
