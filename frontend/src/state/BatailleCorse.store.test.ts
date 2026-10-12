import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';

vi.mock('../service/WebSocketService', () => ({
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

import webSocketService from '../service/WebSocketService';
import { useBatailleCorseStore } from './BatailleCorse.store';
import type { LobbyView } from '../model/lobby/LobbyView';
import { buildGame } from '../model/fixtures';

function lobbyView(overrides: Partial<LobbyView> = {}): LobbyView {
  return {
    started: false, gameId: 'g1',
    players: [{ seat: 0, name: 'Alice', joined: true }, { seat: 1, name: null, joined: true, bot: true }],
    hostSeat: 0, mySeat: 0, minPlayers: 2, maxPlayers: 2, canStart: true, lobbyVersion: 1,
    ...overrides,
  };
}

type Store = ReturnType<typeof useBatailleCorseStore>;

function seated(seat = 0): Store {
  const store = useBatailleCorseStore();
  store.restore('g1', seat, `tok-${seat}`);
  return store;
}

describe('BatailleCorse store', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); vi.mocked(webSocketService.publish).mockReset(); });
  afterEach(() => { vi.unstubAllGlobals(); });

  describe('phase', () => {
    it('goes from connecting to the lobby, the table, then the end of the game', () => {
      const store = seated();
      expect(store.phase).toBe('connecting');

      store.applyEvent({ type: 'lobby-update', lobby: lobbyView() });
      expect(store.phase).toBe('lobby');
      expect(store.lobby?.gameId).toBe('g1');

      store.applyEvent({ type: 'state-update', state: buildGame() });
      expect(store.phase).toBe('playing');
      expect(store.lobby).toBeNull();

      store.applyEvent({ type: 'state-update', state: buildGame({ winner: { id: '0' } }) });
      expect(store.phase).toBe('finished');
    });

    it('back in the lobby (Play again) forgets the finished table', () => {
      const store = seated();
      store.applyEvent({ type: 'state-update', state: buildGame({ winner: { id: '0' } }) });

      store.applyEvent({ type: 'lobby-update', lobby: lobbyView({ lobbyVersion: 4 }) });

      expect(store.phase).toBe('lobby');
      expect(store.state).toBeUndefined();
    });
  });

  describe('opponent presence', () => {
    function disconnect(store: Store, seat: number, deadline = 1_060_000) {
      store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_DISCONNECTED',
        eventData: { disconnectedSeat: seat, deadlineEpochMs: deadline } });
    }

    it('exposes the opponent disconnect, and clears it on reconnect', () => {
      const store = seated(0);
      disconnect(store, 1);
      expect(store.liveDisconnections).toEqual({ 1: 1_060_000 });

      store.applyEvent({ type: 'presence-event', eventType: 'OPPONENT_RECONNECTED', eventData: { reconnectedSeat: 1 } });
      expect(store.liveDisconnections).toEqual({});
    });

    it('filters out a disconnect carrying my own seat', () => {
      const store = seated(1);
      disconnect(store, 1);
      expect(store.liveDisconnections).toEqual({});
    });

    it('is reset when the room goes back to a lobby', () => {
      const store = seated(0);
      disconnect(store, 1);

      store.applyEvent({ type: 'lobby-update', lobby: lobbyView() });

      expect(store.liveDisconnections).toEqual({});
    });
  });

  describe('lobby commands', () => {
    it('Start is sent once until the answer comes back', () => {
      const store = seated();
      store.applyEvent({ type: 'lobby-update', lobby: lobbyView() });

      expect(store.startGame()).toBe(true);
      expect(store.startGame()).toBe(false);
      expect(store.starting).toBe(true);
      expect(vi.mocked(webSocketService.publish).mock.calls.filter(c => c[0] === '/app/bataillecorse/start')).toHaveLength(1);

      store.applyEvent({ type: 'error', eventType: 'START', message: 'Not enough players' });
      expect(store.starting).toBe(false);
      expect(store.lobbyError).toBe('Not enough players');
    });

    it('a refusal shows in the lobby until the next lobby view, never at the table', () => {
      const store = seated();
      store.applyEvent({ type: 'lobby-update', lobby: lobbyView() });
      store.applyEvent({ type: 'error', eventType: 'JOIN', message: 'refused' });
      expect(store.lobbyError).toBe('refused');

      store.applyEvent({ type: 'lobby-update', lobby: lobbyView({ lobbyVersion: 2 }) });
      expect(store.lobbyError).toBeNull();

      store.applyEvent({ type: 'state-update', state: buildGame() });
      store.applyEvent({ type: 'error', eventType: 'SLAP', message: 'Not your turn' });
      expect(store.lobbyError).toBeNull();
    });

    it('a command that cannot be sent (socket down) is reported as a connection error', () => {
      const store = seated();
      vi.mocked(webSocketService.publish).mockImplementation(() => { throw new Error('no STOMP connection'); });

      expect(store.addBot()).toBe(false);
      expect(store.lobbyError).toBe('connection');
    });

    it('leave forgets the room; a leave that cannot be sent keeps the player seated', () => {
      const store = seated(1);
      store.applyEvent({ type: 'lobby-update', lobby: lobbyView({ mySeat: 1 }) });

      vi.mocked(webSocketService.publish).mockImplementationOnce(() => { throw new Error('down'); });
      expect(store.leave()).toBe(false);
      expect(store.lobby).not.toBeNull();

      expect(store.leave()).toBe(true);
      expect(store.lobby).toBeNull();
      expect(store.gameId).toBeNull();
    });
  });

  describe('seats', () => {
    it('name the opponent from the seat list, wherever my seat is', () => {
      const store = seated(1);
      store.applyEvent({ type: 'seats-change', seats: [
        { id: 0, name: 'Alice', joined: true, bot: false },
        { id: 1, name: 'Bob', joined: true, bot: false },
      ] });

      expect(store.opponentSeat?.name).toBe('Alice');
      expect(store.mySeatInfo?.name).toBe('Bob');
    });
  });

  describe('removed by the host', () => {
    it('forgets the lobby and says so; removeSeat sends the command', () => {
      const store = seated(1);
      store.applyEvent({ type: 'lobby-update', lobby: lobbyView({ mySeat: 1 }) });
      expect(store.removedByHost).toBe(false);

      store.removeSeat(2);
      expect(vi.mocked(webSocketService.publish).mock.calls.at(-1)?.[0]).toBe('/app/lobby/removeSeat');

      store.applyEvent({ type: 'removed' });

      expect(store.removedByHost).toBe(true);
      expect(store.lobby).toBeNull();
      expect(store.gameId).toBeNull();
      expect(store.phase).toBe('connecting');
    });
  });

  describe('playAgain', () => {
    it('is pending while the request runs, failed when refused, idle again in the new lobby', async () => {
      const store = seated();
      store.applyEvent({ type: 'state-update', state: buildGame({ winner: { id: '0' } }) });
      vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce({ ok: false, status: 409, json: async () => ({}) }));

      const request = store.playAgain();
      expect(store.rematch).toBe('pending');
      await request;
      expect(store.rematch).toBe('failed');

      store.applyEvent({ type: 'lobby-update', lobby: lobbyView({ lobbyVersion: 3 }) });
      expect(store.rematch).toBe('idle');
    });
  });
});
