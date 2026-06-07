import { messages, DEFAULT_LOCALE, type Locale } from '../locales';
import type { Messages } from '../locales/Messages';

// Returns the active locale's messages, defaulting to English. An unknown locale
// falls back to DEFAULT_LOCALE. Locale is constant for now; when a locale picker
// is added later this is the single seam to make reactive.
export function useI18n(locale: string = DEFAULT_LOCALE): Messages {
  return messages[locale as Locale] ?? messages[DEFAULT_LOCALE];
}
