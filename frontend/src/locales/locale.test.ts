import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { SUPPORTED_LOCALES, DEFAULT_LOCALE, messages } from './index';
import { messagesEn } from './en';
import {
  LOCALE_STORAGE_KEY, currentLocale, initLocale, resolveLocale, setLocale, setCurrentLocaleForTest,
} from './locale';

const BOTH = ['en', 'fr'] as const;

describe('locale contract', () => {
  it('givenTheContract_thenOnlyEnglishIsSupportedUntilFrenchLaunches', () => {
    expect([...SUPPORTED_LOCALES]).toEqual(['en']);
    expect(DEFAULT_LOCALE).toBe('en');
  });

  it('givenTheContract_thenTheChoiceIsStoredUnderBcLocale', () => {
    expect(LOCALE_STORAGE_KEY).toBe('bc_locale');
  });
});

describe('resolveLocale', () => {
  it('givenAStoredSupportedLocale_thenItWinsOverTheBrowser', () => {
    expect(resolveLocale('fr', ['en-US'], BOTH)).toBe('fr');
    expect(resolveLocale('en', ['fr-FR'], BOTH)).toBe('en');
  });

  it('givenAnUnsupportedOrGarbageStoredValue_thenFallsBackToTheBrowser', () => {
    expect(resolveLocale('de', ['fr-FR'], BOTH)).toBe('fr');
    expect(resolveLocale('', ['fr'], BOTH)).toBe('fr');
  });

  it('givenNoStoredValue_thenTheFirstSupportedBrowserLanguageWins', () => {
    expect(resolveLocale(null, ['de-DE', 'fr-CA', 'en-US'], BOTH)).toBe('fr');
    expect(resolveLocale(null, ['en-GB', 'fr'], BOTH)).toBe('en');
  });

  it('givenNothingSupported_thenEnglish', () => {
    expect(resolveLocale(null, ['de', 'es'], BOTH)).toBe('en');
    expect(resolveLocale(null, [], BOTH)).toBe('en');
  });

  it('givenTheShippedSupportList_thenFrenchIsNeverChosen', () => {
    expect(resolveLocale('fr', ['fr-FR'])).toBe('en');
    expect(resolveLocale(null, ['fr-FR'])).toBe('en');
  });
});

describe('initLocale and setLocale', () => {
  beforeEach(() => {
    localStorage.clear();
    setCurrentLocaleForTest('en');
    document.documentElement.lang = 'en';
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    setCurrentLocaleForTest('en');
    document.documentElement.lang = 'en';
  });

  it('givenNoBoot_thenTheCurrentLocaleIsEnglish', () => {
    expect(currentLocale()).toBe('en');
  });

  it('givenTheShippedSupportList_whenBrowserIsFrench_thenPlayersStayInEnglish', async () => {
    vi.stubGlobal('navigator', { languages: ['fr-FR'] });
    await initLocale();
    expect(currentLocale()).toBe('en');
    expect(document.documentElement.lang).toBe('en');
  });

  it('givenFrenchSupportedAndSaved_thenLoadsTheFrenchBundleAndSetsHtmlLang', async () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, 'fr');
    await initLocale({ supported: BOTH });
    expect(currentLocale()).toBe('fr');
    expect(document.documentElement.lang).toBe('fr');
    expect(messages.fr).toBeDefined();
  });

  it('givenFrenchSupportedAndABrowserInFrench_thenUsesFrench', async () => {
    vi.stubGlobal('navigator', { languages: ['fr-FR', 'en'] });
    await initLocale({ supported: BOTH });
    expect(currentLocale()).toBe('fr');
  });

  it('givenTheFrenchBundleFailsToLoad_thenEnglishIsKept', async () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, 'fr');
    const loader = vi.fn().mockRejectedValue(new Error('network'));
    await initLocale({ supported: BOTH, loadFrench: loader });
    expect(loader).toHaveBeenCalled();
    expect(currentLocale()).toBe('en');
    expect(document.documentElement.lang).toBe('en');
  });

  it('givenLocalStorageThrows_thenFallsBackToTheBrowserLanguage', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    vi.stubGlobal('navigator', { languages: ['fr'] });
    await initLocale({ supported: BOTH });
    expect(currentLocale()).toBe('fr');
  });

  it('givenAChoice_thenSetLocaleStoresItAndReloads', () => {
    const reload = vi.fn();
    setLocale('fr', reload);
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('fr');
    expect(reload).toHaveBeenCalledOnce();
  });

  it('givenLocalStorageThrowsOnWrite_thenSetLocaleStillReloads', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
    const reload = vi.fn();
    setLocale('fr', reload);
    expect(reload).toHaveBeenCalledOnce();
  });

  it('givenEnglishMessages_thenTheEagerBundleIsTheEnglishOne', () => {
    expect(messages.en).toBe(messagesEn);
  });
});
