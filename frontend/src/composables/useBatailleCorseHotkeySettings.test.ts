import { describe, it, expect, beforeEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { nextTick } from 'vue';
import { useBatailleCorseHotkeySettings } from './useBatailleCorseHotkeySettings';
import { useSettingsStore } from '../state/Settings.store';

const ev = (key: string, code = 'KeyX') => ({ key, code });

describe('useBatailleCorseHotkeySettings', () => {
  beforeEach(() => { localStorage.clear(); setActivePinia(createPinia()); });

  it('provides Send and Slap on the existing settings keys', () => {
    const s = useBatailleCorseHotkeySettings();
    expect(s.actions.map((a) => [a.id, a.key.value])).toEqual([['send', 'q'], ['slap', 'd']]);
    expect(s.enabled).toBeUndefined();
  });

  it('reads the keys already stored under bc_sendKey and bc_slapKey', () => {
    localStorage.setItem('bc_sendKey', 'w');
    localStorage.setItem('bc_slapKey', 'k');
    setActivePinia(createPinia());
    expect(useBatailleCorseHotkeySettings().actions.map((a) => a.key.value)).toEqual(['w', 'k']);
  });

  it('applies a new key to the settings store and persists it in the existing storage key', async () => {
    const s = useBatailleCorseHotkeySettings();
    expect(s.setKey('send', ev('X'))).toEqual({ ok: true, key: 'x' });
    expect(useSettingsStore().sendKey).toBe('x');
    await nextTick();
    expect(localStorage.getItem('bc_sendKey')).toBe('x');
  });

  it('refuses the key of the other action and a reserved key, changing nothing', () => {
    const s = useBatailleCorseHotkeySettings();
    expect(s.setKey('send', ev('d'))).toEqual({ ok: false, reason: 'duplicate' });
    expect(s.setKey('slap', ev('Tab', 'Tab'))).toEqual({ ok: false, reason: 'reserved' });
    expect(useSettingsStore().sendKey).toBe('q');
    expect(useSettingsStore().slapKey).toBe('d');
  });

  it('resets to Q and D', () => {
    const s = useBatailleCorseHotkeySettings();
    s.setKey('send', ev('x'));
    s.reset();
    expect(s.actions.map((a) => a.key.value)).toEqual(['q', 'd']);
  });
});
