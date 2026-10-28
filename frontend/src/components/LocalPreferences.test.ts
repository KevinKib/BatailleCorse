import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import PrimeVue from 'primevue/config';
import LocalPreferences from './LocalPreferences.vue';
import { findGame, type GameDefinition, type GameKey } from '../model/games';
import { messagesEn } from '../locales/en';

// These tests cover the single-language UI; the two-language UI is in the *.language.test.ts files.
vi.mock('../locales', async (original) => ({
  ...(await original<typeof import('../locales')>()),
  SUPPORTED_LOCALES: ['en'],
}));

function mountFor(key: GameKey) {
  return mount(LocalPreferences, { props: { game: findGame(key)! }, global: { plugins: [PrimeVue] }, attachTo: document.body });
}

describe('LocalPreferences', () => {
  beforeEach(() => { document.body.innerHTML = ''; localStorage.clear(); setActivePinia(createPinia()); });

  it('givenBullshit_thenTheGearOpensThePanelWithDiscardAndCall', async () => {
    const wrapper = mountFor('bullshit');
    await wrapper.get('[data-test="hotkeys-button"]').trigger('click');
    expect(wrapper.get('[data-test="hotkey-discard-key"]').text()).toBe('D');
    expect(wrapper.get('[data-test="hotkey-call-key"]').text()).toBe('B');
    expect(wrapper.find('.note').exists()).toBe(false);
    expect(messagesEn.hotkeys).not.toHaveProperty('bullshitNote');
    wrapper.unmount();
  });

  it('givenBatailleCorse_thenThePanelOffersSendAndSlapOnItsExistingKeys', async () => {
    localStorage.setItem('bc_sendKey', 'w');
    setActivePinia(createPinia());
    const wrapper = mountFor('bataillecorse');
    await wrapper.get('[data-test="hotkeys-button"]').trigger('click');
    expect(wrapper.get('[data-test="hotkey-send-key"]').text()).toBe('W');
    expect(wrapper.get('[data-test="hotkey-slap-key"]').text()).toBe('D');
    expect(wrapper.find('[data-test="hotkey-discard-key"]').exists()).toBe(false);
    wrapper.unmount();
  });

  it('givenAGameWithoutHotkeys_thenNoGearAndNoPanelAreRendered', () => {
    const quiet: GameDefinition = { ...findGame('bullshit')!, key: 'kobo', hotkeys: undefined };
    const wrapper = mount(LocalPreferences, { props: { game: quiet }, global: { plugins: [PrimeVue] } });
    expect(wrapper.find('[data-test="hotkeys-button"]').exists()).toBe(false);
    expect(wrapper.find('.local-preferences').exists()).toBe(false);
    expect(wrapper.text()).toBe('');
  });

  it('givenAKeyChangedOnBullshit_thenItIsStoredUnderBullshitsOwnKeyOnly', async () => {
    const wrapper = mountFor('bullshit');
    await wrapper.get('[data-test="hotkeys-button"]').trigger('click');
    await wrapper.get('[data-test="hotkey-discard-change"]').trigger('click');
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'x', code: 'KeyX', bubbles: true, cancelable: true }));
    await wrapper.vm.$nextTick();
    expect(JSON.parse(localStorage.getItem('bullshit.hotkeys')!).discard).toBe('x');
    expect(localStorage.getItem('bc_slapKey')).toBeNull();
    wrapper.unmount();
  });
});
