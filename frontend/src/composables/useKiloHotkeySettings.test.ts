import { describe, it, expect, beforeEach, vi } from 'vitest';
import {
  KILO_HOTKEY_STORAGE_KEY, KILO_DEFAULT_HOTKEYS, KILO_HOTKEY_ACTIONS, loadKiloHotkeySettings, useKiloHotkeySettings,
} from './useKiloHotkeySettings';

const ev = (key: string, code = 'KeyX') => ({ key, code });

describe('Kilo de merde hotkey storage', () => {
  beforeEach(() => { localStorage.clear(); vi.restoreAllMocks(); });

  it('givenNothingStored_thenTheDefaultsAreTheKeysOfBeforeTheSettings', () => {
    expect(KILO_HOTKEY_ACTIONS).toEqual(['slap', 'pass1', 'pass2', 'pass3', 'pass4', 'ready']);
    expect(loadKiloHotkeySettings()).toEqual({
      enabled: true, keys: { slap: ' ', pass1: '1', pass2: '2', pass3: '3', pass4: '4', ready: 'r' },
    });
    expect(KILO_DEFAULT_HOTKEYS.keys.slap).toBe(' ');
  });

  it('givenCorruptOrConflictingStorage_thenTheDefaultsApplyButAStoredOffIsKept', () => {
    localStorage.setItem(KILO_HOTKEY_STORAGE_KEY, '{not json');
    expect(loadKiloHotkeySettings()).toEqual(KILO_DEFAULT_HOTKEYS);
    localStorage.setItem(KILO_HOTKEY_STORAGE_KEY, JSON.stringify({ enabled: false, keys: { ...KILO_DEFAULT_HOTKEYS.keys, ready: '1' } }));
    expect(loadKiloHotkeySettings()).toEqual({ ...KILO_DEFAULT_HOTKEYS, enabled: false });
    localStorage.setItem(KILO_HOTKEY_STORAGE_KEY, JSON.stringify({ enabled: true, keys: { slap: 'x' } }));
    expect(loadKiloHotkeySettings()).toEqual(KILO_DEFAULT_HOTKEYS);
  });

  it('givenStorageThatThrows_thenTheDefaultsApplyAndNothingBreaks', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
    const s = useKiloHotkeySettings();
    expect(s.enabled.value).toBe(true);
    expect(s.setKey('slap', ev('x')).ok).toBe(true);
  });
});

describe('useKiloHotkeySettings', () => {
  beforeEach(() => { localStorage.clear(); });

  const keyOf = (s: ReturnType<typeof useKiloHotkeySettings>, id: string) => s.actions.find(a => a.id === id)!.key.value;

  it('givenTheSettings_thenTheyListEveryActionInOrderWithItsKey', () => {
    const s = useKiloHotkeySettings();
    expect(s.actions.map(a => a.id)).toEqual(['slap', 'pass1', 'pass2', 'pass3', 'pass4', 'ready']);
    expect(keyOf(s, 'slap')).toBe(' ');
    expect(keyOf(s, 'ready')).toBe('r');
  });

  it('givenANewKey_thenItIsAppliedAndRememberedUnderTheGamesOwnStorageKey', () => {
    const s = useKiloHotkeySettings();
    expect(s.setKey('slap', ev('X'))).toEqual({ ok: true, key: 'x' });
    expect(keyOf(s, 'slap')).toBe('x');
    expect(JSON.parse(localStorage.getItem('kilo-de-merde.hotkeys')!).keys.slap).toBe('x');
    expect(keyOf(useKiloHotkeySettings(), 'slap')).toBe('x');
  });

  it('givenAKeyUsedByAnotherAction_thenItIsRejectedAndNothingChanges', () => {
    const s = useKiloHotkeySettings();
    expect(s.setKey('slap', ev('1'))).toEqual({ ok: false, reason: 'duplicate' });
    expect(s.setKey('pass2', ev('r'))).toEqual({ ok: false, reason: 'duplicate' });
    expect(keyOf(s, 'slap')).toBe(' ');
    expect(keyOf(s, 'pass2')).toBe('2');
  });

  it('givenAReservedKey_thenItIsRejected', () => {
    const s = useKiloHotkeySettings();
    expect(s.setKey('ready', ev('Tab', 'Tab'))).toEqual({ ok: false, reason: 'reserved' });
    expect(s.setKey('ready', ev(' ', 'Space'))).toEqual({ ok: false, reason: 'reserved' });
  });

  it('givenAKeyJustLeft_thenAnotherActionCanTakeIt', () => {
    const s = useKiloHotkeySettings();
    s.setKey('pass1', ev('x'));
    expect(s.setKey('ready', ev('1')).ok).toBe(true);
  });

  it('givenTheMasterSwitchAndReset_thenTheyArePersistedAndRestoreTheDefaults', () => {
    const s = useKiloHotkeySettings();
    s.setKey('slap', ev('k'));
    s.toggleEnabled();
    expect(s.enabled.value).toBe(false);
    expect(useKiloHotkeySettings().enabled.value).toBe(false);
    s.reset();
    expect(s.enabled.value).toBe(true);
    expect(keyOf(s, 'slap')).toBe(' ');
    expect(useKiloHotkeySettings().enabled.value).toBe(true);
  });
});
