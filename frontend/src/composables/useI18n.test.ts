import { describe, it, expect } from 'vitest';
import { useI18n } from './useI18n';
import { messagesEn } from '../locales/en';

describe('useI18n', () => {
  it('givenNoLocale_thenReturnsEnglish', () => {
    expect(useI18n()).toBe(messagesEn);
  });

  it('givenEnLocale_thenReturnsEnglish', () => {
    expect(useI18n('en')).toBe(messagesEn);
  });

  it('givenUnknownLocale_thenFallsBackToEnglish', () => {
    expect(useI18n('zz')).toBe(messagesEn);
  });
});
