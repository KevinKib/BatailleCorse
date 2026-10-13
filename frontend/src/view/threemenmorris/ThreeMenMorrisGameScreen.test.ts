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

    it('shows the shared lobby for two players, without Add bot', async () => {
      const { wrapper } = await mountScreen(lobbyView());
      expect(wrapper.find('[data-test="lobby"]').exists()).toBe(true);
      expect(wrapper.text()).toContain("Three Men's Morris");
      expect(wrapper.text()).not.toContain('Add bot');
      expect(wrapper.find('[data-test="morris-board"]').exists()).toBe(false);
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
});
