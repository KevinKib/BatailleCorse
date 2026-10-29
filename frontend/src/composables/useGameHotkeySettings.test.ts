import { describe, it, expect, beforeEach } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';
import { useGameHotkeySettings } from './useGameHotkeySettings';
import { findGame, type GameDefinition } from '../model/games';

describe('useGameHotkeySettings', () => {
  beforeEach(() => { localStorage.clear(); setActivePinia(createPinia()); });

  it('givenBullshit_thenItsOwnSettingsAreReturned', () => {
    const settings = useGameHotkeySettings(findGame('bullshit')!);
    expect(settings?.actions.map(a => a.id)).toEqual(['discard', 'call']);
  });

  it('givenBatailleCorse_thenItsOwnSettingsAreReturned', () => {
    const settings = useGameHotkeySettings(findGame('bataillecorse')!);
    expect(settings?.actions.map(a => a.id)).toEqual(['send', 'slap']);
  });

  it('givenKiloDeMerde_thenItsOwnSettingsAreReturned', () => {
    const settings = useGameHotkeySettings(findGame('kilo-de-merde')!);
    expect(settings?.actions.map(a => a.id)).toEqual(['slap', 'pass1', 'pass2', 'pass3', 'pass4', 'ready']);
    expect(settings?.enabled?.value).toBe(true);
  });

  it('givenAGameWithoutHotkeys_thenNothingIsReturnedAndNoOtherGamesSettingsLeakIn', () => {
    const quiet: GameDefinition = { ...findGame('bullshit')!, key: 'kobo', hotkeys: undefined };
    expect(useGameHotkeySettings(quiet)).toBeNull();
  });
});
