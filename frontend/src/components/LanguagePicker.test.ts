import { describe, it, expect, vi, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import LanguagePicker from './LanguagePicker.vue';
import { messagesEn } from '../locales/en';
import { setLocale } from '../locales/locale';

vi.mock('../locales/locale', async (original) => ({
  ...(await original<typeof import('../locales/locale')>()),
  setLocale: vi.fn(),
}));

const BOTH = ['en', 'fr'] as const;

function mountPicker(variant: 'menu' | 'row', locales: readonly ('en' | 'fr')[] = BOTH) {
  return mount(LanguagePicker, { props: { variant, locales }, attachTo: document.body });
}

describe('LanguagePicker', () => {
  beforeEach(() => { document.body.innerHTML = ''; vi.mocked(setLocale).mockClear(); });

  it('givenASingleLocale_thenNothingIsRendered', () => {
    for (const variant of ['menu', 'row'] as const) {
      const wrapper = mountPicker(variant, ['en']);
      expect(wrapper.find('[data-test="language-button"]').exists()).toBe(false);
      expect(wrapper.find('[data-test="language-row"]').exists()).toBe(false);
      expect(wrapper.text()).toBe('');
    }
  });

  it('givenTheDefaultLocales_thenNothingIsRenderedUntilFrenchIsOffered', () => {
    const wrapper = mount(LanguagePicker, { props: { variant: 'menu' } });
    expect(wrapper.find('[data-test="language-button"]').exists()).toBe(false);
  });

  describe('menu variant', () => {
    it('givenTwoLocales_thenAGlobeButtonWithAnAccessibleNameOpensTheLanguageNames', async () => {
      const wrapper = mountPicker('menu');
      const button = wrapper.get('[data-test="language-button"]');
      expect(button.element.tagName).toBe('BUTTON');
      expect(button.attributes('aria-label')).toBe(messagesEn.language.label);
      expect(button.attributes('aria-expanded')).toBe('false');
      expect(wrapper.find('[data-test="language-menu"]').exists()).toBe(false);

      await button.trigger('click');
      expect(button.attributes('aria-expanded')).toBe('true');
      const options = wrapper.findAll('[data-test^="language-option-"]');
      expect(options.map(o => o.text().replace(' ✓', ''))).toEqual(['English', 'Français']);
      expect(wrapper.get('[data-test="language-menu"]').attributes('aria-label')).toBe(messagesEn.language.label);
      expect(wrapper.get('[data-test="language-option-fr"]').attributes('lang')).toBe('fr');
      expect(wrapper.text()).not.toMatch(/\p{Regional_Indicator}/u);
    });

    it('givenTheCurrentLocale_thenItsOptionIsPressedAndSelectingItOnlyCloses', async () => {
      const wrapper = mountPicker('menu');
      await wrapper.get('[data-test="language-button"]').trigger('click');
      expect(wrapper.get('[data-test="language-option-en"]').attributes('aria-pressed')).toBe('true');
      expect(wrapper.get('[data-test="language-option-fr"]').attributes('aria-pressed')).toBe('false');

      await wrapper.get('[data-test="language-option-en"]').trigger('click');
      expect(setLocale).not.toHaveBeenCalled();
      expect(wrapper.find('[data-test="language-menu"]').exists()).toBe(false);
    });

    it('givenAnotherLocale_thenSelectingItSavesTheChoiceAndReloads', async () => {
      const wrapper = mountPicker('menu');
      await wrapper.get('[data-test="language-button"]').trigger('click');
      await wrapper.get('[data-test="language-option-fr"]').trigger('click');
      expect(setLocale).toHaveBeenCalledTimes(1);
      expect(setLocale).toHaveBeenCalledWith('fr');
    });

    it('givenTheMenuOpen_thenEscapeAndAClickOutsideCloseIt', async () => {
      const wrapper = mountPicker('menu');
      await wrapper.get('[data-test="language-button"]').trigger('click');
      await wrapper.get('[data-test="language-menu"]').trigger('keydown', { key: 'Escape' });
      expect(wrapper.find('[data-test="language-menu"]').exists()).toBe(false);

      await wrapper.get('[data-test="language-button"]').trigger('click');
      document.body.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }));
      await wrapper.vm.$nextTick();
      expect(wrapper.find('[data-test="language-menu"]').exists()).toBe(false);
    });
  });

  describe('row variant', () => {
    it('givenTwoLocales_thenAGroupOfPressedButtonsNamedAfterTheLanguagesIsShownUnderAHeading', () => {
      const wrapper = mountPicker('row');
      const group = wrapper.get('[data-test="language-row"]');
      expect(group.attributes('role')).toBe('group');
      expect(group.attributes('aria-label')).toBe(messagesEn.language.label);
      expect(wrapper.get('[data-test="language-heading"]').text()).toBe(messagesEn.language.label);
      const options = wrapper.findAll('[data-test^="language-option-"]');
      expect(options.map(o => o.text().replace(' ✓', ''))).toEqual(['English', 'Français']);
      expect(options.map(o => o.attributes('aria-pressed'))).toEqual(['true', 'false']);
      expect(wrapper.find('[data-test="language-button"]').exists()).toBe(false);
    });

    it('givenAnotherLocale_thenSelectingItSavesTheChoiceAndReloads', async () => {
      const wrapper = mountPicker('row');
      await wrapper.get('[data-test="language-option-fr"]').trigger('click');
      expect(setLocale).toHaveBeenCalledWith('fr');
    });

    it('givenTheCurrentLocale_thenSelectingItDoesNothing', async () => {
      const wrapper = mountPicker('row');
      await wrapper.get('[data-test="language-option-en"]').trigger('click');
      expect(setLocale).not.toHaveBeenCalled();
    });
  });
});
