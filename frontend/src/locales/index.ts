import { messagesEn } from './en';
import type { Messages } from './Messages';

// Every locale the code knows; French is written but not yet offered to players (dark launch).
export type Locale = 'en' | 'fr';
// The locales a player can get today. The integration chunk adds 'fr'.
export const SUPPORTED_LOCALES: readonly Locale[] = ['en'];
// Eagerly loaded bundles. French is loaded on demand (`locales/fr`), so it is absent until then.
export const messages: { en: Messages } & Partial<Record<Locale, Messages>> = { en: messagesEn };
export const DEFAULT_LOCALE: Locale = 'en';

// English is bundled with the app; French is a separate chunk fetched only when asked for.
export async function loadMessages(locale: Locale): Promise<Messages> {
  if (locale === 'fr') return (await import('./fr')).messagesFr;
  return messagesEn;
}
