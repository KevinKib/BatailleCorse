import { messages, DEFAULT_LOCALE, type Locale } from '../locales';
import { currentLocale } from '../locales/locale';
import type { Messages } from '../locales/Messages';

// Returns the messages of the running locale (fixed at boot by `initLocale()`; English before that).
// An unknown or not yet loaded locale falls back to English.
export function useI18n(locale: string = currentLocale()): Messages {
  return messages[locale as Locale] ?? messages[DEFAULT_LOCALE];
}
