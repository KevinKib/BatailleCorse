import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KiloDeMerdeKiloPopover from './KiloDeMerdeKiloPopover.vue';
import { messages } from '../../locales';
import { messagesFr } from '../../locales/fr';
import { setCurrentLocaleForTest } from '../../locales/locale';
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

  it('givenMyOwnKilos_thenTheTitleSpeaksToMe', () => {
    const title = (props: Record<string, unknown>) =>
      mount(KiloDeMerdeKiloPopover, { props: { name: 'Alice', cards: [], ...props } }).get('.popover-title').text();
    expect(title({ name: 'You', mine: true })).toBe('Your kilos');
    messages.fr = messagesFr;
    setCurrentLocaleForTest('fr');
    try {
      expect(title({ name: 'Vous', mine: true })).toBe('Vos kilos');
      expect(title({ name: 'Bob' })).toBe('Kilos de Bob');
      expect(title({ name: 'Alice' })).toBe('Kilos d’Alice');
      const cards = mount(KiloDeMerdeKiloPopover, { props: { name: 'Bob', cards: [kiloCard('KING', 'SPADE'), kiloCard('JACK', 'CLUB')] } })
        .findAll('[data-test="kilo-card"]').map(c => c.text());
      expect(cards).toEqual(['R♠', 'V♣']);
    } finally {
      setCurrentLocaleForTest('en');
      delete messages.fr;
    }
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
