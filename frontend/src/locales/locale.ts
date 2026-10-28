import { DEFAULT_LOCALE, SUPPORTED_LOCALES, loadMessages, messages, type Locale } from './index';
import type { Messages } from './Messages';

// Where the player's language choice is remembered.
export const LOCALE_STORAGE_KEY = 'bc_locale';

let active: Locale = DEFAULT_LOCALE;

// Picks the locale from the stored choice, then the browser languages (primary subtag, in the
// browser's order), then English. Only the given supported locales can be chosen.
export function resolveLocale(
  stored: string | null,
  browser: readonly string[],
  supported: readonly Locale[] = SUPPORTED_LOCALES,
): Locale {
  const wanted = (code: string | null): Locale | undefined =>
    supported.find(locale => locale === code?.toLowerCase().split('-')[0]);
  return wanted(stored) ?? browser.map(wanted).find(Boolean) ?? DEFAULT_LOCALE;
}

// The locale of the running app, fixed once at boot.
export function currentLocale(): Locale {
  return active;
}

function readStored(): string | null {
  try {
    return localStorage.getItem(LOCALE_STORAGE_KEY);
  } catch {
    return null;
  }
}

export interface InitLocaleOptions {
  supported?: readonly Locale[];
  loadFrench?: (locale: Locale) => Promise<Messages>;
}

// Resolves and loads the locale before the app mounts. English stays if the bundle fails to load.
export async function initLocale(options: InitLocaleOptions = {}): Promise<void> {
  const browser = typeof navigator !== 'undefined' ? Array.from(navigator.languages ?? []) : [];
  const wanted = resolveLocale(readStored(), browser, options.supported);
  let chosen: Locale = DEFAULT_LOCALE;
  try {
    messages[wanted] = await (options.loadFrench ?? loadMessages)(wanted);
    chosen = wanted;
  } catch {
    chosen = DEFAULT_LOCALE;
  }
  active = chosen;
  document.documentElement.lang = chosen;
}

// Remembers the choice and reloads the page, so the whole UI boots in the new language.
export function setLocale(locale: Locale, reload: () => void = () => window.location.reload()): void {
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // Storage blocked: the choice only lasts until the next visit.
  }
  reload();
}

// Test hook: forces the active locale without loading anything.
export function setCurrentLocaleForTest(locale: Locale): void {
  active = locale;
}
