import { messagesEn } from '../en';

// 99: the French translation comes in its own chunk. Until then the area re-exports the English texts, so that the
// parity test (same keys, same placeholders) keeps passing. Not in `TRANSLATED_AREAS` of parity.test.ts yet.
export const ninetyNineUi = messagesEn.ninetyNineUi;
export const ninetyNineOptions = messagesEn.options.ninetyNine;
