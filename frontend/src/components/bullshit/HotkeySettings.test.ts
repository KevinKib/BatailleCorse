import { describe, it, expect, beforeEach } from 'vitest';
import { mount } from '@vue/test-utils';
import HotkeySettings from './HotkeySettings.vue';
import { useHotkeySettings } from '../../composables/useHotkeySettings';

function setup() {
  localStorage.clear();
  const settings = useHotkeySettings();
  const wrapper = mount(HotkeySettings, { props: { settings }, attachTo: document.body });
  return { settings, wrapper };
}
const find = (w: ReturnType<typeof setup>['wrapper'], id: string) => w.find(`[data-test="${id}"]`);
const keydown = (key: string, code = 'KeyX') =>
  window.dispatchEvent(new KeyboardEvent('keydown', { key, code, bubbles: true, cancelable: true }));
const keyup = (key: string, code = 'KeyX') =>
  document.body.dispatchEvent(new KeyboardEvent('keyup', { key, code, bubbles: true, cancelable: true }));

describe('HotkeySettings', () => {
  beforeEach(() => { document.body.innerHTML = ''; });

  it('is closed until the gear button is pressed, and says so with aria-expanded', async () => {
    const { wrapper } = setup();
    expect(find(wrapper, 'hotkeys-panel').exists()).toBe(false);
    expect(find(wrapper, 'hotkeys-button').attributes('aria-expanded')).toBe('false');
    await find(wrapper, 'hotkeys-button').trigger('click');
    expect(find(wrapper, 'hotkeys-panel').exists()).toBe(true);
    expect(find(wrapper, 'hotkeys-button').attributes('aria-expanded')).toBe('true');
    wrapper.unmount();
  });

  it('shows the current keys and the master switch', async () => {
    const { wrapper } = setup();
    await find(wrapper, 'hotkeys-button').trigger('click');
    expect(find(wrapper, 'hotkey-discard-key').text()).toBe('D');
    expect(find(wrapper, 'hotkey-call-key').text()).toBe('B');
    expect(find(wrapper, 'hotkeys-enabled').attributes('aria-pressed')).toBe('true');
    await find(wrapper, 'hotkeys-enabled').trigger('click');
    expect(find(wrapper, 'hotkeys-enabled').attributes('aria-pressed')).toBe('false');
    wrapper.unmount();
  });

  it('captures the next key for an action, and swallows its keyup so it triggers nothing', async () => {
    const { settings, wrapper } = setup();
    await find(wrapper, 'hotkeys-button').trigger('click');
    await find(wrapper, 'hotkey-discard-change').trigger('click');
    keydown('x');
    await wrapper.vm.$nextTick();
    expect(settings.discardKey.value).toBe('x');
    let reached = false;
    document.addEventListener('keyup', () => { reached = true; }, { once: true });
    keyup('x');
    expect(reached).toBe(false);
    await wrapper.vm.$nextTick();
    expect(find(wrapper, 'hotkey-discard-key').text()).toBe('X');
    wrapper.unmount();
  });

  it('refuses an invalid key with an explanation and keeps the old one', async () => {
    const { settings, wrapper } = setup();
    await find(wrapper, 'hotkeys-button').trigger('click');
    await find(wrapper, 'hotkey-discard-change').trigger('click');
    keydown('b');
    await wrapper.vm.$nextTick();
    expect(settings.discardKey.value).toBe('d');
    expect(find(wrapper, 'hotkeys-error').text()).not.toBe('');
    wrapper.unmount();
  });

  it('Escape cancels the capture without a complaint', async () => {
    const { settings, wrapper } = setup();
    await find(wrapper, 'hotkeys-button').trigger('click');
    await find(wrapper, 'hotkey-call-change').trigger('click');
    keydown('Escape');
    await wrapper.vm.$nextTick();
    expect(settings.callKey.value).toBe('b');
    expect(find(wrapper, 'hotkeys-error').exists()).toBe(false);
    wrapper.unmount();
  });

  it('resets to the defaults', async () => {
    const { settings, wrapper } = setup();
    settings.setKey('call', { key: 'k', code: 'KeyK' });
    await find(wrapper, 'hotkeys-button').trigger('click');
    await find(wrapper, 'hotkeys-reset').trigger('click');
    expect(settings.callKey.value).toBe('b');
    expect(find(wrapper, 'hotkey-call-key').text()).toBe('B');
    wrapper.unmount();
  });
});
