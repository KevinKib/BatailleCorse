import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, createRouter } from 'vue-router';
import PrimeVue from 'primevue/config';
import KiloDeMerdeGameScreen from './KiloDeMerdeGameScreen.vue';
import { useKiloDeMerdeStore } from '../../state/KiloDeMerde.store';
import { useGameRooms } from '../../composables/useGameRooms';
import { findGame } from '../../model/games';
import webSocketService from '../../service/WebSocketService';
import { DEFAULT_KILO_DE_MERDE_OPTIONS, kiloDeMerdeFinishedState, kiloDeMerdeStepState } from '../../model/kilodemerde/fixtures';
import type { LobbyView } from '../../model/lobby/LobbyView';
import type { KiloDeMerdeState } from '../../model/kilodemerde/KiloDeMerdeState';

// The whole life of a room through the real registry entry, room client, store and screen, with the server played
// by a few canned answers: create, join, bots, start, a pass, the end, Play again (back to a lobby for a new game).

const DESCRIPTORS = [
  { key: 'rounds', type: 'CHOICE' as const, values: ['10', '5', '15', '20'], defaultValue: '10', labelKey: 'options.kiloDeMerde.rounds' },
];

function lobby(players: Array<{ name: string; bot?: boolean }>, mySeat = 0): LobbyView {
  return {
    started: false, gameId: 'k1',
    players: players.map((p, seat) => ({ seat, name: p.name, joined: true, ...(p.bot ? { bot: true, options: { level: 'medium' } } : {}) })),
    seatOptionDescriptors: [], removableBotSeats: [],
    hostSeat: 0, mySeat, minPlayers: 3, maxPlayers: 10, canStart: players.length >= 3, lobbyVersion: players.length,
    optionDescriptors: DESCRIPTORS, options: { ...DEFAULT_KILO_DE_MERDE_OPTIONS }, canEditOptions: mySeat === 0,
  } as unknown as LobbyView;
}

const json = (body: unknown, status = 200) => Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }));

