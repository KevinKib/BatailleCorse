import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import PrimeVue from 'primevue/config';
import DraughtsGameScreen from './DraughtsGameScreen.vue';
import { useDraughtsStore } from '../../state/Draughts.store';
import {
  draughtsAmbiguousState, draughtsCaptureState, draughtsFinishedState, draughtsOpeningState, draughtsState,
} from '../../model/draughts/fixtures';
import type { DraughtsReason, DraughtsState } from '../../model/draughts/DraughtsState';
import type { LobbyView } from '../../model/lobby/LobbyView';
import webSocketService from '../../service/WebSocketService';
import { setConnectionOnline } from '../../composables/useConnectionStatus';
import { messagesEn } from '../../locales/en';

const ui = messagesEn.draughtsUi;

const DESCRIPTORS = [
  { key: 'hostColour', type: 'CHOICE' as const, values: ['white', 'black', 'random'], defaultValue: 'random', labelKey: 'options.draughts.hostColour' },
  { key: 'drawOffers', type: 'CHOICE' as const, values: ['on', 'off'], defaultValue: 'on', labelKey: 'options.draughts.drawOffers' },
];

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'd1',
    players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: null, joined: false }],
    seatOptionDescriptors: [], removableBotSeats: [],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 2, canStart: false, lobbyVersion: 1,
    optionDescriptors: DESCRIPTORS, options: { hostColour: 'random', drawOffers: 'on' }, canEditOptions: true,
    ...overrides,
  };
}

// Every screen mounted by a test, unmounted after it: a screen left alive keeps reacting to the module-level
// connection status of the tests that follow.
const mounted: VueWrapper[] = [];

// Each table gets its own store: one that survived the previous table kept its forfeit and its bot levels.
async function mountScreen(view: DraughtsState | LobbyView | null, mySeat = 0) {
  setActivePinia(createPinia());
  const store = useDraughtsStore();
  store.applyEvent({ type: 'seat-change', seat: mySeat });
  if (view) store.applyEvent({ type: 'state-update', state: view });
  localStorage.setItem('draughts:tokens:d1', JSON.stringify({ [mySeat]: 'tok' }));
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', name: 'games', component: { render: () => null } }, { path: '/:rest(.*)*', component: { render: () => null } }],
  });
  await router.push('/draughts/room/d1');
  await router.isReady();
  const wrapper = mount(DraughtsGameScreen, { props: { gameId: 'd1' }, global: { plugins: [router, PrimeVue] }, attachTo: document.body });
  mounted.push(wrapper);
  await flushPromises();
  return { store, router, wrapper };
}

const asSeat = (state: DraughtsState): DraughtsState => state;
const publishSpy = () => vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});

