import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { mount, shallowMount, flushPromises } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
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

vi.mock('../../composables/useCardSound', () => ({ cardSound: { play: vi.fn() } }));

import GameScreen from './BatailleCorseGameScreen.vue';
import DisconnectOverlay from '../../components/DisconnectOverlay.vue';
import SeatDisconnectBadge from '../../components/SeatDisconnectBadge.vue';
import GameTimer from '../../components/GameTimer.vue';
import RulesPanel from '../../components/RulesPanel.vue';
import { cardSound } from '../../composables/useCardSound';
import { setConnectionOnline } from '../../composables/useConnectionStatus';
import { SOUND_STORAGE_KEY } from '../../composables/useSoundPreference';
import { buildCard, buildPile } from '../../model/fixtures';
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

describe('BatailleCorseGameScreen', () => {
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

    it('shows the reconnecting badge on the opponent deck, hides it on reconnect, and no full-screen card', async () => {
      const wrapper = await mountGameScreen();
      const store = playing(0);
      disconnect(store);
      await nextTick();

      const badge = wrapper.getComponent(SeatDisconnectBadge);
      expect(badge.props('secondsRemaining')).toBeGreaterThan(0);
      expect(wrapper.get('[data-test="opponent-deck"]').classes()).toContain('opponent-deck--disconnected');
      expect(wrapper.get('[data-test="opponent-deck"]').findComponent(SeatDisconnectBadge).exists()).toBe(true);
      expect(wrapper.findComponent(DisconnectOverlay).exists()).toBe(false);

      store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_RECONNECTED', eventData: { reconnectedSeat: 1 } });
      await nextTick();
      expect(wrapper.findComponent(SeatDisconnectBadge).exists()).toBe(false);
      expect(wrapper.get('[data-test="opponent-deck"]').classes()).not.toContain('opponent-deck--disconnected');
    });

    it('hides the badge once the game is over, even if the disconnect lingers', async () => {
      const wrapper = await mountGameScreen();
      const store = playing(0);
      disconnect(store);
      await nextTick();
      expect(wrapper.findComponent(SeatDisconnectBadge).exists()).toBe(true);

      // The disconnect deadline expires server-side and a win is broadcast, with no
      // reconnect event: the disconnect entry lingers but the badge must hide.
      store.applyEvent({ type: 'state-update', state: buildGame({ winner: { id: '0' } }) });
      await nextTick();
      expect(wrapper.findComponent(SeatDisconnectBadge).exists()).toBe(false);
    });
  });

  describe('what the table shows (nothing may disappear)', () => {
    it('keeps every piece of information visible with the new top bar', async () => {
      const wrapper = mount(GameScreen, { global: { plugins: [PrimeVue], stubs: { RouterLink: true } } });
      mounted.push(wrapper);
      await flushPromises();
      const store = playing(0, buildGame({
        pile: buildPile({ cards: [buildCard(), buildCard()] }),
        players: [buildPlayer({ id: '0', nbCards: 20, availableActions: ['SEND'] }), buildPlayer({ id: '1', nbCards: 30 })],
      }));
      store.applyEvent({ type: 'seats-change', seats: [{ id: 0, name: 'Alice', joined: true }, { id: 1, name: 'Bob', joined: true }] });
      store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: 1, deadlineEpochMs: Date.now() + 41_000 } });
      await flushPromises();

      const tags = wrapper.findAll('h1.player_tag').map(t => t.text());
      expect(tags).toEqual(['Bob', 'Alice']);
      expect(wrapper.get('[data-cy="opponent-card-count"]').text()).toBe('30');
      expect(wrapper.get('[data-cy="player-card-count"]').text()).toBe('20');
      expect(wrapper.get('[data-cy="pile-card-count"]').text()).toBe('2');
      expect(wrapper.get('[data-cy="game-timer"]').text()).toMatch(/\d\d:\d\d/);
      const labels = wrapper.findAll('.action_button').map(b => b.text());
      expect(labels).toEqual([messagesEn.batailleCorseUi.send, messagesEn.batailleCorseUi.slap]);
      expect(wrapper.get('.back_button').exists()).toBe(true);
      expect(wrapper.find('[data-test="hotkeys-button"]').exists() || wrapper.findComponent(HotkeySettings).exists()).toBe(true);
      expect(wrapper.get('[data-test="seat-disconnect-badge"]').text()).toMatch(/^Reconnecting… \d+s$/);
      expect(wrapper.get('[data-test="sound-toggle"]').exists()).toBe(true);
    });
  });

  describe('top bar', () => {
    it('holds the timer (inline) then the sound toggle, with the rules panel on the table', async () => {
      const wrapper = await mountGameScreen();
      playing();
      await nextTick();
      const bar = wrapper.get('.top-bar');
      expect(bar.findComponent(GameTimer).props('inline')).toBe(true);
      expect(bar.element.children[0].tagName.toLowerCase()).toBe('game-timer-stub');
      expect(bar.element.children[1].getAttribute('data-test')).toBe('sound-toggle');
      expect(wrapper.findComponent(RulesPanel).exists()).toBe(true);
    });
  });

  describe('sound', () => {
    beforeEach(() => { vi.mocked(cardSound.play).mockClear(); });

    function pileOf(n: number) {
      return buildPile({ cards: Array.from({ length: n }, () => buildCard()) });
    }

    it('plays a card sound when a card lands on the pile, not when the pile is taken', async () => {
      await mountGameScreen();
      const store = playing(0, buildGame({ pile: pileOf(1) }));
      await nextTick();
      store.applyEvent({ type: 'state-update', state: buildGame({ pile: pileOf(2) }) });
      await nextTick();
      expect(cardSound.play).toHaveBeenCalledTimes(1);

      store.applyEvent({ type: 'state-update', state: buildGame({ pile: pileOf(0) }) });
      await nextTick();
      expect(cardSound.play).toHaveBeenCalledTimes(1);
    });

    it('stays silent once muted, with the toggle naming the action and the choice stored for every game', async () => {
      const wrapper = await mountGameScreen();
      const store = playing(0, buildGame({ pile: pileOf(1) }));
      await nextTick();
      const toggle = () => wrapper.get('[data-test="sound-toggle"]');
      expect(toggle().attributes('aria-label')).toBe(messagesEn.batailleCorseUi.sound.mute);

      await toggle().trigger('click');
      expect(toggle().attributes('aria-label')).toBe(messagesEn.batailleCorseUi.sound.unmute);
      expect(localStorage.getItem(SOUND_STORAGE_KEY)).toBe('off');

      store.applyEvent({ type: 'state-update', state: buildGame({ pile: pileOf(2) }) });
      await nextTick();
      expect(cardSound.play).not.toHaveBeenCalled();
    });
  });

  describe('my own connection', () => {
    afterEach(() => setConnectionOnline(true));

    it('shows nothing while connected', async () => {
      const wrapper = await mountGameScreen();
      playing();
      await nextTick();
      expect(wrapper.find('[data-test="offline"]').exists()).toBe(false);
    });

    it('announces the lost connection and disables Send and Slap until it is back', async () => {
      const wrapper = await mountGameScreen();
      playing(0, buildGame({ players: [buildPlayer({ id: '0', availableActions: ['SEND', 'SLAP'] }), buildPlayer({ id: '1' })] }));
      await nextTick();
      const disabled = () => wrapper.findAll('.action_button').map(b => b.attributes('disabled') === 'true');
      expect(disabled()).toEqual([false, false]);

      setConnectionOnline(false);
      await nextTick();
      const banner = wrapper.get('[data-test="offline"]');
      expect(banner.attributes('role')).toBe('alert');
      expect(banner.text()).toBe('Connection lost. Reconnecting…');
      expect(disabled()).toEqual([true, true]);

      setConnectionOnline(true);
      await nextTick();
      expect(wrapper.find('[data-test="offline"]').exists()).toBe(false);
      expect(disabled()).toEqual([false, false]);
    });

    it('re-fetches the state once the connection is back, since events may have been missed', async () => {
      await mountGameScreen();
      const store = playing(0);
      const hydrate = vi.spyOn(store, 'hydrate').mockResolvedValue(undefined as never);
      setConnectionOnline(false);
      await nextTick();
      expect(hydrate).not.toHaveBeenCalled();
      setConnectionOnline(true);
      await nextTick();
      expect(hydrate).toHaveBeenCalledTimes(1);
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