describe('Kilo de merde, the life of a room', () => {
  let served: LobbyView | KiloDeMerdeState;
  let publish: ReturnType<typeof vi.spyOn>;
  let calls: string[];

  async function mountRoom() {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/', name: 'games', component: { render: () => null } }, { path: '/:rest(.*)*', component: { render: () => null } }],
    });
    await router.push('/kilo-de-merde/room/k1');
    await router.isReady();
    const wrapper = mount(KiloDeMerdeGameScreen, { props: { gameId: 'k1' }, global: { plugins: [router, PrimeVue] }, attachTo: document.body });
    await flushPromises();
    return wrapper;
  }

  const sent = () => publish.mock.calls.map(c => c[0] as string);

  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    calls = [];
    served = lobby([{ name: 'Alice' }]);
    publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
    vi.spyOn(webSocketService, 'subscribeToSeat').mockImplementation(() => {});
    vi.spyOn(webSocketService, 'setPresence').mockImplementation(() => {});
    vi.stubGlobal('fetch', vi.fn((url: string, init?: RequestInit) => {
      calls.push(`${init?.method ?? 'GET'} ${url}`);
      if (url === '/api/lobby') return json({ gameId: 'k1', token: 'tok-host' });
      if (url === '/api/kilo-de-merde/game/k1/join') return json({ playerId: 1, token: 'tok-guest' });
      if (url === '/api/kilo-de-merde/game/k1/play-again') return json({ playerId: 0, token: 'tok-again' });
      if (url.startsWith('/api/kilo-de-merde/game/k1?token=')) return json(served);
      return json({}, 404);
    }));
  });
  afterEach(() => { document.body.innerHTML = ''; vi.unstubAllGlobals(); vi.restoreAllMocks(); });

  it('givenTheRegistry_thenTheGameIsPlayableFromTheGameSelection', () => {
    const game = findGame('kilo-de-merde')!;
    expect(game.supportsBots).toBe(true);
    expect(useGameRooms()(game)).toBeTruthy();
  });

  it('givenAHost_thenCreateBotsStartPassAndPlayAgainWalkTheWholeLifecycle', async () => {
    const game = findGame('kilo-de-merde')!;
    const store = useKiloDeMerdeStore();

    // Create: the lobby request names the game type, the host token is stored under the game's own key.
    const id = await useGameRooms()(game).create('Alice');
    expect(id).toBe('k1');
    expect(calls[0]).toBe('POST /api/lobby');
    expect(JSON.parse(localStorage.getItem('kilo-de-merde:tokens:k1')!)).toEqual({ 0: 'tok-host' });
    expect(store.screen).toBe('lobby');

    const wrapper = await mountRoom();
    expect(wrapper.get('[data-test="player-count"]').text()).toContain('1 / 10');
    expect(wrapper.get('[data-test="start"]').attributes('disabled')).toBeDefined();

    // Bots: Add bot publishes the shared lobby command; the next lobby view seats two computer players.
    await wrapper.get('[data-test="add-bot"]').trigger('click');
    expect(sent()).toContain('/app/lobby/addBot');
    store.applyEvent({ type: 'state-update', state: lobby([{ name: 'Alice' }, { name: 'Bot 1', bot: true }]) });
    store.applyEvent({ type: 'state-update', state: { ...lobby([{ name: 'Alice' }, { name: 'Bot 1', bot: true }, { name: 'Bot 2', bot: true }]), lobbyVersion: 9 } });
    await flushPromises();
    expect(wrapper.get('[data-test="player-count"]').text()).toContain('3 / 10');
    expect(wrapper.get('[data-test="start"]').attributes('disabled')).toBeUndefined();

    // Start: the host publishes the game's own start command.
    await wrapper.get('[data-test="start"]').trigger('click');
    expect(sent()).toContain('/app/kilo-de-merde/start');
    store.applyEvent({ type: 'state-update', state: kiloDeMerdeStepState });
    await flushPromises();
    expect(wrapper.find('[data-test="kilo-table"]').exists()).toBe(true);

    // A pass: tapping a card sends it with the seat token, the pile slaps on its own button.
    await wrapper.get(`[data-card="${kiloDeMerdeStepState.hand[0].name}"]`).trigger('click');
    const pass = publish.mock.calls.find(c => c[0] === '/app/kilo-de-merde/pass')!;
    expect(JSON.parse(pass[1] as string)).toMatchObject({ gameId: 'k1', token: 'tok-host' });

    // The end, then Play again: the room reopens as a lobby and the screen shows it.
    store.applyEvent({ type: 'state-update', state: kiloDeMerdeFinishedState });
    await flushPromises();
    expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
    served = lobby([{ name: 'Alice' }, { name: 'Bot 1', bot: true }, { name: 'Bot 2', bot: true }]);
    await wrapper.get('[data-cy="play-again"]').trigger('click');
    await flushPromises();
    expect(calls).toContain('POST /api/kilo-de-merde/game/k1/play-again');
    expect(JSON.parse(localStorage.getItem('kilo-de-merde:tokens:k1')!)).toEqual({ 0: 'tok-again' });
    expect(store.screen).toBe('lobby');
    expect(wrapper.find('[data-test="lobby"]').exists()).toBe(true);
  });

  it('givenAGuest_thenJoinSeatsItInTheLobbyUnderItsOwnToken', async () => {
    served = lobby([{ name: 'Alice' }, { name: 'Bob' }], 1);
    const store = useKiloDeMerdeStore();
    await useGameRooms()(findGame('kilo-de-merde')!).join('k1', 'Bob');
    expect(calls[0]).toBe('POST /api/kilo-de-merde/game/k1/join');
    expect(JSON.parse(localStorage.getItem('kilo-de-merde:tokens:k1')!)).toEqual({ 1: 'tok-guest' });
    expect(store.mySeat).toBe(1);

    const wrapper = await mountRoom();
    expect(wrapper.get('[data-test="lobby"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="start"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="waiting-host"]').exists()).toBe(true);
  });
});