describe('DraughtsGameScreen', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})));
    setActivePinia(createPinia());
    localStorage.clear();
    setConnectionOnline(true);
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    mounted.splice(0).forEach(w => w.unmount());
    document.body.innerHTML = '';
  });

  describe('before the game', () => {
    it('waits on a neutral screen until the first server state arrives', async () => {
      const { wrapper } = await mountScreen(null);
      expect(wrapper.get('[data-test="connecting"]').text()).toBe(ui.connecting);
      expect(wrapper.find('[data-test="draughts-board"]').exists()).toBe(false);
    });

    it('shows the shared lobby with the mini board, who plays White and the draw offers', async () => {
      const { wrapper } = await mountScreen(lobbyView());
      expect(wrapper.find('[data-test="lobby"]').exists()).toBe(true);
      expect(wrapper.text()).toContain('Draughts');
      expect(wrapper.find('[data-test="draughts-mini-board"]').exists()).toBe(true);
      expect(wrapper.get('[data-test="option-hostColour"]').text()).toContain('Host plays');
      expect(wrapper.get('[data-test="option-drawOffers"]').text()).toContain('Draw offers');
      expect(wrapper.find('[data-test="add-bot"]').exists()).toBe(true);
    });

    it('sets an option through the store', async () => {
      const publish = publishSpy();
      const { wrapper, store } = await mountScreen(lobbyView());
      store.restore('d1', 0, 'tok');
      await wrapper.get('[data-test="option-drawOffers-off"]').setValue(true);
      expect(publish).toHaveBeenCalledWith('/app/lobby/setOptions', expect.stringContaining('"drawOffers":"off"'));
    });

    it('lets the host set the level of a bot on its seat (Easy, Medium, Expert)', async () => {
      publishSpy();
      const { wrapper, store } = await mountScreen(lobbyView({
        players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: 'Bot 1', joined: true, bot: true, seatOptions: { level: 'greedy' } }],
        removableBotSeats: [1],
        seatOptionDescriptors: [
          { key: 'level', type: 'CHOICE', values: ['random', 'greedy', 'expert'], defaultValue: 'greedy', labelKey: 'options.draughts.level' },
        ],
      }));
      store.restore('d1', 0, 'tok');
      const level = wrapper.get('[data-test="seat-option-1-level"]');
      expect(level.findAll('option').map(o => o.text())).toEqual(['Easy', 'Medium', 'Expert']);
    });
  });

  describe('the table', () => {
    it('shows both plates (the opponent above, me below), the board and the chrome of the other tables', async () => {
      const { wrapper } = await mountScreen(draughtsOpeningState);
      const plates = wrapper.findAll('[data-test="plate"]');
      expect(plates.map(p => p.get('[data-test="plate-name"]').text())).toEqual(['Bob', ui.you]);
      expect(plates.map(p => p.get('[data-test="plate-colour"]').text())).toEqual(['Black', 'White']);
      expect(plates.map(p => p.get('[data-test="plate-counts"]').text())).toEqual(['20 men · took 0', '20 men · took 0']);
      expect(wrapper.findAll('[data-test^="square-"]')).toHaveLength(50);
      expect(wrapper.find('[data-cy="game-timer"]').exists()).toBe(true);
      expect(wrapper.find('[data-cy="rules-toggle"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="leave"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="resign"]').exists()).toBe(true);
    });

    it('turns the board for Black: square 50 at the top-left, my men at the bottom', async () => {
      const { wrapper } = await mountScreen(draughtsState({ currentSeat: 0 }), 1);
      const order = wrapper.findAll('[data-test^="square-"]').map(s => s.attributes('data-test'));
      expect(order[0]).toBe('square-50');
      expect(order[49]).toBe('square-1');
      expect(wrapper.findAll('[data-test="plate-name"]').map(n => n.text())).toEqual(['Alice', ui.you]);
      expect(wrapper.findAll('[data-test="plate-colour"]').map(n => n.text())).toEqual(['White', 'Black']);
    });

    it('shows the level of a computer opponent in its name', async () => {
      const game = draughtsState();
      const { wrapper } = await mountScreen({
        ...game,
        players: game.players.map(p => (p.seat === 1 ? { ...p, name: null, bot: true, level: 'expert' } : p)),
      });
      expect(wrapper.findAll('[data-test="plate-name"]')[0].text()).toBe('Bot 1 (Expert)');
    });

    it('names a player without a typed name by seat', async () => {
      const game = draughtsState();
      const { wrapper } = await mountScreen({ ...game, players: game.players.map(p => ({ ...p, name: p.seat === 1 ? '  ' : p.name })) });
      expect(wrapper.findAll('[data-test="plate-name"]')[0].text()).toBe('Player 2');
    });

    it('says what to do on my turn, and who it waits for otherwise', async () => {
      const { wrapper, store } = await mountScreen(draughtsOpeningState);
      expect(wrapper.get('[data-test="hint"]').text()).toContain(ui.table.hintMove);
      const theirTurn = draughtsState({ version: 1, currentSeat: 1, availableActions: [], legalMoves: [] });
      store.applyEvent({
        type: 'state-update',
        state: { ...theirTurn, players: theirTurn.players.map(p => ({ ...p, current: p.seat === 1 })) },
      });
      await flushPromises();
      expect(wrapper.get('[data-test="hint"]').text()).toContain('Waiting for Bob…');
    });

    it('says which colour I play until the first move', async () => {
      const { wrapper, store } = await mountScreen(draughtsOpeningState);
      expect(wrapper.get('[data-test="colour-hint"]').text()).toBe('You play White. White moves first.');
      store.applyEvent({ type: 'state-update', state: draughtsState({ version: 1, history: ['32-28'] }) });
      await flushPromises();
      expect(wrapper.find('[data-test="colour-hint"]').exists()).toBe(false);
    });
  });

  describe('a compulsory capture', () => {
    it('says how many pieces the capture takes, and lets only the capturing pieces move', async () => {
      const { wrapper } = await mountScreen(draughtsCaptureState);
      expect(wrapper.get('[data-test="capture-hint"]').text()).toBe('You must capture: 1 piece');
      const enabled = wrapper.findAll('[data-test^="square-"]').filter(s => s.attributes('aria-disabled') !== 'true');
      expect(enabled.map(s => s.attributes('data-test'))).toEqual(['square-33', 'square-34']);
    });

    it('counts several pieces', async () => {
      const { wrapper } = await mountScreen(draughtsAmbiguousState);
      expect(wrapper.get('[data-test="capture-hint"]').text()).toBe('You must capture: 2 pieces');
    });

    it('says nothing of capturing when none is compulsory', async () => {
      const { wrapper } = await mountScreen(draughtsOpeningState);
      expect(wrapper.find('[data-test="capture-hint"]').exists()).toBe(false);
    });

    it('selects a piece, shows where it goes and plays the move with the pieces it takes', async () => {
      const publish = publishSpy();
      const { wrapper, store } = await mountScreen(draughtsCaptureState);
      store.restore('d1', 0, 'tok');
      await wrapper.get('[data-test="square-33"]').trigger('click');
      expect(wrapper.get('[data-test="square-33"]').attributes('aria-pressed')).toBe('true');
      expect(wrapper.get('[data-test="square-22"]').attributes('data-target')).toBe('true');
      await wrapper.get('[data-test="square-22"]').trigger('click');
      expect(publish).toHaveBeenCalledWith('/app/draughts/move', expect.stringContaining('"from":33,"to":22,"captures":[28]'));
    });

    it('does not move when it is not my turn', async () => {
      const publish = publishSpy();
      const { wrapper, store } = await mountScreen(draughtsCaptureState, 1);
      store.restore('d1', 1, 'tok');
      await wrapper.get('[data-test="square-33"]').trigger('click');
      expect(publish).not.toHaveBeenCalled();
      expect(wrapper.get('[data-test="square-33"]').attributes('aria-pressed')).toBe('false');
    });
  });

  describe('an ambiguous destination', () => {
    async function openChooser() {
      const publish = publishSpy();
      const room = await mountScreen(draughtsAmbiguousState);
      room.store.restore('d1', 0, 'tok');
      await room.wrapper.get('[data-test="square-46"]').trigger('click');
      await room.wrapper.get('[data-test="square-19"]').trigger('click');
      return { ...room, publish };
    }

    it('offers the routes as a list and numbers the pieces on the board, without sending anything', async () => {
      const { wrapper, publish } = await openChooser();
      const chooser = wrapper.get('[data-test="chooser"]');
      expect(chooser.text()).toContain('Several captures end on square 19.');
      const options = chooser.findAll('[data-test^="choice-option-"]');
      expect(options.map(o => o.get('[data-test="choice-number"]').text())).toEqual(['1', '2']);
      expect(options.map(o => o.get('[data-test="choice-takes"]').text())).toEqual(['Takes 37, 28', 'Takes 32, 23']);
      // In the order of the squares: 23 and 32 belong to route 2, 28 and 37 to route 1.
      expect(wrapper.findAll('[data-test="route-badge"]').map(b => b.text())).toEqual(['2', '1', '2', '1']);
      expect(publish).not.toHaveBeenCalledWith('/app/draughts/move', expect.anything());
    });

    it('sends the captured set of the route that is chosen from the list', async () => {
      const { wrapper, publish } = await openChooser();
      await wrapper.get('[data-test="choice-option-1"]').trigger('click');
      expect(publish).toHaveBeenCalledWith('/app/draughts/move', expect.stringContaining('"from":46,"to":19,"captures":[32,23]'));
    });

    it('sends the route of a numbered piece that is tapped on the board', async () => {
      const { wrapper, publish } = await openChooser();
      await wrapper.get('[data-test="square-37"]').trigger('click');
      expect(publish).toHaveBeenCalledWith('/app/draughts/move', expect.stringContaining('"captures":[37,28]'));
    });

    it('closes the list on Cancel and sends nothing', async () => {
      const { wrapper, publish } = await openChooser();
      await wrapper.get('[data-test="choice-cancel"]').trigger('click');
      expect(wrapper.find('[data-test="chooser"]').exists()).toBe(false);
      expect(publish).not.toHaveBeenCalledWith('/app/draughts/move', expect.anything());
    });
  });

  describe('draws', () => {
    it('offers a draw, and says so once it is offered', async () => {
      const publish = publishSpy();
      const { wrapper, store } = await mountScreen(draughtsOpeningState);
      store.restore('d1', 0, 'tok');
      const offer = wrapper.get('[data-test="draw-offer"]');
      expect(offer.text()).toBe(ui.draw.offer);
      await offer.trigger('click');
      expect(publish).toHaveBeenCalledWith('/app/draughts/offerDraw', expect.anything());
      store.applyEvent({ type: 'state-update', state: draughtsState({ version: 1, drawOffer: { by: 0 }, availableActions: ['MOVE'] }) });
      await flushPromises();
      expect(wrapper.get('[data-test="draw-offer"]').text()).toBe(ui.draw.offered);
      expect(wrapper.get('[data-test="draw-offer"]').attributes('disabled')).toBeDefined();
    });

    it('has no offer button when the lobby switched draw offers off', async () => {
      const { wrapper } = await mountScreen(draughtsState({ drawOffersAllowed: false, availableActions: ['MOVE'] }));
      expect(wrapper.find('[data-test="draw-offer"]').exists()).toBe(false);
    });

    it('asks me to accept or decline the opponent’s offer', async () => {
      const publish = publishSpy();
      const { wrapper, store } = await mountScreen(draughtsState({
        drawOffer: { by: 1 }, availableActions: ['MOVE', 'ACCEPT_DRAW', 'DECLINE_DRAW'],
      }));
      store.restore('d1', 0, 'tok');
      const incoming = wrapper.get('[data-test="draw-incoming"]');
      expect(incoming.text()).toContain('Bob offers a draw');
      await wrapper.get('[data-test="draw-accept"]').trigger('click');
      await wrapper.get('[data-test="draw-decline"]').trigger('click');
      expect(publish).toHaveBeenCalledWith('/app/draughts/acceptDraw', expect.anything());
      expect(publish).toHaveBeenCalledWith('/app/draughts/declineDraw', expect.anything());
    });

    it('shows no answer bar for my own offer', async () => {
      const { wrapper } = await mountScreen(draughtsState({ drawOffer: { by: 0 }, availableActions: ['MOVE'] }));
      expect(wrapper.find('[data-test="draw-incoming"]').exists()).toBe(false);
    });
  });

  describe('resigning', () => {
    it('forfeits after the shared confirmation', async () => {
      const publish = publishSpy();
      vi.stubGlobal('confirm', vi.fn(() => true));
      const { wrapper, store } = await mountScreen(draughtsOpeningState);
      store.restore('d1', 0, 'tok');
      await wrapper.get('[data-test="resign"]').trigger('click');
      expect(window.confirm).toHaveBeenCalledWith(messagesEn.leaveGuard.confirm);
      expect(publish).toHaveBeenCalledWith('/app/forfeit', expect.anything());
    });

    it('does nothing when the confirmation is refused', async () => {
      const publish = publishSpy();
      vi.stubGlobal('confirm', vi.fn(() => false));
      const { wrapper, store } = await mountScreen(draughtsOpeningState);
      store.restore('d1', 0, 'tok');
      await wrapper.get('[data-test="resign"]').trigger('click');
      expect(publish).not.toHaveBeenCalledWith('/app/forfeit', expect.anything());
    });
  });

  describe('the counts', () => {
    it('shows the draw counters the server sends and the repetition warning', async () => {
      const { wrapper } = await mountScreen(draughtsState({
        drawCounters: { kingOnlyPlies: 41, kingOnlyLimit: 50, endgame: { case: 'SIXTEEN', plies: 15, limit: 32 } },
        repetitions: 2,
      }));
      expect(wrapper.get('[data-test="kings-only"]').text()).toBe('Kings only: 5 moves left');
      expect(wrapper.get('[data-test="short-ending"]').text()).toBe('Draw in 9 moves');
      expect(wrapper.get('[data-test="repetition-hint"]').text()).toBe(ui.table.repetition);
    });

    it('shows none of them in a plain game', async () => {
      const { wrapper } = await mountScreen(draughtsOpeningState);
      expect(wrapper.find('[data-test="kings-only"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="repetition-hint"]').exists()).toBe(false);
    });
  });

  describe('the move list', () => {
    it('lists the moves played and hides and shows again on request', async () => {
      const { wrapper } = await mountScreen(draughtsState({ history: ['32-28', '19-23', '28x19'] }));
      const toggle = wrapper.get('[data-test="moves-toggle"]');
      expect(wrapper.findAll('[data-test="moves-row"]')).toHaveLength(2);
      await wrapper.get('[data-test="moves-hide"]').trigger('click');
      expect(wrapper.find('[data-test="moves"]').exists()).toBe(false);
      expect(toggle.attributes('aria-expanded')).toBe('false');
      await toggle.trigger('click');
      expect(wrapper.find('[data-test="moves"]').exists()).toBe(true);
      expect(toggle.attributes('aria-expanded')).toBe('true');
    });
  });

  describe('announcements', () => {
    it('reads out a move with the notation and the pieces it took', async () => {
      const { wrapper, store } = await mountScreen(draughtsOpeningState);
      store.applyEvent({
        type: 'state-update',
        state: draughtsState({ version: 1, currentSeat: 1, history: ['33x22'], availableActions: [], legalMoves: [] }),
      });
      store.applyEvent({
        type: 'event', eventType: 'MOVE', message: '',
        eventData: { seat: 0, from: 33, to: 22, path: [22], captures: [28], promotes: false },
      });
      await flushPromises();
      expect(wrapper.get('[data-test="board-live"]').text()).toBe('You played 33x22, taking 1 piece.');
    });

    it('names the opponent for their move', async () => {
      const { wrapper, store } = await mountScreen(draughtsOpeningState);
      store.applyEvent({ type: 'state-update', state: draughtsState({ version: 1, history: ['32-28', '19-23'] }) });
      store.applyEvent({
        type: 'event', eventType: 'MOVE', message: '',
        eventData: { seat: 1, from: 19, to: 23, path: [23], captures: [], promotes: false },
      });
      await flushPromises();
      expect(wrapper.get('[data-test="board-live"]').text()).toBe('Bob played 19-23.');
    });

    it('reads out a draw offer and its refusal', async () => {
      const { wrapper, store } = await mountScreen(draughtsOpeningState);
      store.applyEvent({ type: 'event', eventType: 'DRAW_OFFERED', message: '', eventData: { seat: 1 } });
      await flushPromises();
      expect(wrapper.get('[data-test="board-live"]').text()).toBe('Bob offers a draw.');
      store.applyEvent({ type: 'event', eventType: 'DRAW_DECLINED', message: '', eventData: { seat: 1 } });
      await flushPromises();
      expect(wrapper.get('[data-test="board-live"]').text()).toBe('Bob declined the draw.');
    });
  });

  describe('the connection', () => {
    it('says so when the connection is lost, and leaves the board inert', async () => {
      const { wrapper } = await mountScreen(draughtsOpeningState);
      setConnectionOnline(false);
      await flushPromises();
      expect(wrapper.get('[data-test="offline"]').text()).toContain(ui.table.offline);
      expect(wrapper.get('[data-test="square-31"]').attributes('aria-disabled')).toBe('true');
    });
  });

  describe('the end of the game', () => {
    const over = (reason: DraughtsReason, winner: number | null): DraughtsState => ({
      ...draughtsFinishedState,
      outcome: { status: 'FINISHED', winner, reason },
    });
    const subtitle = async (state: DraughtsState, seat = 0, setup?: (s: ReturnType<typeof useDraughtsStore>) => void) => {
      const room = await mountScreen(asSeat(state), seat);
      setup?.(room.store);
      await flushPromises();
      return { wrapper: room.wrapper, text: room.wrapper.get('[data-test="end-sub"]').text() };
    };

    it('shows the end screen at once for a game found over, with the number of moves and the last one', async () => {
      const { wrapper } = await subtitle(over('NO_PIECES', 0));
      expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
      expect(wrapper.get('[data-test="end-winner"]').text()).toBe('Winner: Alice');
      expect(wrapper.get('[data-test="end-summary"]').text()).toBe('4 moves · last move 33x22');
    });

    it('says why, for each of the seven reasons', async () => {
      expect((await subtitle(over('NO_PIECES', 0))).text).toBe('Bob has no pieces left.');
      expect((await subtitle(over('NO_PIECES', 1))).text).toBe('You have no pieces left: Bob wins.');
      expect((await subtitle(over('BLOCKED', 0))).text).toBe('Bob cannot move.');
      expect((await subtitle(over('BLOCKED', 1))).text).toBe('You cannot move: Bob wins.');
      expect((await subtitle(over('FORFEIT', 1))).text).toBe(ui.end.youLostForfeit);
      expect((await subtitle(over('FORFEIT', 0))).text).toBe(ui.end.youWonForfeit);
      expect((await subtitle(over('DRAW_AGREED', null))).text).toBe(ui.draw.agreed);
      expect((await subtitle(over('DRAW_REPETITION', null))).text).toBe(ui.draw.byRepetition);
      expect((await subtitle(over('DRAW_KINGS_ONLY', null))).text).toBe(ui.draw.byKingsOnly);
      expect((await subtitle(over('DRAW_ENDGAME', null))).text).toBe(ui.draw.byShortEnding);
    });

    it('names who resigned or disconnected', async () => {
      const resigned = await subtitle(over('FORFEIT', 0), 0, (s) => {
        s.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '', eventData: { loserSeat: 1, reason: 'RESIGNED' } });
      });
      expect(resigned.text).toBe('Bob resigned.');
      const gone = await subtitle(over('FORFEIT', 0), 0, (s) => {
        s.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '', eventData: { loserSeat: 1, reason: 'DISCONNECTED' } });
      });
      expect(gone.text).toBe('Bob disconnected.');
    });

    it('starts every table from a clean store: nothing of the previous one (forfeit, bot level) shows up', async () => {
      const game = over('FORFEIT', 0);
      const withBot: DraughtsState = {
        ...game, players: game.players.map(p => (p.seat === 1 ? { ...p, name: null, bot: true, level: 'expert' } : p)),
      };
      const first = await subtitle(withBot, 0, (s) => {
        s.applyEvent({ type: 'event', eventType: 'FORFEIT', message: '', eventData: { loserSeat: 1, reason: 'DISCONNECTED' } });
      });
      expect(first.text).toBe('Bot 1 (Expert) disconnected.');
      const second = await subtitle(over('FORFEIT', 0));
      expect(second.text).toBe(ui.end.youWonForfeit);
      expect(second.wrapper.get('[data-test="end-winner"]').text()).toBe('Winner: Alice');
      expect(second.wrapper.findAll('[data-test="plate-name"]')[0].text()).toBe('Bob');
    });

    it('has no winner line for a draw, and calls it a draw', async () => {
      const { wrapper } = await subtitle(over('DRAW_AGREED', null));
      expect(wrapper.find('[data-test="end-winner"]').exists()).toBe(false);
      expect(wrapper.get('.end-title').text()).toBe(messagesEn.endScreen.draw);
    });

    it('asks for a new game from the end screen', async () => {
      const publish = publishSpy();
      const { wrapper, store } = await (async () => {
        const room = await mountScreen(over('NO_PIECES', 0));
        room.store.restore('d1', 0, 'tok');
        return room;
      })();
      const playAgain = vi.spyOn(store, 'playAgain').mockImplementation(() => {});
      await wrapper.get('[data-cy="play-again"]').trigger('click');
      expect(playAgain).toHaveBeenCalled();
      expect(publish).not.toHaveBeenCalledWith('/app/forfeit', expect.anything());
    });

    it('holds the table for the time of the last move before the end screen, when the game ends on a move', async () => {
      vi.useFakeTimers();
      const { wrapper, store } = await mountScreen(draughtsOpeningState);
      const ended = over('NO_PIECES', 0);
      store.applyEvent({ type: 'state-update', state: { ...ended, version: 90 } });
      store.applyEvent({
        type: 'event', eventType: 'GAME_OVER', message: '',
        eventData: { seat: 1, from: 33, to: 22, path: [22], captures: [28], promotes: false },
      });
      await flushPromises();
      expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
      await vi.advanceTimersByTimeAsync(5000);
      expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
    });

    it('does not hold the table for my own last move, which is not animated', async () => {
      vi.useFakeTimers();
      const { wrapper, store } = await mountScreen(draughtsOpeningState);
      const ended = over('NO_PIECES', 0);
      store.applyEvent({ type: 'state-update', state: { ...ended, version: 90 } });
      store.applyEvent({
        type: 'event', eventType: 'GAME_OVER', message: '',
        eventData: { seat: 0, from: 33, to: 22, path: [22], captures: [28], promotes: false },
      });
      await flushPromises();
      await vi.advanceTimersByTimeAsync(700);
      expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
    });
  });
});
