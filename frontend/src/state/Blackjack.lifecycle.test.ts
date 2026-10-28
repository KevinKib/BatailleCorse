import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useBlackjackStore } from './Blackjack.store';
import { useGameRooms } from '../composables/useGameRooms';
import { findGame } from '../model/games';
import webSocketService from '../service/WebSocketService';
import { blackjackBettingState, blackjackSoloEndState, blackjackTournamentEndState } from '../model/blackjack/fixtures';
import type { LobbyView } from '../model/lobby/LobbyView';

// The whole life of a Blackjack room as a player lives it, through the registry's room client and the real session:
// create, join, a bot, the options, start, a bet, the end, play again. Only the network is faked.
function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Ann', joined: true }],
    hostSeat: 0, mySeat: 0, minPlayers: 1, maxPlayers: 6, canStart: true, lobbyVersion: 1,
    optionDescriptors: [], options: { hands: '10', maxBet: '50' }, canEditOptions: true, ...overrides,
  };
}

const reply = (body: unknown) => Promise.resolve({ ok: true, status: 200, json: async () => body } as Response);

describe('Blackjack room lifecycle', () => {
  let publish: ReturnType<typeof vi.spyOn>;
  let fetchMock: ReturnType<typeof vi.fn>;
  let hydrated: unknown;
  const sent = () => publish.mock.calls.map(c => ({ dest: c[0], body: JSON.parse(c[1] as string) }));
  const calls = () => fetchMock.mock.calls.map(c => `${(c[1] as RequestInit | undefined)?.method ?? 'GET'} ${c[0] as string}`);

  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    publish = vi.spyOn(webSocketService, 'publish').mockImplementation(() => {});
    vi.spyOn(webSocketService, 'subscribeToSeat').mockImplementation(() => {});
    vi.spyOn(webSocketService, 'setPresence').mockImplementation(() => {});
    hydrated = lobbyView();
    fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url === '/api/lobby') return reply({ gameId: 'g1', token: 'tok0' });
      if (url === '/api/blackjack/game/g1/join') return reply({ playerId: 1, token: 'tok1' });
      if (url === '/api/blackjack/game/g1/play-again') return reply({ playerId: 0, token: 'tok0b' });
      if (init?.method === undefined && url.startsWith('/api/blackjack/game/g1?token=')) return reply(hydrated);
      return Promise.reject(new Error(`unexpected ${url}`));
    });
    vi.stubGlobal('fetch', fetchMock);
  });
  afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

  it('givenAHost_whenCreatingAddingABotStartingBettingAndPlayingAgain_thenTheScreenFollowsEveryStep', async () => {
    const store = useBlackjackStore();
    const room = useGameRooms()(findGame('blackjack')!);

    // Create: a lobby of one (a solo game is allowed), the token is kept for a reload.
    expect(await room.create('Ann')).toBe('g1');
    expect(calls()).toContain('POST /api/lobby');
    expect(store.screen).toBe('lobby');
    expect(store.gameId).toBe('g1');
    expect(store.lobby?.minPlayers).toBe(1);
    expect(JSON.parse(localStorage.getItem('blackjack:tokens:g1')!)).toEqual({ 0: 'tok0' });

    // A bot and an option go through the shared lobby commands.
    store.addBot();
    store.setOption('hands', '5');
    expect(sent().map(s => s.dest)).toEqual(['/app/lobby/addBot', '/app/lobby/setOptions']);
    expect(sent()[1].body).toMatchObject({ gameId: 'g1', token: 'tok0', values: { hands: '5' } });
    store.applyEvent({ type: 'state-update', state: lobbyView({
      lobbyVersion: 2, players: [{ seat: 0, name: 'Ann', joined: true }, { seat: 1, name: 'Bot', joined: true, bot: true }],
      options: { hands: '5', maxBet: '50' },
    }) as never });
    expect(store.lobby?.players).toHaveLength(2);

    // Start, then the first betting phase: the table, a one-tap bet.
    store.startGame();
    expect(sent().at(-1)).toMatchObject({ dest: '/app/blackjack/start', body: { gameId: 'g1', token: 'tok0' } });
    store.applyEvent({ type: 'state-update', state: blackjackBettingState });
    expect(store.screen).toBe('playing');
    expect(store.bet(10)).toBe(true);
    expect(sent().at(-1)).toMatchObject({ dest: '/app/blackjack/bet', body: { gameId: 'g1', token: 'tok0', amount: 10 } });

    // The end screen, then Play again reopens the lobby with a fresh token.
    store.applyEvent({ type: 'state-update', state: blackjackTournamentEndState });
    expect(store.screen).toBe('finished');
    hydrated = lobbyView({ lobbyVersion: 1 });
    await store.playAgain();
    expect(calls()).toContain('POST /api/blackjack/game/g1/play-again');
    expect(store.screen).toBe('lobby');
    expect(JSON.parse(localStorage.getItem('blackjack:tokens:g1')!)).toEqual({ 0: 'tok0b' });
  });

  it('givenAGuest_whenJoiningTheRoom_thenItSitsAtItsSeatInTheLobby', async () => {
    const store = useBlackjackStore();
    hydrated = lobbyView({
      mySeat: 1, canStart: false, canEditOptions: false,
      players: [{ seat: 0, name: 'Ann', joined: true }, { seat: 1, name: 'Bob', joined: true }],
    });
    await useGameRooms()(findGame('blackjack')!).join('g1', 'Bob');
    expect(calls()).toContain('POST /api/blackjack/game/g1/join');
    expect(store.mySeat).toBe(1);
    expect(store.screen).toBe('lobby');
    expect(JSON.parse(localStorage.getItem('blackjack:tokens:g1')!)).toEqual({ 1: 'tok1' });
  });

  it('givenASoloGame_whenItEnds_thenTheEndScreenIsShownAndTheRematchIsOffered', () => {
    const store = useBlackjackStore();
    store.restore('g1', 0, 'tok0');
    store.applyEvent({ type: 'state-update', state: blackjackSoloEndState });
    expect(store.screen).toBe('finished');
    expect(store.solo).toBe(true);
  });
});
