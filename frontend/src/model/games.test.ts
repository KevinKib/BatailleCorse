import { describe, it, expect } from 'vitest';
import { GAMES, findGame, type GameDefinition, type GameKey } from './games';

describe('game registry', () => {
  it('givenEveryGame_thenEachDeclaresItsRoutesUnderGamesKey', () => {
    expect(GAMES.map(g => g.key)).toEqual(['bataillecorse', 'bullshit', 'kobo']);
    for (const g of GAMES) {
      expect(g.gameHomeRoute).toBe(`/games/${g.key}`);
      expect(g.joinRoute()).toBe(`/games/${g.key}/join`);
      expect(g.joinRoute('abc')).toBe(`/games/${g.key}/join/abc`);
      expect(g.roomRoute('abc')).toBe(`/games/${g.key}/room/abc`);
    }
  });

  it('givenEveryGame_thenNewGameCreatesTheLobbyDirectlyWithoutACreatePage', () => {
    GAMES.forEach(g => expect(Object.keys(g)).not.toContain('newGameRoute'));
  });

  it('givenEachGame_thenItDeclaresItsLocalHotkeyActionsAndItsOwnStorageKeys', () => {
    expect(findGame('bullshit')?.hotkeys?.actions).toEqual(['discard', 'call']);
    expect(findGame('bataillecorse')?.hotkeys?.actions).toEqual(['send', 'slap']);
    // Bataille Corse keeps the keys it has always stored; Bullshit has one entry of its own.
    expect(findGame('bataillecorse')?.hotkeys?.storageKeys).toEqual(['bc_sendKey', 'bc_slapKey']);
    expect(findGame('bullshit')?.hotkeys?.storageKeys).toEqual(['bullshit.hotkeys']);
    const all = GAMES.flatMap(g => g.hotkeys?.storageKeys ?? []);
    expect(new Set(all).size).toBe(all.length);
  });

  it('givenAnyGameWithHotkeys_thenItsHotkeysAreOnlyRemappableActions', () => {
    GAMES.filter(g => g.hotkeys).forEach(g => expect(Object.keys(g.hotkeys!).sort()).toEqual(['actions', 'storageKeys']));
  });

  it('givenBatailleCorseAndBullshit_thenBothOfferBotsAndKoboDoesNot', () => {
    expect(findGame('bataillecorse')?.supportsBots).toBe(true);
    expect(findGame('bullshit')?.supportsBots).toBe(true);
    expect(findGame('kobo')?.supportsBots).toBe(false);
  });

  it('givenKobo_thenItHasNoShortcutsSoNoGear', () => {
    expect(findGame('kobo')?.hotkeys).toBeUndefined();
  });

  it('givenEachGame_thenItDeclaresItsPickerIconAndSeverity', () => {
    expect(findGame('bataillecorse')?.picker).toEqual({ icon: 'pi pi-play', severity: 'success' });
    expect(findGame('bullshit')?.picker).toEqual({ icon: 'pi pi-bolt', severity: 'warn' });
    expect(findGame('kobo')?.picker).toEqual({ icon: 'pi pi-star', severity: 'info' });
  });

  it('givenTheGameKeyIsOpen_thenThreeMenMorrisIsAKeyButNotRegisteredYet', () => {
    const upcoming: GameKey[] = ['three-men-morris'];
    upcoming.forEach(k => expect(findGame(k)).toBeUndefined());
  });

  it('givenAGameWithoutShortcuts_thenItsDefinitionNeedsNoHotkeys', () => {
    const quiet: GameDefinition = {
      key: 'kobo', gameHomeRoute: '/games/kobo', joinRoute: () => '/', roomRoute: () => '/',
      supportsBots: false, picker: { icon: 'pi pi-star', severity: 'info' },
    };
    expect(quiet.hotkeys).toBeUndefined();
  });

  it('givenUnknownKey_thenFindGameReturnsUndefined', () => {
    expect(findGame('nope')).toBeUndefined();
    expect(findGame(undefined)).toBeUndefined();
  });
});
