import { describe, it, expect } from 'vitest';
import { SUPPORTED_LOCALES, DEFAULT_LOCALE } from './index';
import { LOCALE_STORAGE_KEY, currentLocale, initLocale, resolveLocale, setLocale } from './locale';

describe('locale contract', () => {
  it('givenTheContract_thenOnlyEnglishIsSupportedUntilFrenchLaunches', () => {
    expect([...SUPPORTED_LOCALES]).toEqual(['en']);
    expect(DEFAULT_LOCALE).toBe('en');
  });

  it('givenTheContract_thenTheChoiceIsStoredUnderBcLocale', () => {
    expect(LOCALE_STORAGE_KEY).toBe('bc_locale');
  });

  it('givenAnyStoredValueOrBrowserLanguages_thenResolveLocaleReturnsEnglish', () => {
    expect(resolveLocale(null, [])).toBe('en');
    expect(resolveLocale('fr', ['fr-FR'])).toBe('en');
  });

  it('givenNoBoot_thenTheCurrentLocaleIsEnglish', async () => {
    await initLocale();
    setLocale('fr');
    expect(currentLocale()).toBe('en');
  });
});
