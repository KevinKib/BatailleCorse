import { messagesEn } from './en';
import type { Messages } from './Messages';

// Every locale the code knows; French is written but not yet offered to players (dark launch).
export type Locale = 'en' | 'fr';
// The locales a player can get today. The integration chunk adds 'fr'.
export const SUPPORTED_LOCALES = ['en'] as const;
// Eagerly loaded bundles. French is loaded on demand (`locales/fr`), so it is absent here.
export const messages: { en: Messages } & Partial<Record<Locale, Messages>> = { en: messagesEn };
export const DEFAULT_LOCALE: Locale = 'en';
