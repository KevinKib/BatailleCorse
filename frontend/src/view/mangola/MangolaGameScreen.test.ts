import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, RouterView } from 'vue-router';
import { defineComponent, h } from 'vue';
import PrimeVue from 'primevue/config';
import { createAppRouter } from '../../router';
import { useMangolaStore, ARRANGE_DEBOUNCE_MS, STUCK_START_KEY } from '../../state/Mangola.store';
import { mangolaState, placementState } from '../../model/mangola/mangolaFixtures';
import { boardOf, sow } from '../../model/mangola/sowingFixtures';
import type { MangolaState } from '../../model/mangola/MangolaState';
import type { LobbyView } from '../../model/lobby/LobbyView';
import { END_SCREEN_DELAY_MS } from '../../composables/useEndScreen';
import webSocketService from '../../service/WebSocketService';
import { setConnectionOnline } from '../../composables/useConnectionStatus';
import { messagesEn } from '../../locales/en';

const ui = messagesEn.mangolaUi;

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: 'Bob', joined: true }],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 2, canStart: true, lobbyVersion: 1,
    ...overrides,
  };
}

async function mountScreen(view: MangolaState | LobbyView | null, mySeat = 0) {
  const store = useMangolaStore();
  store.applyEvent({ type: 'seat-change', seat: mySeat });
  if (view) store.applyEvent({ type: 'state-update', state: view });
  localStorage.setItem('mangola:tokens:g1', JSON.stringify({ [mySeat]: 'tok' }));
  const router = createAppRouter(createMemoryHistory());
  await router.push('/games/mangola/room/g1');
  await router.isReady();
  // Rendered through RouterView so the route-leave guard is registered like in the app.
  const Root = defineComponent({ render: () => h(RouterView) });
  const wrapper = mount(Root, { global: { plugins: [router, PrimeVue] }, attachTo: document.body });
  await flushPromises();
  return { store, router, wrapper };
}

const pit = (w: VueWrapper, name: string) => w.get(`[data-test="pit-${name}"]`);
const subtitle = (w: VueWrapper) => w.get('.end-sub').text();

// E7 of the spec: the guest (seat 1, side B) sows h4, captures g2 and g1, and relays; a1 keeps my side alive.
const E7_BEFORE = 'h4:2 g3:1 g2:1 g1:1 a1:2';

/** Seat 1 to move on E7, as I (seat 0) see it. */
const beforeOpponentMove = () => mangolaState({
  version: 1, currentSeat: 1, board: boardOf(E7_BEFORE), legalPits: [],
  players: [
    { seat: 0, name: 'Alice', current: false, side: 'A', seeds: 4, ready: true },
    { seat: 1, name: 'Bob', current: true, side: 'B', seeds: 4, ready: true },
  ],
});
/** The same game one move later: the guest sowed h4. */
function afterOpponentMove(overrides: Partial<MangolaState> = {}): MangolaState {
  const before = beforeOpponentMove();
  const sown = sow(before.board as number[], 'h4');
  return {
    ...before, version: 2, currentSeat: 0, board: sown.board, legalPits: [0],
    players: [
      { seat: 0, name: 'Alice', current: true, side: 'A', seeds: 2, ready: true },
      { seat: 1, name: 'Bob', current: false, side: 'B', seeds: 4, ready: true },
    ],
    lastMove: { seat: 1, pit: 31, trace: sown.trace },
    ...overrides,
  };
}

