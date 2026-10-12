import { describe, it, expect, vi, afterEach } from 'vitest';
import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { useBullshitHotkeys } from './useBullshitHotkeys';

function setup(over: Partial<Parameters<typeof useBullshitHotkeys>[0]> = {}, keys?: Parameters<typeof useBullshitHotkeys>[1]) {
  const actions = {
    canDiscard: () => true, canCall: () => true,
    discard: vi.fn(), call: vi.fn(), ...over,
  };
  const wrapper = mount(defineComponent({
    setup() { useBullshitHotkeys(actions, keys); return () => h('div'); },
  }), { attachTo: document.body });
  return { actions, wrapper };
}
// QWERTY by default: digits come from the Digit row, letters from their Key code.
const press = (key: string, init: KeyboardEventInit = {}, target: EventTarget = document.body) =>
  target.dispatchEvent(new KeyboardEvent('keyup', {
    key, code: /^\d$/.test(key) ? `Digit${key}` : `Key${key.toUpperCase()}`, bubbles: true, ...init,
  }));

describe('useBullshitHotkeys', () => {
  afterEach(() => { document.body.innerHTML = ''; });

  it('D discards and B calls Bullshit, whatever the case', () => {
    const { actions, wrapper } = setup();
    press('d'); press('B');
    expect(actions.discard).toHaveBeenCalledTimes(1);
    expect(actions.call).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('does nothing when the matching button would be disabled', () => {
    const { actions, wrapper } = setup({ canDiscard: () => false, canCall: () => false });
    press('d'); press('b');
    expect(actions.discard).not.toHaveBeenCalled();
    expect(actions.call).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('has no card shortcut: digits, on any layout, do nothing', () => {
    const { actions, wrapper } = setup();
    expect(() => {
      press('1'); press('0'); press('&', { code: 'Digit1' }); press('7', { code: 'Numpad7' });
    }).not.toThrow();
    expect(actions.discard).not.toHaveBeenCalled();
    expect(actions.call).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('ignores browser shortcuts and typing in a field', () => {
    const { actions, wrapper } = setup();
    press('d', { ctrlKey: true });
    const input = document.createElement('input');
    document.body.appendChild(input);
    press('d', {}, input);
    expect(actions.discard).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('follows remapped keys and no longer answers to the old ones', () => {
    const { actions, wrapper } = setup({}, { enabled: () => true, discardKey: () => 'x', callKey: () => 'k' });
    press('x'); press('K'); press('d'); press('b');
    expect(actions.discard).toHaveBeenCalledTimes(1);
    expect(actions.call).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('reacts to a change of keys made after mounting', () => {
    let discard = 'd';
    const { actions, wrapper } = setup({}, { enabled: () => true, discardKey: () => discard, callKey: () => 'b' });
    discard = 'z';
    press('d'); press('z');
    expect(actions.discard).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('turns every shortcut off when disabled', () => {
    const { actions, wrapper } = setup({}, { enabled: () => false, discardKey: () => 'd', callKey: () => 'b' });
    press('d'); press('b');
    expect(actions.discard).not.toHaveBeenCalled();
    expect(actions.call).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('stops listening once unmounted', () => {
    const { actions, wrapper } = setup();
    wrapper.unmount();
    press('d');
    expect(actions.discard).not.toHaveBeenCalled();
  });
});
