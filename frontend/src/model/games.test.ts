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

  it('givenEachGame_thenItDeclaresItsLocalHotkeyActionsAndItsOwnStorageKeys', () => {
    expect(findGame('bullshit')?.hotkeys.actions).toEqual(['discard', 'call']);
    expect(findGame('bataillecorse')?.hotkeys.actions).toEqual(['send', 'slap']);
    // Bataille Corse keeps the keys it has always stored; Bullshit has one entry of its own.
    expect(findGame('bataillecorse')?.hotkeys.storageKeys).toEqual(['bc_sendKey', 'bc_slapKey']);
    expect(findGame('bullshit')?.hotkeys.storageKeys).toEqual(['bullshit.hotkeys']);
    const all = GAMES.flatMap(g => g.hotkeys.storageKeys);
    expect(new Set(all).size).toBe(all.length);
  });

  it('givenBullshit_thenItsDigitsAreFixedAndSaid', () => {
    expect(findGame('bullshit')?.hotkeys.fixedDigits).toBe(true);
    expect(findGame('bataillecorse')?.hotkeys.fixedDigits).toBe(false);
  });

  it('givenUnknownKey_thenFindGameReturnsUndefined', () => {
    expect(findGame('kobo')).toBeUndefined();
    expect(findGame(undefined)).toBeUndefined();
  });
});
