import { describe, it, expect, vi } from 'vitest';
import { nextTick } from 'vue';
import { mount } from '@vue/test-utils';
import BullshitHand from './BullshitHand.vue';
import type Card from '../../model/Card';

const cards: Card[] = [
  { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' },
  { rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' },
  { rank: '2', suit: 'CLUB', name: 'CLUB_2' },
];

describe('BullshitHand', () => {
  it('renders every card as a button, sorted by rank then suit, on a single row', () => {
    const wrapper = mount(BullshitHand, { props: { cards, selected: [] } });
    expect(wrapper.findAll('button.hand-card')).toHaveLength(3);
    expect(wrapper.findAll('.hand-row')).toHaveLength(1);
    expect(wrapper.findAll('button.hand-card').map(b => b.attributes('aria-label')))
      .toEqual(['ace of heart', '2 of club', 'king of spade']);
  });

  it('emits toggle with the clicked card, whatever its displayed position', async () => {
    const wrapper = mount(BullshitHand, { props: { cards, selected: [] } });
    await wrapper.get('[data-test="hand-card-2"]').trigger('click');
    expect(wrapper.emitted('toggle')).toEqual([[cards[1]]]);
  });

  it('keeps the same order when a card is added to the hand', async () => {
    const wrapper = mount(BullshitHand, { props: { cards, selected: [] } });
    await wrapper.setProps({ cards: [{ rank: '2', suit: 'HEART', name: 'HEART_2' }, ...cards] });
    expect(wrapper.findAll('button.hand-card').map(b => b.attributes('aria-label')))
      .toEqual(['ace of heart', '2 of heart', '2 of club', 'king of spade']);
  });

  it('marks selected cards as raised and pressed', () => {
    const wrapper = mount(BullshitHand, { props: { cards, selected: [cards[2]] } });
    expect(wrapper.get('[data-test="hand-card-1"]').classes()).toContain('selected');
    expect(wrapper.get('[data-test="hand-card-1"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('[data-test="hand-card-0"]').attributes('aria-pressed')).toBe('false');
  });

  it('exposes the card width and step as CSS variables', () => {
    const wrapper = mount(BullshitHand, { props: { cards, selected: [] } });
    const style = wrapper.get('.hand').attributes('style') ?? '';
    expect(style).toContain('--card-w');
    expect(style).toContain('--step');
  });

  it('splits a crowded hand into two rows, numbering the cards continuously', async () => {
    const spy = vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(359);
    const ranks = ['ACE', '2', '3', '4', '5', '6', '7', '8', '9', '10', 'JACK', 'QUEEN', 'KING'];
    const big: Card[] = ['HEART', 'CLUB'].flatMap(suit => ranks.map(rank => ({ rank, suit, name: `${suit}_${rank}` })));
    const wrapper = mount(BullshitHand, { props: { cards: big, selected: [] } });
    await nextTick();
    const rows = wrapper.findAll('.hand-row');
    expect(rows).toHaveLength(2);
    expect(rows[0].findAll('button.hand-card')).toHaveLength(13);
    expect(rows[1].findAll('button.hand-card')).toHaveLength(13);
    expect(rows[1].get('button.hand-card').attributes('data-test')).toBe('hand-card-13');
    spy.mockRestore();
  });
});
