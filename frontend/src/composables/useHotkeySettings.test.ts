import { describe, it, expect, beforeEach, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { nextTick } from 'vue';
import { useSettingsStore } from '../state/Settings.store';
import {
  HOTKEY_STORAGE_KEY, DEFAULT_HOTKEYS, loadHotkeySettings, saveHotkeySettings, validateHotkey, useHotkeySettings,
} from './useHotkeySettings';

const ev = (key: string, code = 'KeyX') => ({ key, code });

describe('hotkey settings storage', () => {
  beforeEach(() => { localStorage.clear(); vi.restoreAllMocks(); });

  it('defaults to enabled with D and B', () => {
    expect(loadHotkeySettings()).toEqual({ enabled: true, discard: 'd', call: 'b' });
    expect(DEFAULT_HOTKEYS).toEqual({ enabled: true, discard: 'd', call: 'b' });
  });

  it('remembers a saved choice', () => {
    saveHotkeySettings({ enabled: false, discard: 'x', call: 'k' });
    expect(loadHotkeySettings()).toEqual({ enabled: false, discard: 'x', call: 'k' });
  });

  it('falls back to defaults on corrupt or invalid stored data', () => {
    localStorage.setItem(HOTKEY_STORAGE_KEY, '{not json');
    expect(loadHotkeySettings()).toEqual(DEFAULT_HOTKEYS);
    localStorage.setItem(HOTKEY_STORAGE_KEY, JSON.stringify({ enabled: false, discard: 'x', call: 'x' }));
    expect(loadHotkeySettings()).toEqual({ enabled: false, discard: 'd', call: 'b' });
    localStorage.setItem(HOTKEY_STORAGE_KEY, JSON.stringify({ enabled: true, discard: '5', call: 'b' }));
    expect(loadHotkeySettings()).toEqual({ enabled: true, discard: '5', call: 'b' });
  });

  it('falls back to defaults when storage throws on read', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    expect(loadHotkeySettings()).toEqual(DEFAULT_HOTKEYS);
  });

  it('does not throw when storage throws on write', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
    expect(() => saveHotkeySettings(DEFAULT_HOTKEYS)).not.toThrow();
  });
});

describe('validateHotkey', () => {
  it('accepts a single printable key, case-insensitively', () => {
    expect(validateHotkey(ev('K'), 'b')).toEqual({ ok: true, key: 'k' });
  });

  it.each([['Tab'], ['Enter'], ['Escape'], [' '], ['Shift'], ['Control'], ['Alt'], ['Meta'], ['ArrowUp'], ['Dead']])(
    'rejects the reserved or non single-character key %j', (key) => {
      expect(validateHotkey(ev(key), 'b')).toEqual({ ok: false, reason: 'reserved' });
    });

  it('accepts digits, which no longer pick cards', () => {
    expect(validateHotkey(ev('5', 'Digit5'), 'b')).toEqual({ ok: true, key: '5' });
    expect(validateHotkey(ev('&', 'Digit1'), 'b')).toEqual({ ok: true, key: '&' });
    expect(validateHotkey(ev('7', 'Numpad7'), 'b')).toEqual({ ok: true, key: '7' });
  });

  it('rejects the key already used by the other action', () => {
    expect(validateHotkey(ev('B'), 'b')).toEqual({ ok: false, reason: 'duplicate' });
  });

  it('rejects any key in a list of other actions keys', () => {
    expect(validateHotkey(ev('C'), ['b', 'c'])).toEqual({ ok: false, reason: 'duplicate' });
    expect(validateHotkey(ev('x'), ['b', 'c'])).toEqual({ ok: true, key: 'x' });
  });
});

describe('useHotkeySettings', () => {
  beforeEach(() => localStorage.clear());

  it('exposes its two actions for the shared settings panel', () => {
    const s = useHotkeySettings();
    expect(s.actions.map((a) => [a.id, a.key.value])).toEqual([['discard', 'd'], ['call', 'b']]);
    s.setKey('call', ev('k'));
    expect(s.actions[1].key.value).toBe('k');
  });

  it('applies and persists a new key, rejecting a duplicate without changing anything', () => {
    const s = useHotkeySettings();
    expect(s.setKey('discard', ev('x'))).toEqual({ ok: true, key: 'x' });
    expect(s.discardKey.value).toBe('x');
    expect(s.setKey('call', ev('x'))).toEqual({ ok: false, reason: 'duplicate' });
    expect(s.callKey.value).toBe('b');
    expect(useHotkeySettings().discardKey.value).toBe('x');
  });

  it('allows swapping into the key the other action just left', () => {
    const s = useHotkeySettings();
    s.setKey('discard', ev('x'));
    expect(s.setKey('call', ev('d')).ok).toBe(true);
  });

  it('toggles everything off and resets to the defaults', () => {
    const s = useHotkeySettings();
    s.setKey('call', ev('k'));
    s.toggleEnabled();
    expect(s.enabled.value).toBe(false);
    expect(useHotkeySettings().enabled.value).toBe(false);
    s.reset();
    expect({ enabled: s.enabled.value, d: s.discardKey.value, c: s.callKey.value }).toEqual({ enabled: true, d: 'd', c: 'b' });
    expect(loadHotkeySettings()).toEqual(DEFAULT_HOTKEYS);
  });
});

describe("useHotkeySettings('bataillecorse')", () => {
  beforeEach(() => { localStorage.clear(); setActivePinia(createPinia()); });

  it('provides Send and Slap on the existing settings keys', () => {
    const s = useHotkeySettings('bataillecorse');
    expect(s.actions.map((a) => [a.id, a.key.value])).toEqual([['send', 'q'], ['slap', 'd']]);
    expect(s.enabled).toBeUndefined();
  });

  it('reads the keys already stored under bc_sendKey and bc_slapKey', () => {
    localStorage.setItem('bc_sendKey', 'w');
    localStorage.setItem('bc_slapKey', 'k');
    setActivePinia(createPinia());
    expect(useHotkeySettings('bataillecorse').actions.map((a) => a.key.value)).toEqual(['w', 'k']);
  });

  it('applies a new key to the settings store and persists it in the existing storage key', async () => {
    const s = useHotkeySettings('bataillecorse');
    expect(s.setKey('send', ev('X'))).toEqual({ ok: true, key: 'x' });
    expect(useSettingsStore().sendKey).toBe('x');
    await nextTick();
    expect(localStorage.getItem('bc_sendKey')).toBe('x');
  });

  it('refuses the key of the other action and a reserved key, changing nothing', () => {
    const s = useHotkeySettings('bataillecorse');
    expect(s.setKey('send', ev('d'))).toEqual({ ok: false, reason: 'duplicate' });
    expect(s.setKey('slap', ev('Tab', 'Tab'))).toEqual({ ok: false, reason: 'reserved' });
    expect(useSettingsStore().sendKey).toBe('q');
    expect(useSettingsStore().slapKey).toBe('d');
  });

  it('resets to Q and D', () => {
    const s = useHotkeySettings('bataillecorse');
    s.setKey('send', ev('x'));
    s.reset();
    expect(s.actions.map((a) => a.key.value)).toEqual(['q', 'd']);
  });

  it('does not touch the Bullshit storage', () => {
    useHotkeySettings('bataillecorse').setKey('send', ev('x'));
    expect(localStorage.getItem(HOTKEY_STORAGE_KEY)).toBeNull();
  });
});
