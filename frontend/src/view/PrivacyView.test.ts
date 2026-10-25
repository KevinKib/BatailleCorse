import { describe, it, expect } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import { createMemoryHistory } from 'vue-router';
import { createAppRouter } from '../router';
import PrivacyView from './PrivacyView.vue';
import { messagesEn } from '../locales/en';

async function mountPrivacy() {
  const router = createAppRouter(createMemoryHistory());
  await router.push('/privacy');
  await router.isReady();
  const wrapper = mount(PrivacyView, { global: { plugins: [router, PrimeVue] } });
  return { router, wrapper };
}

describe('PrivacyView', () => {
  it('givenThePage_thenHasOneH1AndOneH2PerSectionInOrder', async () => {
    const { wrapper } = await mountPrivacy();
    const p = messagesEn.privacy;
    expect(wrapper.findAll('h1')).toHaveLength(1);
    expect(wrapper.get('h1').text()).toBe(p.title);
    expect(wrapper.findAll('h2').map(h => h.text())).toEqual(p.sections.map(s => s.heading));
    expect(wrapper.findAll('section')).toHaveLength(p.sections.length);
  });

  it('givenTheSections_thenEachParagraphOfTheLocaleIsShown', async () => {
    const { wrapper } = await mountPrivacy();
    const text = wrapper.text();
    for (const section of messagesEn.privacy.sections) {
      expect(section.paragraphs.length).toBeGreaterThan(0);
      for (const paragraph of section.paragraphs) expect(text).toContain(paragraph);
    }
  });

  it('givenTheSections_thenTheSixTopicsAreCovered', async () => {
    expect(messagesEn.privacy.sections.map(s => s.id)).toEqual(['visits', 'usage', 'browser', 'memory', 'public', 'other']);
  });

  it('givenTheKeyFacts_thenTheyAreStatedPlainly', async () => {
    const { wrapper } = await mountPrivacy();
    const text = wrapper.text();
    expect(text).toContain('last number of an IPv4 address');
    expect(text).toContain('14 days');
    expect(text).toContain('No cookie');
    expect(text).toContain('bc_playerName');
    expect(text).toContain('animationSpeed');
    expect(text).toContain('only in the memory of the server');
    expect(text).toContain('No accounts');
    expect(text).toContain('No advertising');
    // The one third party: the font is fetched from Google Fonts (frontend/index.html).
    expect(text).toContain('Google Fonts');
  });

  it('givenThePublicGamesList_thenThePageDescribesWhatItShowsAndHowToStayUnlisted', async () => {
    const { wrapper } = await mountPrivacy();
    const section = wrapper.get('[data-test="privacy-public"]').text();
    expect(section).toContain('display name');
    expect(section).toContain('seats are taken');
    expect(section).toContain('Private game');
    expect(section).toContain('is live');
    expect(section).toContain('stores nothing beyond');
    // The "not covered yet" note is gone.
    expect(wrapper.find('[data-test="privacy-not-covered"]').exists()).toBe(false);
    expect(wrapper.text()).not.toContain('not built yet');
  });

  it('givenNoContactAddressYet_thenNoEmailIsShown', async () => {
    const { wrapper } = await mountPrivacy();
    expect(wrapper.text()).not.toContain('@');
    expect(wrapper.find('a[href^="mailto:"]').exists()).toBe(false);
  });

  it('givenTheBackLink_thenItIsALinkToTheGamePicker', async () => {
    const { wrapper, router } = await mountPrivacy();
    const back = wrapper.get('[data-test="privacy-back"]');
    expect(back.element.tagName).toBe('A');
    expect(back.text()).toBe(messagesEn.privacy.back);
    expect(back.attributes('href')).toBe('/');
    await back.trigger('click');
    await flushPromises();
    expect(router.currentRoute.value.fullPath).toBe('/');
  });
});
