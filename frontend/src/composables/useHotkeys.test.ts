import { describe, it, expect, vi, afterEach } from 'vitest';
import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { useHotkeys } from './useHotkeys';

// Bataille Corse usage: a send key and a slap key (configurable), nothing else.
function setup(sendKeys = ['q'], slapKeys = ['d']) {
  const send = vi.fn(); const slap = vi.fn(); const other = vi.fn();
  const wrapper = mount(defineComponent({
    setup() { useHotkeys(send, slap, () => sendKeys, () => slapKeys, other); return () => h('div'); },
  }), { attachTo: document.body });
  return { send, slap, other, wrapper };
}
const press = (key: string, init: KeyboardEventInit = {}, target: EventTarget = document.body) =>
  target.dispatchEvent(new KeyboardEvent('keyup', { key, code: 'KeyX', bubbles: true, ...init }));

describe('useHotkeys', () => {
  afterEach(() => { document.body.innerHTML = ''; });

  it('calls send on the send key and slap on the slap key, case-insensitively', () => {
    const { send, slap, wrapper } = setup();
    press('q'); press('D');
    expect(send).toHaveBeenCalledTimes(1);
    expect(slap).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('follows rebound keys', () => {
    const { send, slap, wrapper } = setup(['k'], ['l']);
    press('k'); press('l'); press('q');
    expect(send).toHaveBeenCalledTimes(1);
    expect(slap).toHaveBeenCalledTimes(1);
    wrapper.unmount();
  });

  it('hands any other key (with its code) to the optional handler only', () => {
    const { send, slap, other, wrapper } = setup();
    press('x', { code: 'KeyX' });
    expect(other).toHaveBeenCalledWith('x', 'KeyX');
    expect(send).not.toHaveBeenCalled();
    expect(slap).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('ignores Ctrl, Alt and Meta combinations such as Ctrl+D', () => {
    const { slap, wrapper } = setup();
    press('d', { ctrlKey: true }); press('d', { altKey: true }); press('d', { metaKey: true });
    expect(slap).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('ignores typing in an input, a textarea or a select', () => {
    const { send, slap, wrapper } = setup();
    for (const tag of ['input', 'textarea', 'select']) {
      const el = document.createElement(tag);
      document.body.appendChild(el);
      press('q', {}, el); press('d', {}, el);
    }
    expect(send).not.toHaveBeenCalled();
    expect(slap).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('stops listening once unmounted', () => {
    const { send, wrapper } = setup();
    wrapper.unmount();
    press('q');
    expect(send).not.toHaveBeenCalled();
  });
});
