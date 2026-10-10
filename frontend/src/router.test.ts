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

  it('givenBatailleCorseMenu_thenStaysUnderItsOwnPath', async () => {
    const route = await resolveAt('/games/bataillecorse');
    expect(route.fullPath).toBe('/games/bataillecorse');
    expect(route.name).toBe('home');
  });

  it('givenInviteLinks_thenTheyAreNotRedirected', async () => {
    expect((await resolveAt('/games/bullshit/join/abc')).name).toBe('bullshit-join');
    expect((await resolveAt('/games/bataillecorse/join/abc')).name).toBe('join');
    expect((await resolveAt('/games/bullshit/room/abc')).name).toBe('bullshit-room');
    expect((await resolveAt('/games/bataillecorse/room/abc')).name).toBe('room');
  });
});
