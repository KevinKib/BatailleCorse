import { ref, toRef, type Ref } from 'vue';
import { useSettingsStore } from '../state/Settings.store';

export const HOTKEY_STORAGE_KEY = 'bullshit.hotkeys';

export interface HotkeySettings { enabled: boolean; discard: string; call: string }
export type HotkeyAction = 'discard' | 'call';
export type HotkeyRejection = 'reserved' | 'duplicate';
export type HotkeyValidation = { ok: true; key: string } | { ok: false; reason: HotkeyRejection };

// What the shared settings panel needs from a game: its remappable actions (a game labels them), an
// optional master switch, and the two operations. Bullshit and Bataille Corse each provide one.
export interface HotkeyPanelApi {
  actions: { id: string; key: Ref<string> }[];
  enabled?: Ref<boolean>;
  toggleEnabled?: () => void;
  setKey: (action: string, e: { key: string; code: string }) => HotkeyValidation;
  reset: () => void;
}

export const DEFAULT_HOTKEYS: Readonly<HotkeySettings> = { enabled: true, discard: 'd', call: 'b' };

// A shortcut is one printable character. Everything else (Tab, Enter, Space, Escape, modifiers,
// arrows, dead keys...) is reserved for navigation and assistive technologies.
export function validateHotkey(e: { key: string; code: string }, otherKeys: string | string[]): HotkeyValidation {
  const key = e.key.toLowerCase();
  if ([...key].length !== 1 || key.trim() === '') return { ok: false, reason: 'reserved' };
  if ([otherKeys].flat().includes(key)) return { ok: false, reason: 'duplicate' };
  return { ok: true, key };
}

function isValidStored(s: unknown): s is HotkeySettings {
  if (!s || typeof s !== 'object') return false;
  const { enabled, discard, call } = s as Record<string, unknown>;
  return typeof enabled === 'boolean' && typeof discard === 'string' && typeof call === 'string'
    && validateHotkey({ key: discard, code: '' }, call).ok
    && validateHotkey({ key: call, code: '' }, discard).ok;
}

// Storage can be blocked or throw (private window, cleared site data) and can hold anything: the
// defaults then apply for the session and the choice is not remembered.
export function loadHotkeySettings(): HotkeySettings {
  try {
    const raw = localStorage.getItem(HOTKEY_STORAGE_KEY);
    if (raw === null) return { ...DEFAULT_HOTKEYS };
    const parsed: unknown = JSON.parse(raw);
    if (isValidStored(parsed)) return { enabled: parsed.enabled, discard: parsed.discard, call: parsed.call };
    // Keep a stored "off" even if the keys are unusable.
    const enabled = (parsed as { enabled?: unknown } | null)?.enabled;
    return { ...DEFAULT_HOTKEYS, enabled: typeof enabled === 'boolean' ? enabled : true };
  } catch {
    return { ...DEFAULT_HOTKEYS };
  }
}

export function saveHotkeySettings(settings: HotkeySettings): void {
  try {
    localStorage.setItem(HOTKEY_STORAGE_KEY, JSON.stringify(settings));
  } catch {
    /* not remembered */
  }
}

// Bataille Corse's Send and Slap keys live on the settings store, which already persists them
// (`bc_sendKey`, `bc_slapKey`, the keys players hold); there is no master switch for this game.
const BATAILLE_CORSE_DEFAULTS = { send: 'q', slap: 'd' };

function useBatailleCorseHotkeys(): HotkeyPanelApi {
  const store = useSettingsStore();
  const send = toRef(store, 'sendKey');
  const slap = toRef(store, 'slapKey');

  function setKey(action: string, e: { key: string; code: string }) {
    const target = action === 'send' ? send : slap;
    const result = validateHotkey(e, (action === 'send' ? slap : send).value);
    if (result.ok) target.value = result.key;
    return result;
  }
  function reset() {
    send.value = BATAILLE_CORSE_DEFAULTS.send;
    slap.value = BATAILLE_CORSE_DEFAULTS.slap;
  }
  return { actions: [{ id: 'send', key: send }, { id: 'slap', key: slap }], setKey, reset };
}

/**
 * Local shortcut preferences of a game, as the settings panel needs them. Bullshit (the default) keeps its
 * own storage and exposes its keys by name; Bataille Corse reads and writes the settings store. Only the
 * game asked for is read.
 */
export function useHotkeySettings(game: 'bataillecorse'): HotkeyPanelApi;
export function useHotkeySettings(game?: 'bullshit'): ReturnType<typeof useBullshitHotkeySettings>;
export function useHotkeySettings(game: 'bullshit' | 'bataillecorse' = 'bullshit') {
  return game === 'bataillecorse' ? useBatailleCorseHotkeys() : useBullshitHotkeySettings();
}

// Reactive preference for a screen: read once, written on every change.
function useBullshitHotkeySettings() {
  const initial = loadHotkeySettings();
  const enabled = ref(initial.enabled);
  const discardKey = ref(initial.discard);
  const callKey = ref(initial.call);
  const persist = () => saveHotkeySettings({ enabled: enabled.value, discard: discardKey.value, call: callKey.value });

  function toggleEnabled() {
    enabled.value = !enabled.value;
    persist();
  }
  function setKey(action: string, e: { key: string; code: string }): HotkeyValidation {
    const result = validateHotkey(e, action === 'discard' ? callKey.value : discardKey.value);
    if (!result.ok) return result;
    (action === 'discard' ? discardKey : callKey).value = result.key;
    persist();
    return result;
  }
  function reset() {
    enabled.value = DEFAULT_HOTKEYS.enabled;
    discardKey.value = DEFAULT_HOTKEYS.discard;
    callKey.value = DEFAULT_HOTKEYS.call;
    persist();
  }
  const actions = [{ id: 'discard', key: discardKey }, { id: 'call', key: callKey }];
  return { enabled, discardKey, callKey, actions, toggleEnabled, setKey, reset };
}

