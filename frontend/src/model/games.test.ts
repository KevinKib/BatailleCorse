import { describe, it, expect } from 'vitest';
import { GAMES, findGame, type GameDefinition, type GameKey } from './games';

describe('game registry', () => {
  it('givenEveryGame_thenEachDeclaresItsRoutesUnderTheirKey', () => {
    expect(GAMES.map(g => g.key)).toEqual(['bataillecorse', 'bullshit', 'kobo', 'three-men-morris', 'president', 'mangola', 'ninety-nine', 'draughts']);
    for (const g of GAMES) {
      expect(g.gameHomeRoute).toBe(`/${g.key}`);
      expect(g.joinRoute()).toBe(`/${g.key}/join`);
      expect(g.joinRoute('abc')).toBe(`/${g.key}/join/abc`);
      expect(g.roomRoute('abc')).toBe(`/${g.key}/room/abc`);
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
    expect(findGame('kobo')?.supportsBots).toBe(true);
  });

  it('givenKobo_thenItHasNoShortcutsSoNoGear', () => {
    expect(findGame('kobo')?.hotkeys).toBeUndefined();
  });

  it('givenEachGame_thenItDeclaresItsPickerIconAndSeverity', () => {
    expect(findGame('bataillecorse')?.picker).toEqual({ icon: 'pi pi-play', severity: 'success' });
    expect(findGame('bullshit')?.picker).toEqual({ icon: 'pi pi-bolt', severity: 'warn' });
    expect(findGame('kobo')?.picker).toEqual({ icon: 'pi pi-star', severity: 'info' });
  });

  it('givenThreeMenMorris_thenItIsRegisteredWithBotsButNoShortcutsAndItsOwnPickerStyle', () => {
    const morris = findGame('three-men-morris');
    expect(morris?.supportsBots).toBe(true);
    expect(morris?.hotkeys).toBeUndefined();
    expect(morris?.picker).toEqual({ icon: 'pi pi-th-large', severity: 'info' });
  });

  it('givenMangola_thenItIsRegisteredWithBotsButNoShortcutsAndItsOwnPickerStyle', () => {
    const mangola = findGame('mangola');
    expect(mangola?.supportsBots).toBe(true);
    expect(mangola?.hotkeys).toBeUndefined();
    expect(mangola?.picker).toEqual({ icon: 'pi pi-circle', severity: 'help' });
  });

  it('givenNinetyNine_thenItIsRegisteredWithBotsButNoShortcutsAndItsOwnPickerStyle', () => {
    const ninetyNine = findGame('ninety-nine');
    expect(ninetyNine?.serverType).toBe('ninety-nine');
    expect(ninetyNine?.supportsBots).toBe(true);
    expect(ninetyNine?.hotkeys).toBeUndefined();
    expect(ninetyNine?.picker).toEqual({ icon: 'pi pi-hashtag', severity: 'danger' });
    expect(ninetyNine?.roomRoute('abc')).toBe('/ninety-nine/room/abc');
  });

  it('givenDraughts_thenItIsRegisteredWithBotsButNoShortcutsAndItsOwnPickerStyle', () => {
    const draughts = findGame('draughts');
    expect(draughts?.serverType).toBe('draughts');
    expect(draughts?.supportsBots).toBe(true);
    expect(draughts?.hotkeys).toBeUndefined();
    expect(draughts?.picker).toEqual({ icon: 'pi pi-table', severity: 'secondary' });
    expect(draughts?.roomRoute('abc')).toBe('/draughts/room/abc');
  });

  it('givenPresident_thenItIsRegisteredWithBotsButNoShortcutsAndItsOwnPickerStyle', () => {
    const president = findGame('president');
    expect(president?.supportsBots).toBe(true);
    expect(president?.hotkeys).toBeUndefined();
    expect(president?.picker).toEqual({ icon: 'pi pi-crown', severity: 'contrast' });
    expect(president?.roomRoute('abc')).toBe('/president/room/abc');
  });

  it('givenTheGameKeyIsOpen_thenAnUnknownKeyIsNotRegistered', () => {
    const upcoming = ['chess'] as unknown as GameKey[];
    upcoming.forEach(k => expect(findGame(k)).toBeUndefined());
  });

  it('givenAGameWithoutShortcuts_thenItsDefinitionNeedsNoHotkeys', () => {
    const quiet: GameDefinition = {
      key: 'kobo', serverType: 'kobo', gameHomeRoute: '/kobo', joinRoute: () => '/', roomRoute: () => '/',
      supportsBots: false, picker: { icon: 'pi pi-star', severity: 'info' },
    };
    expect(quiet.hotkeys).toBeUndefined();
  });

  it('givenUnknownKey_thenFindGameReturnsUndefined', () => {
    expect(findGame('nope')).toBeUndefined();
    expect(findGame(undefined)).toBeUndefined();
  });

  it('givenEveryGame_thenItsServerTypeIsItsKeyExceptForBatailleCorse', () => {
    expect(findGame('bataillecorse')?.serverType).toBe('bataille-corse');
    for (const g of GAMES.filter(g => g.key !== 'bataillecorse')) {
      expect(g.serverType, g.key).toBe(g.key);
    }
  });
});
