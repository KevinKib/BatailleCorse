import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { effectScope, type EffectScope } from 'vue';
import { flushPromises } from '@vue/test-utils';
import type { PublicLobby, PublicLobbyList } from '../model/lobby/PublicLobby';
import { findGame } from '../model/games';

const sub = vi.hoisted(() => ({
  subscribe: vi.fn(),
  unsubscribe: vi.fn(),
}));
vi.mock('../service/WebSocketService', () => ({
  default: { subscribeToPublicLobbies: sub.subscribe },
}));

import { usePublicLobbies } from './usePublicLobbies';
import { setConnectionOnline } from './useConnectionStatus';

function lobby(id: string, over: Partial<PublicLobby> = {}): PublicLobby {
  return { gameId: id, gameType: 'bataille-corse', hostName: 'Zoe', joined: 1, bots: 0, maxPlayers: 4, state: 'OPEN', ...over };
}
function list(version: number, lobbies: PublicLobby[], over: Partial<PublicLobbyList> = {}): PublicLobbyList {
  return { gameType: 'bataille-corse', lobbies, total: lobbies.length, truncated: false, version, ...over };
}

let fetchMock: ReturnType<typeof vi.fn>;
let scope: EffectScope;
let onMessage: (l: PublicLobbyList) => void;
let onConnected: () => void;

function respond(l: PublicLobbyList | number) {
  fetchMock.mockResolvedValueOnce(typeof l === 'number'
    ? { ok: false, status: l, json: async () => ({}) }
    : { ok: true, status: 200, json: async () => l });
}

function start() {
  let api!: ReturnType<typeof usePublicLobbies>;
  scope = effectScope();
  scope.run(() => { api = usePublicLobbies(findGame('bataillecorse')!); });
  return api;
}

beforeEach(() => {
  vi.useFakeTimers();
  fetchMock = vi.fn();
  vi.stubGlobal('fetch', fetchMock);
  sub.subscribe.mockReset();
  sub.unsubscribe.mockReset();
  sub.subscribe.mockImplementation((_t: string, m: (l: PublicLobbyList) => void, c: () => void) => {
    onMessage = m; onConnected = c;
    return sub.unsubscribe;
  });
  setConnectionOnline(true);
});
afterEach(() => {
  scope?.stop();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('usePublicLobbies', () => {
  it('givenAGame_thenFetchesTheSnapshotOfItsServerTypeThenSubscribes', async () => {
    respond(list(3, [lobby('a')]));
    const api = start();
    expect(api.loading.value).toBe(true);
    await flushPromises();
    expect(fetchMock).toHaveBeenCalledWith('/api/lobbies/bataille-corse');
    expect(sub.subscribe).toHaveBeenCalledWith('bataille-corse', expect.any(Function), expect.any(Function));
    expect(api.lobbies.value.map(l => l.gameId)).toEqual(['a']);
    expect(api.total.value).toBe(1);
    expect(api.loading.value).toBe(false);
  });

  it('givenAFailedSnapshot_thenLoadingEndsWithAnEmptyList', async () => {
    respond(500);
    const api = start();
    await flushPromises();
    expect(api.loading.value).toBe(false);
    expect(api.lobbies.value).toEqual([]);
  });

  it('givenAPush_thenReplacesTheWholeList', async () => {
    respond(list(1, [lobby('a')]));
    const api = start();
    await flushPromises();
    onMessage(list(2, [lobby('a'), lobby('b')], { total: 7, truncated: true }));
    expect(api.lobbies.value.map(l => l.gameId)).toEqual(['a', 'b']);
    expect(api.total.value).toBe(7);
    expect(api.truncated.value).toBe(true);
  });

  it('givenAnOlderVersion_thenItIsDropped', async () => {
    respond(list(5, [lobby('a')]));
    const api = start();
    await flushPromises();
    onMessage(list(4, [lobby('old')]));
    onMessage(list(5, [lobby('same')]));
    expect(api.lobbies.value.map(l => l.gameId)).toEqual(['a']);
  });

  it('givenAPushBeforeTheSnapshot_thenTheLateOlderSnapshotIsDropped', async () => {
    let resolve!: (v: unknown) => void;
    fetchMock.mockReturnValueOnce(new Promise(r => { resolve = r; }));
    const api = start();
    onMessage(list(9, [lobby('new')]));
    resolve({ ok: true, status: 200, json: async () => list(8, [lobby('stale')]) });
    await flushPromises();
    expect(api.lobbies.value.map(l => l.gameId)).toEqual(['new']);
    expect(api.loading.value).toBe(false);
  });

  it('givenAReconnect_thenRefetchesAndTakesTheSnapshotEvenWithALowerVersion', async () => {
    respond(list(50, [lobby('a')]));
    const api = start();
    await flushPromises();
    respond(list(1, [lobby('after-restart')]));
    onConnected();
    await flushPromises();
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(api.lobbies.value.map(l => l.gameId)).toEqual(['after-restart']);
  });

  it('givenUnmount_thenUnsubscribes', async () => {
    respond(list(1, []));
    start();
    await flushPromises();
    scope.stop();
    expect(sub.unsubscribe).toHaveBeenCalledTimes(1);
  });

  it('exposesTheConnectionStatus', async () => {
    respond(list(1, []));
    const api = start();
    await flushPromises();
    expect(api.online.value).toBe(true);
    setConnectionOnline(false);
    expect(api.online.value).toBe(false);
  });

  describe('announcements', () => {
    it('saysNothingAboutTheFirstSnapshotOrAReconnectSnapshot', async () => {
      respond(list(1, [lobby('a')]));
      const api = start();
      await flushPromises();
      respond(list(2, [lobby('a'), lobby('b')]));
      onConnected();
      await flushPromises();
      await vi.advanceTimersByTimeAsync(5000);
      expect(api.announcement.value).toBe('');
    });

    it('sumsUpANewGameAndTheOpenCount', async () => {
      respond(list(1, [lobby('a')]));
      const api = start();
      await flushPromises();
      onMessage(list(2, [lobby('a'), lobby('b')]));
      expect(api.announcement.value).toBe('1 new game. 2 open.');
    });

    it('batchesAtMostOneMessageEveryTwoSeconds', async () => {
      respond(list(1, []));
      const api = start();
      await flushPromises();
      onMessage(list(2, [lobby('a')]));
      expect(api.announcement.value).toBe('1 new game. 1 open.');
      onMessage(list(3, [lobby('a'), lobby('b')]));
      onMessage(list(4, [lobby('a'), lobby('b'), lobby('c')]));
      expect(api.announcement.value).toBe('1 new game. 1 open.');
      await vi.advanceTimersByTimeAsync(2000);
      expect(api.announcement.value).toBe('2 new games. 3 open.');
    });

    it('announcesWhenNoGameIsOpenAnymore', async () => {
      respond(list(1, [lobby('a')]));
      const api = start();
      await flushPromises();
      onMessage(list(2, [lobby('a', { state: 'FULL' })]));
      expect(api.announcement.value).toBe('No open games.');
    });

    it('saysNothingWhenNothingVisibleChanged', async () => {
      respond(list(1, [lobby('a')]));
      const api = start();
      await flushPromises();
      onMessage(list(2, [lobby('a', { hostName: 'Zed' })]));
      expect(api.announcement.value).toBe('');
    });
  });
});
