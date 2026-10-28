import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KiloDeMerdeKiloPopover from './KiloDeMerdeKiloPopover.vue';
import { kiloCard } from '../../model/kilodemerde/fixtures';

describe('KiloDeMerdeKiloPopover', () => {
  it('givenKiloCards_thenTheyAreListedWithTheirTotal', () => {
    const wrapper = mount(KiloDeMerdeKiloPopover, {
      props: { name: 'Alice', cards: [kiloCard('KING', 'SPADE'), kiloCard('9', 'CLUB')] },
    });
    expect(wrapper.get('[data-test="kilo-popover"]').attributes('aria-label')).toBe('Kilos of Alice');
    expect(wrapper.findAll('[data-test="kilo-card"]').map(c => c.text())).toEqual(['K♠', '9♣']);
    expect(wrapper.get('[data-test="kilo-total"]').text()).toBe('Total: 22 kg');
  });

  it('givenNoKiloCards_thenItSaysSo', () => {
    const wrapper = mount(KiloDeMerdeKiloPopover, { props: { name: 'Alice', cards: [] } });
    expect(wrapper.get('[data-test="kilo-none"]').text()).toBe('No kilo cards yet');
  });

  it('givenEscapeOrTheCloseButton_thenItAsksToClose', async () => {
    const wrapper = mount(KiloDeMerdeKiloPopover, { props: { name: 'Alice', cards: [] } });
    await wrapper.get('[data-test="kilo-popover"]').trigger('keydown', { key: 'Escape' });
    await wrapper.get('[data-test="kilo-close"]').trigger('click');
    expect(wrapper.emitted('close')).toHaveLength(2);
  });
});
