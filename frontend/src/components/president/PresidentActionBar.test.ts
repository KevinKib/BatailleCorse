import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentActionBar from './PresidentActionBar.vue';

function mountBar(props: Record<string, unknown> = {}) {
  return mount(PresidentActionBar, {
    props: {
      phaseText: 'Your turn', primaryLabel: 'Play', canPrimary: false, canPass: true, hasSelection: false,
      hint: 'Tap the cards you want to play.', notice: '', ...props,
    },
  });
}

describe('PresidentActionBar', () => {
  it('showsThePhaseTextAndTheHintLine', () => {
    const wrapper = mountBar();
    expect(wrapper.get('[data-test="phase-text"]').text()).toBe('Your turn');
    expect(wrapper.get('[data-test="play-hint"]').text()).toBe('Tap the cards you want to play.');
  });

  it('givenAnIllegalSelection_thenPlayIsDisabledAndTheHintIsLinkedToIt', () => {
    const wrapper = mountBar();
    const play = wrapper.get('[data-test="play"]');
    expect(play.attributes('disabled')).toBeDefined();
    expect(play.attributes('aria-describedby')).toBe(wrapper.get('[data-test="play-hint"]').attributes('id'));
  });

  it('givenALegalSelection_thenPlayIsEnabledAndEmitsPlay', async () => {
    const wrapper = mountBar({ canPrimary: true, hint: '', hasSelection: true });
    const play = wrapper.get('[data-test="play"]');
    expect(play.attributes('disabled')).toBeUndefined();
    await play.trigger('click');
    expect(wrapper.emitted('primary')).toHaveLength(1);
  });

  it('whenPassIsPressed_thenItEmitsPass', async () => {
    const wrapper = mountBar();
    await wrapper.get('[data-test="pass"]').trigger('click');
    expect(wrapper.emitted('pass')).toHaveLength(1);
  });

  it('givenPassIsNotOffered_thenThePassButtonIsDisabled', () => {
    expect(mountBar({ canPass: false }).get('[data-test="pass"]').attributes('disabled')).toBeDefined();
  });

  it('givenNoSelection_thenClearIsHiddenElseItEmitsClear', async () => {
    expect(mountBar().find('[data-test="clear"]').exists()).toBe(false);
    const wrapper = mountBar({ hasSelection: true });
    await wrapper.get('[data-test="clear"]').trigger('click');
    expect(wrapper.emitted('clear')).toHaveLength(1);
  });

  it('givenACompletionOnOffer_thenTheCompleteButtonShowsAndEmitsComplete', async () => {
    expect(mountBar().find('[data-test="complete"]').exists()).toBe(false);
    const wrapper = mountBar({ canComplete: true, canPass: false });
    const button = wrapper.get('[data-test="complete"]');
    expect(button.text()).toBe('Complete the four');
    await button.trigger('click');
    expect(wrapper.emitted('complete')).toHaveLength(1);
  });

  it('givenARefusalNotice_thenItIsAnnounced', () => {
    const wrapper = mountBar({ notice: 'That play was refused. Check your cards.' });
    expect(wrapper.get('[data-test="notice"]').attributes('role')).toBe('alert');
  });
});
