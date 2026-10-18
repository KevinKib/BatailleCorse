import { describe, expect, it } from 'vitest';
import { mount } from '@vue/test-utils';
import AnimationSpeedButton from './AnimationSpeedButton.vue';

const names = { normal: 'Normal', fast: 'Fast', instant: 'Instant' };
const button = (speed: 'normal' | 'fast' | 'instant' = 'normal', attrs: Record<string, string> = {}) =>
  mount(AnimationSpeedButton, { props: { speed, names, label: 'Animation speed: {speed}' }, attrs });

describe('AnimationSpeedButton', () => {
  it('isARealButtonNamedAfterTheSpeed', () => {
    const b = button().get('[data-test="speed-button"]');
    expect(b.element.tagName).toBe('BUTTON');
    expect(b.attributes('type')).toBe('button');
    expect(b.attributes('aria-label')).toBe('Animation speed: Normal');
    expect(b.text()).toBe('Normal');
  });

  it('litsOneMoreChevronAtEachSpeed', () => {
    expect(button('fast').get('.speed-chevrons').attributes('data-speed')).toBe('fast');
  });

  it('emitsCycleOnATap', async () => {
    const w = button();
    await w.get('[data-test="speed-button"]').trigger('click');
    expect(w.emitted('cycle')).toHaveLength(1);
  });

  it('announcesANewSpeed_notAtFirst', async () => {
    const w = button();
    expect(w.get('[data-test="speed-live"]').text()).toBe('');
    await w.setProps({ speed: 'instant' });
    expect(w.get('[data-test="speed-live"]').text()).toBe('Animation speed: Instant');
  });

  it('putsTheClassItIsGivenOnTheButton', () => {
    expect(button('normal', { class: 'my-place' }).get('[data-test="speed-button"]').classes()).toContain('my-place');
  });
});
