import { ref, type Ref } from 'vue';
import { validateHotkey, type HotkeyPanelApi, type HotkeyValidation } from './useHotkeySettings';

export const KILO_HOTKEY_STORAGE_KEY = 'kilo-de-merde.hotkeys';

// The remappable actions of the Kilo de merde table, in the order the settings panel lists them.
export const KILO_HOTKEY_ACTIONS = ['slap', 'pass1', 'pass2', 'pass3', 'pass4', 'ready'] as const;
export type KiloHotkeyAction = (typeof KILO_HOTKEY_ACTIONS)[number];

export interface KiloHotkeySettings { enabled: boolean; keys: Record<KiloHotkeyAction, string> }

// Space slaps by default: it is a valid default and a valid stored value, although the panel cannot capture it
// again (it is reserved like Tab and Enter), so "Reset" is how a player gets it back.
export const KILO_DEFAULT_HOTKEYS: Readonly<KiloHotkeySettings> = {
  enabled: true,
  keys: { slap: ' ', pass1: '1', pass2: '2', pass3: '3', pass4: '4', ready: 'r' },
};

const isKey = (k: unknown): k is string => typeof k === 'string' && [...k].length === 1;

function validKeys(keys: unknown): keys is Record<KiloHotkeyAction, string> {
  if (!keys || typeof keys !== 'object') return false;
  const values = KILO_HOTKEY_ACTIONS.map(a => (keys as Record<string, unknown>)[a]);
  return values.every(isKey) && new Set(values.map(v => (v as string).toLowerCase())).size === values.length;
}

// Storage can be blocked or throw and can hold anything: the defaults then apply (a stored "off" is kept).
export function loadKiloHotkeySettings(): KiloHotkeySettings {
  const defaults = (): KiloHotkeySettings => ({ enabled: KILO_DEFAULT_HOTKEYS.enabled, keys: { ...KILO_DEFAULT_HOTKEYS.keys } });
  try {
    const raw = localStorage.getItem(KILO_HOTKEY_STORAGE_KEY);
    if (raw === null) return defaults();
    const parsed = JSON.parse(raw) as { enabled?: unknown; keys?: unknown } | null;
    const enabled = typeof parsed?.enabled === 'boolean' ? parsed.enabled : true;
    if (validKeys(parsed?.keys)) return { enabled, keys: { ...parsed.keys } };
    return { ...defaults(), enabled };
  } catch {
    return defaults();
  }
}

function save(settings: KiloHotkeySettings): void {
  try {
    localStorage.setItem(KILO_HOTKEY_STORAGE_KEY, JSON.stringify(settings));
  } catch {
    /* not remembered */
  }
}

/** Reactive shortcut preferences of the Kilo de merde table: read once, written on every change. */
export function useKiloHotkeySettings(): HotkeyPanelApi & { enabled: Ref<boolean>; toggleEnabled: () => void; keyOf: (action: KiloHotkeyAction) => string } {
  const initial = loadKiloHotkeySettings();
  const enabled = ref(initial.enabled);
  const refs = Object.fromEntries(KILO_HOTKEY_ACTIONS.map(a => [a, ref(initial.keys[a])])) as Record<KiloHotkeyAction, Ref<string>>;
  const persist = () => save({
    enabled: enabled.value,
    keys: Object.fromEntries(KILO_HOTKEY_ACTIONS.map(a => [a, refs[a].value])) as Record<KiloHotkeyAction, string>,
  });

  function toggleEnabled() {
    enabled.value = !enabled.value;
    persist();
  }
  function setKey(action: string, e: { key: string; code: string }): HotkeyValidation {
    const target = refs[action as KiloHotkeyAction];
    if (!target) return { ok: false, reason: 'reserved' };
    const others = KILO_HOTKEY_ACTIONS.filter(a => a !== action).map(a => refs[a].value);
    const result = validateHotkey(e, others);
    if (result.ok) { target.value = result.key; persist(); }
    return result;
  }
  function reset() {
    enabled.value = KILO_DEFAULT_HOTKEYS.enabled;
    for (const a of KILO_HOTKEY_ACTIONS) refs[a].value = KILO_DEFAULT_HOTKEYS.keys[a];
    persist();
  }
  const actions = KILO_HOTKEY_ACTIONS.map(id => ({ id, key: refs[id] }));
  return { actions, enabled, toggleEnabled, setKey, reset, keyOf: (a) => refs[a].value };
}
