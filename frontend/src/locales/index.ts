import { messagesEn } from './en';

export const messages = { en: messagesEn } as const;
export type Locale = keyof typeof messages;
export const DEFAULT_LOCALE: Locale = 'en';
