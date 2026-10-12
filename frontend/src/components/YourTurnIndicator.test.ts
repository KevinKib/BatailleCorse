import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import YourTurnIndicator from './YourTurnIndicator.vue';
import { messagesEn } from '../locales/en';

// Shared by the Bataille Corse and Bullshit tables: the same cue, the same words, the same markup.
describe('YourTurnIndicator', () => {
  it('shows the localized "Your turn" text with a decorative dot while active', () => {
    const wrapper = mount(YourTurnIndicator, { props: { active: true } });
    const hint = wrapper.get('[data-test="turn-hint"]');
    expect(hint.text()).toBe(messagesEn.turn.yourTurn);
    expect(hint.text()).toBe('Your turn');
    expect(hint.get('.turn-hint__dot').attributes('aria-hidden')).toBe('true');
  });

  it('shows nothing, but keeps its live region in place, while inactive', () => {
    const wrapper = mount(YourTurnIndicator, { props: { active: false } });
    expect(wrapper.find('[data-test="turn-hint"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="turn-indicator"]').attributes('role')).toBe('status');
    expect(wrapper.get('[data-test="turn-indicator"]').text()).toBe('');
  });

  it('is a polite status region that is always present, so the text is announced once when it appears', async () => {
    const wrapper = mount(YourTurnIndicator, { props: { active: false } });
    const region = wrapper.get('[data-test="turn-indicator"]').element;
    await wrapper.setProps({ active: true });
    await nextTick();
    // The same element received the text: a live region that is added together with its text is not announced.
    expect(wrapper.get('[data-test="turn-indicator"]').element).toBe(region);
    expect(region.getAttribute('role')).toBe('status');
    expect(region.textContent?.trim()).toBe('Your turn');
    // Not read twice: the dot is hidden and the text is not duplicated in a label.
    expect(region.querySelectorAll('[aria-label]').length).toBe(0);
  });

  it('lets the table place it with its own class', () => {
    const wrapper = mount(YourTurnIndicator, { props: { active: true }, attrs: { class: 'slot' } });
    expect(wrapper.get('[data-test="turn-indicator"]').classes()).toContain('slot');
  });
});
