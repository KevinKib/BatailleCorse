import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { shallowMount, flushPromises } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { nextTick } from 'vue';

// Captures the guard registered via onBeforeRouteLeave so we can invoke it directly.
const routeLeaveGuards: Array<(...args: unknown[]) => unknown> = [];
const router = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: 'g1' } }),
  useRouter: () => router,
  onBeforeRouteLeave: (cb: (...args: unknown[]) => unknown) => { routeLeaveGuards.push(cb); },
  RouterLink: { name: 'RouterLink', template: '<a><slot/></a>' },
}));

vi.mock('../../service/WebSocketService', () => ({
  default: {
    subscribeToGame: vi.fn(),
    unsubscribeFromGame: vi.fn(),
    subscribeToSeat: vi.fn(),
    unsubscribeFromSeat: vi.fn(),
    setPresence: vi.fn(),
    clearPresence: vi.fn(),
    publish: vi.fn(),
  },
}));

import GameScreen from './GameScreen.vue';
import DisconnectOverlay from '../../components/DisconnectOverlay.vue';
import HotkeySettings from '../../components/HotkeySettings.vue';
import GameLobby from '../../components/lobby/GameLobby.vue';
import EndGameOverlay from '../../components/EndGameOverlay.vue';
import { useBatailleCorseStore } from '../../state/BatailleCorse.store';
import { buildGame, buildPlayer } from '../../model/fixtures';
import type { LobbyView } from '../../model/lobby/LobbyView';
import type SessionSeat from '../../model/SessionSeat';
import { messagesEn } from '../../locales/en';

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: null, joined: true, bot: true, seatOptions: { tier: '3' } }],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 2, canStart: true, lobbyVersion: 1,
    ...overrides,
  };
}

/** The table of a running game, my seat being `seat`. */
function playing(seat = 0, state = buildGame()) {
  const store = useBatailleCorseStore();
  store.myPlayerIndex = seat;
  store.applyEvent({ type: 'state-update', state });
  return store;
}

// Unmounted after each test: a screen still mounted would go on retrying its load (fetch answers 502) into later tests.
const mounted: Array<{ unmount(): void }> = [];

async function mountGameScreen() {
  const wrapper = shallowMount(GameScreen, {
    global: { stubs: { RouterLink: true } },
  });
  mounted.push(wrapper);
  await flushPromises(); // let onMounted settle (fetch is stubbed to fail -> nothing loaded)
  return wrapper;
}

