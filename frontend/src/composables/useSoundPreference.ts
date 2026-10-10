import { ref } from 'vue';

export const SOUND_STORAGE_KEY = 'bullshit.sound';

// Storage can be blocked or throw (private window, cleared site data): the sound then simply
// stays on for the session and the choice is not remembered.
export function loadSoundEnabled(): boolean {
  try {
    return localStorage.getItem(SOUND_STORAGE_KEY) !== 'off';
  } catch {
    return true;
  }
}

export function saveSoundEnabled(enabled: boolean): void {
  try {
    localStorage.setItem(SOUND_STORAGE_KEY, enabled ? 'on' : 'off');
  } catch {
    /* not remembered */
  }
}

// Reactive preference for a screen: read once, written on every change.
export function useSoundPreference() {
  const enabled = ref(loadSoundEnabled());
  function toggle() {
    enabled.value = !enabled.value;
    saveSoundEnabled(enabled.value);
  }
  return { enabled, toggle };
}
