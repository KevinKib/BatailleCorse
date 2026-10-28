import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import { createAppRouter } from '../router';
import { createMemoryHistory } from 'vue-router';
import GamePickerView from './GamePickerView.vue';
import { messagesEn } from '../locales/en';

// French is offered in this file only; GamePickerView.test.ts keeps the shipped single-locale list.
vi.mock('../locales', async (original) => ({
  ...(await original<typeof import('../locales')>()),
  SUPPORTED_LOCALES: ['en', 'fr'],
}));

describe('GamePickerView with two languages', () => {
  it('givenTwoLanguages_thenAGlobeMenuIsInThePanelAndOpensEnglishAndFrancais', async () => {
    const router = createAppRouter(createMemoryHistory());
    await router.push('/');
    await router.isReady();
    const wrapper = mount(GamePickerView, { global: { plugins: [router, PrimeVue] } });
    const panel = wrapper.get('.title-panel');
    const globe = panel.get('[data-test="language-button"]');
    expect(globe.attributes('aria-label')).toBe(messagesEn.language.label);
    await globe.trigger('click');
    expect(wrapper.findAll('[data-test^="language-option-"]').map(o => o.text().replace(' ✓', ''))).toEqual(['English', 'Français']);
    // The games and the Privacy link are all still there.
    expect(wrapper.findAll('article')).toHaveLength(7);
    expect(wrapper.get('[data-test="privacy-link"]').text()).toBe(messagesEn.gamePicker.privacy);
  });
});
