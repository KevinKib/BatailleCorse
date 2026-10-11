import { toRef } from 'vue';
import { useSettingsStore } from '../state/Settings.store';
import { validateHotkey, type HotkeyPanelApi } from './useHotkeySettings';

const DEFAULT_SEND = 'q';
const DEFAULT_SLAP = 'd';

// Send and Slap shortcuts of the Bataille Corse table. The keys live on the settings store, which
// already persists them (`bc_sendKey`, `bc_slapKey`); there is no master switch for this game.
export function useBatailleCorseHotkeySettings(): HotkeyPanelApi {
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
    send.value = DEFAULT_SEND;
    slap.value = DEFAULT_SLAP;
  }
  return { actions: [{ id: 'send', key: send }, { id: 'slap', key: slap }], setKey, reset };
}
