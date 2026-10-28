import type { Locale } from './index';

// Where the player's language choice is remembered.
export const LOCALE_STORAGE_KEY = 'bc_locale';

// Picks the locale from the stored choice, then the browser languages, then English.
// Contract only: English until the locale-detection chunk fills it in.
export function resolveLocale(_stored: string | null, _browser: readonly string[]): Locale {
  return 'en';
}

// The locale of the running app, fixed once at boot.
export function currentLocale(): Locale {
  return 'en';
}

// Resolves and loads the locale before the app mounts. No-op for now.
export async function initLocale(): Promise<void> {}

// Remembers the choice and reloads the page. No-op for now.
export function setLocale(_locale: Locale): void {}
