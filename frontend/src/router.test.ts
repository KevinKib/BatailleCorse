import { describe, it, expect } from 'vitest';
import { createMemoryHistory } from 'vue-router';
import { createAppRouter } from './router';
import { GAMES } from './model/games';

async function resolveAt(path: string) {
  const router = createAppRouter(createMemoryHistory());
  await router.push(path);
  await router.isReady();
  return router.currentRoute.value;
}

describe('app router', () => {
  it('givenRoot_thenShowsGamePickerWithoutRedirecting', async () => {
    const route = await resolveAt('/');
    expect(route.fullPath).toBe('/');
    expect(route.name).toBe('games');
  });

  it('givenUnknownRoute_thenRedirectsToGamePicker', async () => {
    expect((await resolveAt('/nope/at/all')).fullPath).toBe('/');
  });

  it('givenAGameHome_thenStaysUnderItsOwnPathForBothGames', async () => {
    for (const key of ['bataillecorse', 'bullshit', 'kobo', 'three-men-morris', 'president', 'mangola']) {
      const route = await resolveAt(`/${key}`);
      expect(route.fullPath).toBe(`/${key}`);
      expect(route.name).toBe('game-home');
      expect(route.params.game).toBe(key);
    }
  });

  it('givenInviteLinks_thenTheyAreNotRedirected', async () => {
    for (const key of ['bataillecorse', 'bullshit', 'kobo', 'three-men-morris', 'president', 'mangola']) {
      const join = await resolveAt(`/${key}/join/abc`);
      expect(join.fullPath).toBe(`/${key}/join/abc`);
      expect(join.name).toBe('game-join');
      expect(join.params.id).toBe('abc');
      expect((await resolveAt(`/${key}/join`)).name).toBe('game-join');
    }
    expect((await resolveAt('/bullshit/room/abc')).name).toBe('bullshit-room');
    expect((await resolveAt('/bataillecorse/room/abc')).name).toBe('room');
    expect((await resolveAt('/kobo/room/abc')).name).toBe('kobo-room');
    expect((await resolveAt('/three-men-morris/room/abc')).name).toBe('three-men-morris-room');
    expect((await resolveAt('/president/room/abc')).name).toBe('president-room');
    expect((await resolveAt('/mangola/room/abc')).name).toBe('mangola-room');
  });

  it('givenLegacyCreatePaths_thenRedirectToTheGameHome', async () => {
    expect((await resolveAt('/bataillecorse/create')).fullPath).toBe('/bataillecorse');
    expect((await resolveAt('/bullshit/create')).fullPath).toBe('/bullshit');
    expect((await resolveAt('/three-men-morris/create')).fullPath).toBe('/three-men-morris');
  });

  it('givenTheRemovedCreatePages_thenLeadToTheGameHome', async () => {
    expect((await resolveAt('/bataillecorse/new')).fullPath).toBe('/bataillecorse');
    expect((await resolveAt('/bullshit/new')).fullPath).toBe('/bullshit');
    expect((await resolveAt('/three-men-morris/new')).fullPath).toBe('/three-men-morris');
  });

  it('givenAGameAbsentFromTheRegistry_thenRedirectsToTheGameSelection', async () => {
    expect((await resolveAt('/chess')).fullPath).toBe('/');
    expect((await resolveAt('/chess/join/abc')).fullPath).toBe('/');
  });

  it('givenLegacyGamesPrefix_thenOldLinksRedirectToTheNewPaths', async () => {
    expect((await resolveAt('/games')).fullPath).toBe('/');
    expect((await resolveAt('/games/')).fullPath).toBe('/');
    expect((await resolveAt('/games/bullshit')).fullPath).toBe('/bullshit');
    expect((await resolveAt('/games/president/join/abc')).fullPath).toBe('/president/join/abc');
    expect((await resolveAt('/games/president/join/abc')).name).toBe('game-join');
    expect((await resolveAt('/games/mangola/room/abc')).fullPath).toBe('/mangola/room/abc');
    expect((await resolveAt('/games/mangola/room/abc')).name).toBe('mangola-room');
    expect((await resolveAt('/games/bataillecorse/room/abc')).fullPath).toBe('/bataillecorse/room/abc');
    expect((await resolveAt('/games/bullshit/join/abc?x=1')).fullPath).toBe('/bullshit/join/abc?x=1');
  });

  it('givenLegacyPrefixWithAnUnknownGame_thenEndsOnTheGamePicker', async () => {
    expect((await resolveAt('/games/chess')).fullPath).toBe('/');
  });

  it('givenPrivacyPath_thenShowsThePrivacyPageWithoutRedirecting', async () => {
    const route = await resolveAt('/privacy');
    expect(route.fullPath).toBe('/privacy');
    expect(route.name).toBe('privacy');
  });

  it('givenPrivacy_thenItIsNotAGameKeyAndGameRoutesAreUnaffected', async () => {
    expect(GAMES.map(game => game.key as string)).not.toContain('privacy');
    expect((await resolveAt('/privacy/join/abc')).fullPath).toBe('/');
    expect((await resolveAt('/games/privacy')).fullPath).toBe('/privacy');
    expect((await resolveAt('/bullshit')).name).toBe('game-home');
    expect((await resolveAt('/chess')).fullPath).toBe('/');
  });
});
