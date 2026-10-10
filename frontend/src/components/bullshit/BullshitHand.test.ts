import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import BullshitHand from './BullshitHand.vue';
import type Card from '../../model/Card';

const cards: Card[] = [
  { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' },
  { rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' },
  { rank: '2', suit: 'CLUB', name: 'CLUB_2' },
];

describe('BullshitHand', () => {
  it('renders every card as a button on a single row', () => {
    const wrapper = mount(BullshitHand, { props: { cards, selected: [] } });
    expect(wrapper.findAll('button.hand-card')).toHaveLength(3);
    expect(wrapper.get('[data-test="hand-card-0"]').attributes('aria-label')).toBe('ace of heart');
  });

  it('emits toggle with the clicked card', async () => {
    const wrapper = mount(BullshitHand, { props: { cards, selected: [] } });
    await wrapper.get('[data-test="hand-card-1"]').trigger('click');
    expect(wrapper.emitted('toggle')).toEqual([[cards[1]]]);
  });

  it('marks selected cards as raised and pressed', () => {
    const wrapper = mount(BullshitHand, { props: { cards, selected: [cards[2]] } });
    expect(wrapper.get('[data-test="hand-card-2"]').classes()).toContain('selected');
    expect(wrapper.get('[data-test="hand-card-2"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('[data-test="hand-card-0"]').attributes('aria-pressed')).toBe('false');
  });

  it('exposes the card width and step as CSS variables', () => {
    const wrapper = mount(BullshitHand, { props: { cards, selected: [] } });
    const style = wrapper.get('.hand').attributes('style') ?? '';
    expect(style).toContain('--card-w');
    expect(style).toContain('--step');
  });
});
