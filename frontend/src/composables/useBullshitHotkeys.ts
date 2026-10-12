import { useHotkeys } from './useHotkeys';

export const DISCARD_KEY = 'd';
export const CALL_KEY = 'b';

// Keyboard shortcuts of the Bullshit table, on top of the shared useHotkeys. Each one does what
// its button does and nothing when the button is disabled (checked at the time of the key press).
// The keys come from getters so a change made on screen applies to the very next key press.
export interface BullshitHotkeyKeys { enabled: () => boolean; discardKey: () => string; callKey: () => string }
const DEFAULT_KEYS: BullshitHotkeyKeys = { enabled: () => true, discardKey: () => DISCARD_KEY, callKey: () => CALL_KEY };

export function useBullshitHotkeys(actions: {
  canDiscard: () => boolean;
  canCall: () => boolean;
  discard: () => void;
  call: () => void;
}, keys: BullshitHotkeyKeys = DEFAULT_KEYS) {
  useHotkeys(
    () => { if (actions.canDiscard()) actions.discard(); },
    () => { if (actions.canCall()) actions.call(); },
    () => [keys.discardKey()],
    () => [keys.callKey()],
    undefined,
    keys.enabled,
  );
}
