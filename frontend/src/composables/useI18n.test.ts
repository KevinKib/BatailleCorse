import { describe, it, expect, afterEach } from 'vitest';
import { useI18n } from './useI18n';
import { messagesEn } from '../locales/en';
import { messages } from '../locales';
import { setCurrentLocaleForTest } from '../locales/locale';
import type { Messages } from '../locales/Messages';

describe('useI18n', () => {
  afterEach(() => {
    setCurrentLocaleForTest('en');
    delete messages.fr;
  });

  it('givenNoLocale_thenReturnsEnglish', () => {
    expect(useI18n()).toBe(messagesEn);
  });

  it('givenEnLocale_thenReturnsEnglish', () => {
    expect(useI18n('en')).toBe(messagesEn);
  });

  it('givenUnknownLocale_thenFallsBackToEnglish', () => {
    expect(useI18n('zz')).toBe(messagesEn);
  });

  it('givenFrenchIsTheCurrentLocaleAndLoaded_thenReturnsFrench', () => {
    const fake = { ...messagesEn } as Messages;
    messages.fr = fake;
    setCurrentLocaleForTest('fr');
    expect(useI18n()).toBe(fake);
  });

  it('givenFrenchIsCurrentButNotLoaded_thenFallsBackToEnglish', () => {
    setCurrentLocaleForTest('fr');
    expect(useI18n()).toBe(messagesEn);
  });
});
