import { describe, it, expect, vi, afterEach } from 'vitest';
import { defineComponent, h } from 'vue';
import { mount } from '@vue/test-utils';
import { useBullshitHotkeys } from './useBullshitHotkeys';

function setup(over: Partial<Parameters<typeof useBullshitHotkeys>[0]> = {}) {
  const actions = {
    canDiscard: () => true, canCall: () => true, handSize: () => 4,
    discard: vi.fn(), call: vi.fn(), toggleCard: vi.fn(), ...over,
  };
  const wrapper = mount(defineComponent({
    setup() { useBullshitHotkeys(actions); return () => h('div'); },
  }), { attachTo: document.body });
  return { actions, wrapper };
}
const press = (key: string, init: KeyboardEventInit = {}, target: EventTarget = document.body) =>
  target.dispatchEvent(new KeyboardEvent('keyup', { key, bubbles: true, ...init }));

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

  it('digits toggle the displayed card at that position, 0 being the tenth', () => {
    const { actions, wrapper } = setup({ handSize: () => 10 });
    press('1'); press('9'); press('0');
    expect(actions.toggleCard.mock.calls.map(c => c[0])).toEqual([0, 8, 9]);
    wrapper.unmount();
  });

  it('ignores digits beyond the hand size', () => {
    const { actions, wrapper } = setup({ handSize: () => 2 });
    press('3');
    expect(actions.toggleCard).not.toHaveBeenCalled();
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

  it('stops listening once unmounted', () => {
    const { actions, wrapper } = setup();
    wrapper.unmount();
    press('d');
    expect(actions.discard).not.toHaveBeenCalled();
  });
});