describe('GameScreen', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    routeLeaveGuards.length = 0;
    router.replace.mockReset();
    localStorage.setItem('tokens:g1', JSON.stringify({ 0: 'tok' }));
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 502 }));
  });

  afterEach(() => {
    mounted.splice(0).forEach(w => w.unmount());
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('givenBackLink_thenLeadsToTheGameSelection', async () => {
    const wrapper = await mountGameScreen();
    playing();
    await nextTick();
    expect(wrapper.get('.back_button').attributes('to')).toBe('/');
  });

  it('shows a neutral connecting screen until the lobby or the game is known, never the table', async () => {
    const wrapper = await mountGameScreen();
    expect(wrapper.get('[data-test="connecting"]').text()).toContain(messagesEn.batailleCorseUi.connecting);
    expect(wrapper.find('.action_buttons').exists()).toBe(false);
  });

  describe('lobby phase', () => {
    it('shows the shared lobby of Bataille Corse instead of the table', async () => {
      const wrapper = await mountGameScreen();
      useBatailleCorseStore().applyEvent({ type: 'lobby-update', lobby: lobbyView() });
      await nextTick();

      const lobby = wrapper.findComponent(GameLobby);
      expect(lobby.exists()).toBe(true);
      expect(lobby.props('game').key).toBe('bataillecorse');
      expect(lobby.props('lobby').gameId).toBe('g1');
      expect(wrapper.find('.action_buttons').exists()).toBe(false);
    });

    it('sends what the lobby asks for: bot, tier, Start', async () => {
      const wrapper = await mountGameScreen();
      const store = useBatailleCorseStore();
      store.applyEvent({ type: 'lobby-update', lobby: lobbyView() });
      await nextTick();
      const addBot = vi.spyOn(store, 'addBot').mockReturnValue(true);
      const setSeatOption = vi.spyOn(store, 'setSeatOption').mockReturnValue(true);
      const start = vi.spyOn(store, 'startGame').mockReturnValue(true);

      const lobby = wrapper.findComponent(GameLobby);
      lobby.vm.$emit('add-bot');
      lobby.vm.$emit('set-seat-option', 1, 'tier', '7');
      lobby.vm.$emit('start');

      expect(addBot).toHaveBeenCalled();
      expect(setSeatOption).toHaveBeenCalledWith(1, 'tier', '7');
      expect(start).toHaveBeenCalled();
    });

    it('Leave goes to the Game selection, replacing the room in the history', async () => {
      const wrapper = await mountGameScreen();
      const store = useBatailleCorseStore();
      store.applyEvent({ type: 'lobby-update', lobby: lobbyView() });
      await nextTick();
      vi.spyOn(store, 'leave').mockReturnValue(true);

      wrapper.findComponent(GameLobby).vm.$emit('leave');

      expect(router.replace).toHaveBeenCalledWith({ name: 'games' });
    });

    it('a leave that could not be sent stays in the lobby', async () => {
      const wrapper = await mountGameScreen();
      const store = useBatailleCorseStore();
      store.applyEvent({ type: 'lobby-update', lobby: lobbyView() });
      await nextTick();
      vi.spyOn(store, 'leave').mockReturnValue(false);

      wrapper.findComponent(GameLobby).vm.$emit('leave');

      expect(router.replace).not.toHaveBeenCalled();
    });
  });

  describe('opponent label', () => {
    const alice: SessionSeat = { id: 0, name: 'Alice', joined: true };
    it.each([
      [[alice, { id: 1, name: null, joined: true, bot: true, seatOptions: { tier: '3' } }], 0, 'Bot 1 (Gold)'],
      [[alice, { id: 1, name: 'Bob', joined: true, bot: false }], 0, 'Bob'],
      [[alice, { id: 1, name: null, joined: true, bot: false }], 0, 'Player 2'],
      [[alice, { id: 1, name: 'Bob', joined: true, bot: false }], 1, 'Alice'],
    ] as [SessionSeat[], number, string][])('names the opponent from the seats (%#)', async (seats, mySeat, label) => {
      const wrapper = await mountGameScreen();
      const store = playing(mySeat);
      store.applyEvent({ type: 'seats-change', seats });
      await nextTick();
      expect(wrapper.findAll('.player_tag')[0].text()).toBe(label);
    });

    it('shows my own name from my seat', async () => {
      const wrapper = await mountGameScreen();
      const store = playing(1);
      store.applyEvent({ type: 'seats-change', seats: [alice, { id: 1, name: 'Bob', joined: true }] });
      await nextTick();
      expect(wrapper.findAll('.player_tag')[1].text()).toBe('Bob');
    });
  });

  describe('end of the game', () => {
    it('Play again asks for the lobby; the button waits, then says when it failed', async () => {
      const wrapper = await mountGameScreen();
      const store = playing(0, buildGame({ winner: { id: '0' } }));
      const playAgain = vi.spyOn(store, 'playAgain').mockResolvedValue();
      await new Promise(r => setTimeout(r, 700));
      const overlay = wrapper.findComponent(EndGameOverlay);
      expect(overlay.props('rematchButton')).toEqual({ label: messagesEn.batailleCorseUi.playAgain, disabled: false });

      overlay.vm.$emit('playAgain');
      expect(playAgain).toHaveBeenCalled();

      store.rematch = 'pending';
      await nextTick();
      expect(wrapper.findComponent(EndGameOverlay).props('rematchButton')).toEqual(
        { label: messagesEn.batailleCorseUi.rematchPending, disabled: true });

      store.rematch = 'failed';
      await nextTick();
      expect(wrapper.findComponent(EndGameOverlay).props('error')).toBe(messagesEn.batailleCorseUi.rematchFailed);
    });
  });

  describe('Send button on my turn', () => {
    it('carries no extra turn ring: the turn is shown by the tag and the turn indicator', async () => {
      const wrapper = await mountGameScreen();
      const store = playing(0, buildGame({ players: [buildPlayer({ id: '0', availableActions: ['SEND'] }), buildPlayer({ id: '1' })] }));
      await nextTick();
      expect(store.state!.canSend(0)).toBe(true);
      expect(wrapper.find('.player_tag--active').exists()).toBe(true);
      const buttons = wrapper.findAll('.action_buttons > *');
      expect(buttons).toHaveLength(2);
      buttons.forEach((b) => expect(b.classes()).toEqual(['action_button']));
    });
  });

  describe('keyboard shortcuts gear', () => {
    it('offers the shortcut settings with the Send and Slap actions on the table', async () => {
      const wrapper = await mountGameScreen();
      playing();
      await nextTick();
      const gear = wrapper.findComponent(HotkeySettings);
      expect(gear.exists()).toBe(true);
      expect(gear.props('settings').actions.map((a: { id: string }) => a.id)).toEqual(['send', 'slap']);
      expect(gear.props('labels')).toMatchObject({ send: 'Send card', slap: 'Slap pile' });
    });
  });

  describe('opponent-disconnect countdown', () => {
    function disconnect(store: ReturnType<typeof useBatailleCorseStore>) {
      store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: Date.now() + 60_000 } });
    }

    it('shows the countdown banner while the opponent is disconnected, hides it on reconnect', async () => {
      const wrapper = await mountGameScreen();
      const store = playing(0);
      disconnect(store);
      await nextTick();

      const overlay = wrapper.findComponent(DisconnectOverlay);
      expect(overlay.exists()).toBe(true);
      expect(overlay.props('secondsRemaining')).toBeGreaterThan(0);

      store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_RECONNECTED', eventData: { reconnectedSeat: 1 } });
      await nextTick();
      expect(wrapper.findComponent(DisconnectOverlay).exists()).toBe(false);
    });

    it('hides the banner once the game is over, even if the disconnect lingers', async () => {
      const wrapper = await mountGameScreen();
      const store = playing(0);
      disconnect(store);
      await nextTick();
      expect(wrapper.findComponent(DisconnectOverlay).exists()).toBe(true);

      // The disconnect deadline expires server-side and a win is broadcast, with no
      // reconnect event — the disconnect entry lingers but the overlay must hide.
      store.applyEvent({ type: 'state-update', state: buildGame({ winner: { id: '0' } }) });
      await nextTick();
      expect(wrapper.findComponent(DisconnectOverlay).exists()).toBe(false);
    });
  });

  describe('leave confirmation guard', () => {
    it('forfeits and allows leaving when confirmed during a game', async () => {
      await mountGameScreen();
      const store = playing(0);
      const forfeitSpy = vi.spyOn(store, 'forfeit');
      window.confirm = vi.fn().mockReturnValue(true);

      const result = routeLeaveGuards[0]();

      expect(result).toBe(true);
      expect(forfeitSpy).toHaveBeenCalled();
    });

    it('blocks leaving and does not forfeit when cancelled', async () => {
      await mountGameScreen();
      const store = playing(0);
      const forfeitSpy = vi.spyOn(store, 'forfeit');
      window.confirm = vi.fn().mockReturnValue(false);

      const result = routeLeaveGuards[0]();

      expect(result).toBe(false);
      expect(forfeitSpy).not.toHaveBeenCalled();
    });

    it('leaves the lobby freely (nothing to forfeit)', async () => {
      await mountGameScreen();
      useBatailleCorseStore().applyEvent({ type: 'lobby-update', lobby: lobbyView() });
      window.confirm = vi.fn();

      expect(routeLeaveGuards[0]()).toBe(true);
      expect(window.confirm).not.toHaveBeenCalled();
    });
  });
});
