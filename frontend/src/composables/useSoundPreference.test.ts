import { describe, it, expect, beforeEach, vi } from 'vitest';
import { loadSoundEnabled, saveSoundEnabled, SOUND_STORAGE_KEY } from './useSoundPreference';

describe('sound preference', () => {
  beforeEach(() => { localStorage.clear(); vi.restoreAllMocks(); });

  it('is enabled by default', () => {
    expect(loadSoundEnabled()).toBe(true);
  });

  it('remembers a muted choice', () => {
    saveSoundEnabled(false);
    expect(localStorage.getItem(SOUND_STORAGE_KEY)).toBe('off');
    expect(loadSoundEnabled()).toBe(false);
    saveSoundEnabled(true);
    expect(loadSoundEnabled()).toBe(true);
  });

  it('falls back to enabled when storage throws on read', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    expect(loadSoundEnabled()).toBe(true);
  });

  it('does not throw when storage throws on write', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
    expect(() => saveSoundEnabled(false)).not.toThrow();
  });
});
