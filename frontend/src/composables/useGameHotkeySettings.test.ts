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

  it('givenAGameWithoutHotkeys_thenNothingIsReturnedAndNoOtherGamesSettingsLeakIn', () => {
    const quiet: GameDefinition = { ...findGame('bullshit')!, key: 'kobo', hotkeys: undefined };
    expect(useGameHotkeySettings(quiet)).toBeNull();
  });
});