describe('MangolaGameScreen', () => {
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
      expect(wrapper.find('[data-test="mangola-board"]').exists()).toBe(false);
    });

    it('shows the shared lobby for two players, with Add bot and the host options', async () => {
      const { wrapper } = await mountScreen(lobbyView({
        players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: null, joined: false }],
        optionDescriptors: [
          { key: 'setup', type: 'CHOICE', values: ['free', 'checkerboard', 'random'], defaultValue: 'free', labelKey: 'options.mangola.setup' },
          { key: 'firstPlayer', type: 'CHOICE', values: ['host', 'guest', 'random'], defaultValue: 'random', labelKey: 'options.mangola.firstPlayer' },
        ],
        options: { setup: 'free', firstPlayer: 'random' },
        canEditOptions: true,
      }));
      expect(wrapper.find('[data-test="lobby"]').exists()).toBe(true);
      expect(wrapper.text()).toContain('Mangola');
      expect(wrapper.find('[data-test="add-bot"]').exists()).toBe(true);
      expect(wrapper.get('[data-test="option-setup"]').text()).toContain('Set-up');
      expect(wrapper.get('[data-test="option-setup"] .choice--on').text()).toBe('Free placement');
      expect(wrapper.get('[data-test="option-firstPlayer"] .choice--on').text()).toBe('Random');
      expect(wrapper.find('[data-test="mangola-board"]').exists()).toBe(false);
    });

    it('adds a bot through the store, and the host sets its level on the seat (Easy, Medium, Expert)', async () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const { wrapper, store } = await mountScreen(lobbyView({
        players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: 'Bot 1', joined: true, bot: true, seatOptions: { level: 'greedy' } }],
        removableBotSeats: [1],
        seatOptionDescriptors: [
          { key: 'level', type: 'CHOICE', values: ['random', 'greedy', 'expert'], defaultValue: 'greedy', labelKey: 'options.mangola.level' },
        ],
      }));
      store.restore('g1', 0, 'tok');
      const level = wrapper.get('[data-test="seat-option-1-level"]');
      expect(level.findAll('option').map(o => o.text())).toEqual(['Easy', 'Medium', 'Expert']);
      await level.setValue('expert');
      expect(publish).toHaveBeenCalledWith('/app/lobby/setSeatOption', expect.stringContaining('"value":"expert"'));
    });

    it('sends Add bot to the server', async () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const { wrapper, store } = await mountScreen(lobbyView({ players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: null, joined: false }] }));
      store.restore('g1', 0, 'tok');
      await wrapper.get('[data-test="add-bot"]').trigger('click');
      expect(publish).toHaveBeenCalledWith('/app/lobby/addBot', expect.stringContaining('"gameId":"g1"'));
    });

    it('the host changes an option and starts the game through the store', async () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const { wrapper, store } = await mountScreen(lobbyView({
        optionDescriptors: [
          { key: 'setup', type: 'CHOICE', values: ['free', 'checkerboard', 'random'], defaultValue: 'free', labelKey: 'options.mangola.setup' },
        ],
        options: { setup: 'free' },
        canEditOptions: true,
      }));
      store.restore('g1', 0, 'tok');
      await wrapper.get('[data-test="option-setup-checkerboard"]').setValue(true);
      expect(publish).toHaveBeenCalledWith('/app/lobby/setOptions', expect.stringContaining('"checkerboard"'));
      const start = vi.spyOn(store, 'startGame').mockImplementation(() => {});
      await wrapper.get('[data-test="start"]').trigger('click');
      expect(start).toHaveBeenCalled();
    });
  });

  describe('placement', () => {
    it('shows the editor, not the board, with the opponent still placing', async () => {
      const { wrapper } = await mountScreen(placementState());
      expect(wrapper.find('[data-test="placement-editor"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="mangola-board"]').exists()).toBe(false);
      expect(wrapper.get('[data-test="opponent-status"]').text()).toBe('Bob: Placing…');
      expect(wrapper.get('[data-test="seeds-left"]').text()).toBe('32 seeds left');
      expect(wrapper.find('[data-cy="game-timer"]').exists()).toBe(true);
      expect(wrapper.find('[data-cy="rules-toggle"]').exists()).toBe(true);
    });

    it('a tap adds a seed through the store; a preset fills the layout and is sent once the delay has passed', async () => {
      vi.useFakeTimers();
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const { wrapper, store } = await mountScreen(placementState());
      store.restore('g1', 0, 'tok');
      await pit(wrapper, 'a2').trigger('click');
      expect(wrapper.get('[data-test="seeds-left"]').text()).toBe('31 seeds left');
      await wrapper.get('[data-test="preset-two-per-pit"]').trigger('click');
      expect(wrapper.get('[data-test="seeds-left"]').text()).toBe('All seeds placed');
      await vi.advanceTimersByTimeAsync(ARRANGE_DEBOUNCE_MS + 10);
      expect(publish).toHaveBeenCalledWith('/app/mangola/arrange', JSON.stringify({ gameId: 'g1', token: 'tok', counts: Array(16).fill(2) }));
    });

    it('Confirm layout sends the confirmation and the editor then offers to edit it back', async () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const { wrapper, store } = await mountScreen(placementState(Array(16).fill(2)));
      store.restore('g1', 0, 'tok');
      await wrapper.get('[data-test="confirm"]').trigger('click');
      expect(publish).toHaveBeenCalledWith('/app/mangola/confirmLayout', JSON.stringify({ gameId: 'g1', token: 'tok' }));
      store.applyEvent({ type: 'state-update', state: placementState(Array(16).fill(2), {
        version: 1,
        players: [
          { seat: 0, name: 'Alice', current: false, side: 'A', seeds: 32, ready: true },
          { seat: 1, name: 'Bob', current: false, side: 'B', seeds: null, ready: false },
        ],
      }) });
      await flushPromises();
      expect(wrapper.find('[data-test="edit-layout"]').exists()).toBe(true);
      await wrapper.get('[data-test="edit-layout"]').trigger('click');
      expect(publish).toHaveBeenCalledWith('/app/mangola/editLayout', JSON.stringify({ gameId: 'g1', token: 'tok' }));
    });

    it('says the opponent is ready', async () => {
      const { wrapper } = await mountScreen(placementState(Array(16).fill(0), {
        players: [
          { seat: 0, name: 'Alice', current: false, side: 'A', seeds: 0, ready: false },
          { seat: 1, name: 'Bob', current: false, side: 'B', seeds: null, ready: true },
        ],
      }));
      expect(wrapper.get('[data-test="opponent-status"]').text()).toBe('Bob: Ready');
    });

    it('a refused confirmation (stuck start) is worded in the editor, and goes away when the layout changes', async () => {
      const { wrapper, store } = await mountScreen(placementState(Array(16).fill(2)));
      store.applyEvent({ type: 'error', message: STUCK_START_KEY });
      await flushPromises();
      expect(wrapper.get('[data-test="placement-error"]').text()).toBe(ui.errors.stuckStart);
      await pit(wrapper, 'a2').trigger('click'); // all 32 are placed: nothing to add...
      await wrapper.get('[data-test="mode-remove"]').trigger('click');
      await pit(wrapper, 'a2').trigger('click');
      expect(wrapper.find('[data-test="placement-error"]').exists()).toBe(false);
    });

    it('warns when the draft has no finite move on an empty board', async () => {
      const { wrapper } = await mountScreen(placementState(Array(16).fill(2), { draftOnlyEndless: true }));
      expect(wrapper.find('[data-test="endless-warning"]').exists()).toBe(true);
    });
  });

  describe('the table', () => {
    it('shows both players, the board, the turn and the chrome of the other tables', async () => {
      const { wrapper } = await mountScreen(mangolaState());
      const tags = wrapper.findAll('[data-test="player-tag"]');
      expect(tags.map(t => t.get('[data-test="tag-name"]').text())).toEqual(['Bob', 'Alice']); // opponent above, me below
      expect(tags.map(t => t.get('[data-test="tag-seeds"]').text())).toEqual(['32 seeds', '32 seeds']);
      expect(wrapper.find('[data-test="mangola-board"]').exists()).toBe(true);
      expect(wrapper.findAll('[data-test="mangola-board"] button.mg-pit')).toHaveLength(32);
      expect(wrapper.find('[data-cy="game-timer"]').exists()).toBe(true);
      expect(wrapper.find('[data-cy="rules-toggle"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="leave"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="turn-hint"]').exists()).toBe(true);
      expect(wrapper.get('[data-test="phase-hint"]').text()).toBe(ui.table.hintSow);
      expect(wrapper.find('[data-test="placement-editor"]').exists()).toBe(false);
    });

    it('a click on a legal pit publishes the sowing; a click on any other pit does nothing', async () => {
      const publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
      const { wrapper, store } = await mountScreen(mangolaState());
      store.restore('g1', 0, 'tok');
      await pit(wrapper, 'c1').trigger('click'); // not legal
      expect(publish).not.toHaveBeenCalled();
      await pit(wrapper, 'a2').trigger('click');
      expect(publish).toHaveBeenCalledWith('/app/mangola/sow', JSON.stringify({ gameId: 'g1', token: 'tok', pit: 8 }));
    });

    it('waits for the opponent: no turn cue, the hint names them, the pits are inert', async () => {
      const { wrapper } = await mountScreen(beforeOpponentMove());
      expect(wrapper.find('[data-test="turn-hint"]').exists()).toBe(false);
      expect(wrapper.get('[data-test="phase-hint"]').text()).toBe('Waiting for Bob');
      expect(wrapper.findAll('[data-selectable="true"]')).toHaveLength(0);
    });

    it('says when the position has appeared twice', async () => {
      const { wrapper } = await mountScreen(mangolaState({ repetitions: 2 }));
      expect(wrapper.get('[data-test="repetition-hint"]').text()).toBe(ui.table.repetition);
    });

    it('goes inert and says so when the connection is lost', async () => {
      const { wrapper } = await mountScreen(mangolaState());
      setConnectionOnline(false);
      await flushPromises();
      expect(wrapper.get('[data-test="offline"]').text()).toContain(ui.table.offline);
      expect(wrapper.findAll('[data-selectable="true"]')).toHaveLength(0);
      setConnectionOnline(true);
      await flushPromises();
      expect(wrapper.find('[data-test="offline"]').exists()).toBe(false);
    });

    it('says when the opponent is disconnected, with the countdown', async () => {
      const { wrapper, store } = await mountScreen(mangolaState());
      store.applyEvent({ type: 'event', eventType: 'OPPONENT_DISCONNECTED', message: '',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: Date.now() + 30_000 } });
      await flushPromises();
      expect(wrapper.get('[data-cy="disconnect-overlay"]').text()).toContain('Bob disconnected');
    });

    it('names a player who forfeited in a banner', async () => {
      const { wrapper, store } = await mountScreen(mangolaState());
      store.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '', eventData: { loserSeat: 1, reason: 'RESIGNED' } });
      await flushPromises();
      expect(wrapper.get('[data-test="forfeit-banner"]').text()).toBe('Bob forfeited');
    });

    it('opens the rules of the game', async () => {
      const { wrapper } = await mountScreen(mangolaState());
      await wrapper.get('[data-cy="rules-toggle"]').trigger('click');
      expect(wrapper.get('[data-cy="rules-panel"]').text()).toContain(messagesEn.mangola.panelTitle);
    });
  });

  describe('the opponent move', () => {
    it('travels with the hand and inert pits, then a click on Skip lands on the final board with the marks of the move', async () => {
      vi.useFakeTimers();
      const { wrapper, store } = await mountScreen(beforeOpponentMove());
      store.applyEvent({ type: 'state-update', state: afterOpponentMove() });
      await flushPromises();
      expect(wrapper.find('[data-test="sowing-hand"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="skip-sowing"]').exists()).toBe(true);
      expect(wrapper.findAll('[data-selectable="true"]')).toHaveLength(0); // my turn, but the hand is still travelling
      expect(wrapper.find('[data-last-start="true"]').exists()).toBe(false); // the marks would give the sowing away

      await wrapper.get('[data-test="skip-sowing"]').trigger('click');
      await flushPromises();
      expect(wrapper.find('[data-test="sowing-hand"]').exists()).toBe(false);
      // The board shows the server's counts: g2 and g1 were captured.
      expect(pit(wrapper, 'g2').attributes('aria-label')).toContain('captured');
      expect(pit(wrapper, 'g2').attributes('aria-label')).toContain('0 seeds');
      expect(pit(wrapper, 'h4').attributes('data-last-start')).toBe('true');
      expect(pit(wrapper, 'g2').attributes('data-captured')).toBe('true');
      expect(pit(wrapper, 'a1').attributes('data-selectable')).toBe('true');
      expect(wrapper.get('[data-test="board-live"]').text()).toBe('Bob sowed from h4: 1 relay, 2 seeds captured.');
    });

    it('plays the whole sowing by itself when nobody skips it', async () => {
      vi.useFakeTimers();
      const { wrapper, store } = await mountScreen(beforeOpponentMove());
      store.applyEvent({ type: 'state-update', state: afterOpponentMove() });
      await flushPromises();
      await vi.advanceTimersByTimeAsync(30_000);
      expect(wrapper.find('[data-test="sowing-hand"]').exists()).toBe(false);
      expect(pit(wrapper, 'a1').attributes('data-selectable')).toBe('true');
    });

    it('my own move leaves no mark of its own: only the opponent\'s moves are marked', async () => {
      vi.useFakeTimers();
      const before = mangolaState({ version: 1, board: boardOf('a2:2 c2:1 c3:2 c4:3 h1:2'), legalPits: [8], availableActions: ['SOW'] });
      const { wrapper, store } = await mountScreen(before);
      const sown = sow(before.board as number[], 'a2');
      store.applyEvent({ type: 'state-update', state: {
        ...before, version: 2, currentSeat: 1, board: sown.board, legalPits: [],
        lastMove: { seat: 0, pit: 8, trace: sown.trace },
      } });
      await flushPromises();
      await vi.advanceTimersByTimeAsync(30_000);
      expect(wrapper.find('[data-last-start="true"]').exists()).toBe(false);
      expect(wrapper.find('[data-captured="true"]').exists()).toBe(false);
      // ... but it is announced like any move.
      expect(wrapper.get('[data-test="board-live"]').text()).toContain('Alice sowed from a2');
    });
  });

  describe('the end', () => {
    const finishedState = (overrides: Partial<MangolaState> = {}) => afterOpponentMove({
      currentSeat: 1, legalPits: [], availableActions: [],
      outcome: { status: 'FINISHED', winner: 1, reason: 'NO_MOVE' },
      ...overrides,
    });

    it('holds the end screen until the last sowing has been shown', async () => {
      vi.useFakeTimers();
      const { wrapper, store } = await mountScreen(beforeOpponentMove());
      store.applyEvent({ type: 'state-update', state: finishedState() });
      await flushPromises();
      await vi.advanceTimersByTimeAsync(END_SCREEN_DELAY_MS + 50);
      expect(wrapper.find('[data-test="end"]').exists()).toBe(false); // the hand is still travelling
      await wrapper.get('[data-test="skip-sowing"]').trigger('click');
      await vi.advanceTimersByTimeAsync(END_SCREEN_DELAY_MS + 50);
      expect(wrapper.get('.end-title').text()).toBe('DEFEAT');
      expect(wrapper.get('[data-test="end-winner"]').text()).toBe('Winner: Bob');
      expect(subtitle(wrapper)).toBe(ui.end.noMoveLost);
    });

    it('a game found already over after a reload shows the end screen at once', async () => {
      const { wrapper } = await mountScreen(finishedState());
      expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
    });

    it('a win by the opponent having no move says who cannot move', async () => {
      const { wrapper } = await mountScreen(finishedState({ outcome: { status: 'FINISHED', winner: 0, reason: 'NO_MOVE' } }));
      expect(wrapper.get('.end-title').text()).toBe('VICTORY');
      expect(subtitle(wrapper)).toBe('Bob has no move left.');
    });

    it('a draw by repetition: a DRAW title without trophy or winner', async () => {
      const { wrapper } = await mountScreen(finishedState({ outcome: { status: 'FINISHED', winner: null, reason: 'REPETITION' } }));
      expect(wrapper.get('.end-title').text()).toBe('DRAW');
      expect(wrapper.find('[data-cy="victory-flourish"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="end-winner"]').exists()).toBe(false);
      expect(subtitle(wrapper)).toBe(ui.end.drawRepetition);
    });

    it('a draw by an endless sowing is a draw too', async () => {
      const { wrapper } = await mountScreen(finishedState({ outcome: { status: 'FINISHED', winner: null, reason: 'ENDLESS_SOWING' } }));
      expect(wrapper.get('.end-title').text()).toBe('DRAW');
      expect(subtitle(wrapper)).toBe(ui.end.drawEndless);
    });

    it('forfeit by leaving or by timeout says how the opponent left', async () => {
      vi.useFakeTimers();
      const forfeited = finishedState({ outcome: { status: 'FINISHED', winner: 0, reason: 'FORFEIT' } });
      const first = await mountScreen(beforeOpponentMove());
      first.store.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '', eventData: { loserSeat: 1, reason: 'RESIGNED' } });
      first.store.applyEvent({ type: 'state-update', state: { ...forfeited, lastMove: undefined } });
      await vi.advanceTimersByTimeAsync(END_SCREEN_DELAY_MS + 50);
      expect(subtitle(first.wrapper)).toBe('Bob resigned.');
      first.wrapper.unmount();

      setActivePinia(createPinia());
      const second = await mountScreen(beforeOpponentMove());
      second.store.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '', eventData: { loserSeat: 1, reason: 'DISCONNECTED' } });
      second.store.applyEvent({ type: 'state-update', state: { ...forfeited, lastMove: undefined } });
      await vi.advanceTimersByTimeAsync(END_SCREEN_DELAY_MS + 50);
      expect(subtitle(second.wrapper)).toBe('Bob disconnected.');
    });

    it('a forfeit I lost (I left, then came back to the finished game) says that I left', async () => {
      const { wrapper } = await mountScreen(finishedState({ lastMove: undefined, outcome: { status: 'FINISHED', winner: 1, reason: 'FORFEIT' } }));
      expect(subtitle(wrapper)).toBe(ui.end.youLostForfeit);
    });

    it('Play again goes through the store; a refused rematch says so', async () => {
      const { wrapper, store } = await mountScreen(finishedState());
      const playAgain = vi.spyOn(store, 'playAgain').mockImplementation(() => Promise.resolve());
      await wrapper.get('[data-cy="play-again"]').trigger('click');
      expect(playAgain).toHaveBeenCalled();
      store.rematch = 'failed';
      await flushPromises();
      expect(wrapper.get('[data-test="rematch-error"]').text()).toBe(ui.end.rematchFailed);
    });
  });

  describe('leaving', () => {
    it('Back during the game asks first, and forfeits when confirmed', async () => {
      const { wrapper, store, router } = await mountScreen(mangolaState());
      const forfeit = vi.spyOn(store, 'forfeit').mockImplementation(() => {});
      window.confirm = () => true;
      await wrapper.get('[data-test="leave"]').trigger('click');
      await flushPromises();
      expect(forfeit).toHaveBeenCalled();
      expect(router.currentRoute.value.fullPath).toBe('/games');
    });

    it('Back declined stays on the table', async () => {
      const { wrapper, store, router } = await mountScreen(mangolaState());
      const forfeit = vi.spyOn(store, 'forfeit').mockImplementation(() => {});
      window.confirm = () => false;
      await wrapper.get('[data-test="leave"]').trigger('click');
      await flushPromises();
      expect(forfeit).not.toHaveBeenCalled();
      expect(router.currentRoute.value.fullPath).toBe('/games/mangola/room/g1');
    });

    it('Leave lobby frees the seat and goes to the Game selection', async () => {
      const { wrapper, store, router } = await mountScreen(lobbyView());
      vi.spyOn(store, 'leave').mockReturnValue(true);
      await wrapper.get('[data-test="leave-lobby"]').trigger('click');
      await flushPromises();
      expect(router.currentRoute.value.fullPath).toBe('/games');
    });
  });
});
