import { describe, it, expect } from 'vitest';
import { GAMES, findGame } from './games';

describe('game registry', () => {
  it('givenBothGames_thenEachDeclaresItsRoutesUnderGamesKey', () => {
    expect(GAMES.map(g => g.key)).toEqual(['bataillecorse', 'bullshit']);
    for (const g of GAMES) {
      expect(g.gameHomeRoute).toBe(`/games/${g.key}`);
      expect(g.joinRoute()).toBe(`/games/${g.key}/join`);
      expect(g.joinRoute('abc')).toBe(`/games/${g.key}/join/abc`);
      expect(g.roomRoute('abc')).toBe(`/games/${g.key}/room/abc`);
    }
  });

  it('givenBatailleCorse_thenNewGameStillLeadsToItsCreatePage', () => {
    expect(findGame('bataillecorse')?.newGameRoute).toBe('/games/bataillecorse/new');
  });

  it('givenBullshit_thenNewGameCreatesTheLobbyDirectlyWithoutACreatePage', () => {
    expect(findGame('bullshit')?.newGameRoute).toBeUndefined();
  });

  it('givenUnknownKey_thenFindGameReturnsUndefined', () => {
    expect(findGame('kobo')).toBeUndefined();
    expect(findGame(undefined)).toBeUndefined();
  });
});
