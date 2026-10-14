import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import KoboSoundToggle from './KoboSoundToggle.vue';

describe('KoboSoundToggle', () => {
  it('isANamedToggleButtonThatSaysItsState', () => {
    const off = mount(KoboSoundToggle, { props: { enabled: false } });
    const button = off.get('button[data-test="sound-toggle"]');
    expect(button.attributes('aria-label')).toBe('Sound effects');
    expect(button.attributes('aria-pressed')).toBe('false');
    expect(button.attributes('title')).toBe('Sound is off');
    const on = mount(KoboSoundToggle, { props: { enabled: true } });
    expect(on.get('button').attributes('aria-pressed')).toBe('true');
    expect(on.get('button').attributes('title')).toBe('Sound is on');
  });

  it('asksToToggleOnClick', async () => {
    const wrapper = mount(KoboSoundToggle, { props: { enabled: false } });
    await wrapper.get('button').trigger('click');
    expect(wrapper.emitted('toggle')).toHaveLength(1);
  });

  it('showsADifferentIconForEachState', () => {
    const off = mount(KoboSoundToggle, { props: { enabled: false } }).get('i').classes();
    const on = mount(KoboSoundToggle, { props: { enabled: true } }).get('i').classes();
    expect(off).not.toEqual(on);
  });
});
