import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KiloDeMerdePile from './KiloDeMerdePile.vue';

const mountPile = (extra: Record<string, unknown> = {}) =>
  mount(KiloDeMerdePile, { props: { count: 12, canSlap: true, ...extra } });

describe('KiloDeMerdePile', () => {
  it('givenAnyState_thenTheButtonKeepsItsNameAndShowsTheCount', () => {
    for (const extra of [{}, { canSlap: false }, { racing: true }]) {
      const pile = mountPile(extra).get('[data-test="pile"]');
      expect(pile.attributes('aria-label')).toContain('Slap the pile');
      expect(pile.attributes('aria-label')).toContain('12 cards left');
    }
    expect(mountPile().get('[data-test="pile-count"]').text()).toBe('12');
  });

  it('givenTheServerAllowsASlap_thenTheButtonIsEnabledAndSlapsOnClick', async () => {
    const wrapper = mountPile();
    expect(wrapper.get('[data-test="pile"]').attributes('disabled')).toBeUndefined();
    await wrapper.get('[data-test="pile"]').trigger('click');
    expect(wrapper.emitted('slap')).toHaveLength(1);
  });

  it('givenNoSlapAvailable_thenTheButtonIsDisabled', async () => {
    const wrapper = mountPile({ canSlap: false });
    expect(wrapper.get('[data-test="pile"]').attributes('disabled')).toBeDefined();
    await wrapper.get('[data-test="pile"]').trigger('click');
    expect(wrapper.emitted('slap')).toBeUndefined();
  });

  it('givenAnOpenRaceIAmNotIn_thenTheButtonPulses', () => {
    expect(mountPile({ racing: true }).get('[data-test="pile"]').classes()).toContain('pile--racing');
    expect(mountPile().get('[data-test="pile"]').classes()).not.toContain('pile--racing');
  });
});
