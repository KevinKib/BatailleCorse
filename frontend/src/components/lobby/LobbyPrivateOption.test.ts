import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import LobbyPrivateOption from './LobbyPrivateOption.vue';
import { messagesEn } from '../../locales/en';

const ui = messagesEn.lobby;

function mountOption(props: { modelValue: boolean; editable: boolean }) {
  return mount(LobbyPrivateOption, { props });
}

describe('LobbyPrivateOption', () => {
  it('is a labelled switch described by its hint', () => {
    const wrapper = mountOption({ modelValue: false, editable: true });
    const sw = wrapper.get('[data-test="private-switch"]');
    expect(sw.attributes('role')).toBe('switch');
    expect(sw.text()).toContain(ui.privateGame);
    const hint = wrapper.get(`#${sw.attributes('aria-describedby')}`);
    expect(hint.text()).toBe(ui.privateHint);
  });

  it('reflects the value in aria-checked', async () => {
    const wrapper = mountOption({ modelValue: false, editable: true });
    expect(wrapper.get('[data-test="private-switch"]').attributes('aria-checked')).toBe('false');
    await wrapper.setProps({ modelValue: true });
    expect(wrapper.get('[data-test="private-switch"]').attributes('aria-checked')).toBe('true');
  });

  it('emits the opposite value on click for the host', async () => {
    const wrapper = mountOption({ modelValue: false, editable: true });
    await wrapper.get('[data-test="private-switch"]').trigger('click');
    expect(wrapper.emitted('change')).toEqual([[true]]);
    await wrapper.setProps({ modelValue: true });
    await wrapper.get('[data-test="private-switch"]').trigger('click');
    expect(wrapper.emitted('change')).toEqual([[true], [false]]);
  });

  it('is disabled and silent for anybody else', async () => {
    const wrapper = mountOption({ modelValue: true, editable: false });
    const sw = wrapper.get('[data-test="private-switch"]');
    expect((sw.element as HTMLButtonElement).disabled).toBe(true);
    await sw.trigger('click');
    expect(wrapper.emitted('change')).toBeUndefined();
  });
});
