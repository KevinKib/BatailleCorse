import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PrimeVue from 'primevue/config';
import KoboMatchHint from './KoboMatchHint.vue';
import { messagesEn } from '../../locales/en';

const ui = messagesEn.koboUi.match;
const mountHint = (props: { hint: boolean; coach: boolean }) =>
  mount(KoboMatchHint, { props, global: { plugins: [PrimeVue] } });

describe('KoboMatchHint', () => {
  it('givenAHintAndNoCue_thenTheOneLineShows', () => {
    const wrapper = mountHint({ hint: true, coach: false });
    expect(wrapper.get('[data-test="match-hint"]').text()).toBe(ui.hint);
    expect(wrapper.find('[data-test="match-coach"]').exists()).toBe(false);
  });

  it('givenTheFirstTimeCue_thenTheNoteReplacesTheLineAndNamesTheTwoGestures', () => {
    const wrapper = mountHint({ hint: true, coach: true });
    const note = wrapper.get('[data-test="match-coach"]');
    expect(note.attributes('role')).toBe('note');
    expect(note.text()).toContain(ui.coachTitle);
    expect(note.text()).toContain(ui.coachText);
    expect(wrapper.find('[data-test="match-hint"]').exists()).toBe(false);
  });

  it('givenGotItIsPressed_thenItEmitsDismiss_andTheButtonIsA44pxTarget', async () => {
    const wrapper = mountHint({ hint: true, coach: true });
    const button = wrapper.get('[data-test="match-coach-dismiss"]');
    expect(button.text()).toBe(ui.gotIt);
    expect(button.classes()).toContain('coach-dismiss');
    await button.trigger('click');
    expect(wrapper.emitted('dismiss')).toHaveLength(1);
  });

  it('givenNoHint_thenNothingShowsEvenWithTheCue', () => {
    const wrapper = mountHint({ hint: false, coach: true });
    expect(wrapper.find('[data-test="match-hint"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="match-coach"]').exists()).toBe(false);
  });
});
