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
    for (const key of ['bataillecorse', 'bullshit']) {
      const route = await resolveAt(`/games/${key}`);
      expect(route.fullPath).toBe(`/games/${key}`);
      expect(route.name).toBe('game-home');
      expect(route.params.game).toBe(key);
    }
  });

  it('givenInviteLinks_thenTheyAreNotRedirected', async () => {
    for (const key of ['bataillecorse', 'bullshit']) {
      const join = await resolveAt(`/games/${key}/join/abc`);
      expect(join.fullPath).toBe(`/games/${key}/join/abc`);
      expect(join.name).toBe('game-join');
      expect(join.params.id).toBe('abc');
      expect((await resolveAt(`/games/${key}/join`)).name).toBe('game-join');
    }
    expect((await resolveAt('/games/bullshit/room/abc')).name).toBe('bullshit-room');
    expect((await resolveAt('/games/bataillecorse/room/abc')).name).toBe('room');
  });

  it('givenLegacyCreatePaths_thenRedirectToTheGameHome', async () => {
    expect((await resolveAt('/games/bataillecorse/create')).fullPath).toBe('/games/bataillecorse');
    expect((await resolveAt('/games/bullshit/create')).fullPath).toBe('/games/bullshit');
  });

  it('givenNewGamePages_thenEachGameKeepsItsCreateScreen', async () => {
    expect((await resolveAt('/games/bataillecorse/new')).name).toBe('create');
    expect((await resolveAt('/games/bullshit/new')).name).toBe('bullshit-create');
  });

  it('givenAGameAbsentFromTheRegistry_thenRedirectsToTheGameSelection', async () => {
    expect((await resolveAt('/games/kobo')).fullPath).toBe('/games');
    expect((await resolveAt('/games/kobo/join/abc')).fullPath).toBe('/games');
  });
});
