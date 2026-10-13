import { describe, it, expect } from 'vitest';
import { createMemoryHistory } from 'vue-router';
import { createAppRouter } from './router';

async function resolveAt(path: string) {
  const router = createAppRouter(createMemoryHistory());
  await router.push(path);
  await router.isReady();
  return router.currentRoute.value;
}

describe('app router', () => {
  it('givenRoot_thenRedirectsToGamePicker', async () => {
    expect((await resolveAt('/')).fullPath).toBe('/games');
  });

  it('givenGames_thenShowsGamePickerWithoutRedirecting', async () => {
    const route = await resolveAt('/games');
    expect(route.fullPath).toBe('/games');
    expect(route.name).toBe('games');
  });

  it('givenUnknownRoute_thenRedirectsToGamePicker', async () => {
    expect((await resolveAt('/nope/at/all')).fullPath).toBe('/games');
  });

  it('givenAGameHome_thenStaysUnderItsOwnPathForBothGames', async () => {
    for (const key of ['bataillecorse', 'bullshit', 'kobo', 'three-men-morris']) {
      const route = await resolveAt(`/games/${key}`);
      expect(route.fullPath).toBe(`/games/${key}`);
      expect(route.name).toBe('game-home');
      expect(route.params.game).toBe(key);
    }
  });

  it('givenInviteLinks_thenTheyAreNotRedirected', async () => {
    for (const key of ['bataillecorse', 'bullshit', 'kobo', 'three-men-morris']) {
      const join = await resolveAt(`/games/${key}/join/abc`);
      expect(join.fullPath).toBe(`/games/${key}/join/abc`);
      expect(join.name).toBe('game-join');
      expect(join.params.id).toBe('abc');
      expect((await resolveAt(`/games/${key}/join`)).name).toBe('game-join');
    }
    expect((await resolveAt('/games/bullshit/room/abc')).name).toBe('bullshit-room');
    expect((await resolveAt('/games/bataillecorse/room/abc')).name).toBe('room');
    expect((await resolveAt('/games/kobo/room/abc')).name).toBe('kobo-room');
    expect((await resolveAt('/games/three-men-morris/room/abc')).name).toBe('three-men-morris-room');
  });

  it('givenLegacyCreatePaths_thenRedirectToTheGameHome', async () => {
    expect((await resolveAt('/games/bataillecorse/create')).fullPath).toBe('/games/bataillecorse');
    expect((await resolveAt('/games/bullshit/create')).fullPath).toBe('/games/bullshit');
    expect((await resolveAt('/games/three-men-morris/create')).fullPath).toBe('/games/three-men-morris');
  });

  it('givenTheRemovedCreatePages_thenLeadToTheGameHome', async () => {
    expect((await resolveAt('/games/bataillecorse/new')).fullPath).toBe('/games/bataillecorse');
    expect((await resolveAt('/games/bullshit/new')).fullPath).toBe('/games/bullshit');
    expect((await resolveAt('/games/three-men-morris/new')).fullPath).toBe('/games/three-men-morris');
  });

  it('givenAGameAbsentFromTheRegistry_thenRedirectsToTheGameSelection', async () => {
    expect((await resolveAt('/games/chess')).fullPath).toBe('/games');
    expect((await resolveAt('/games/chess/join/abc')).fullPath).toBe('/games');
  });
});
