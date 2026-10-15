import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentHand from './PresidentHand.vue';
import { buildPresidentHand } from '../../model/fixtures/president';

const hand = buildPresidentHand(['3', 'CLUB'], ['QUEEN', 'HEART'], ['ACE', 'SPADE'], ['2', 'DIAMOND']);

function mountHand(selected: string[] = []) {
  return mount(PresidentHand, { props: { cards: hand, selected } });
}

describe('PresidentHand', () => {
  it('showsEveryCardAsAToggleButtonWithAName', () => {
    const buttons = mountHand().findAll('button');
    expect(buttons.map(b => b.attributes('aria-label'))).toEqual([
      '3 of clubs', 'Queen of hearts', 'Ace of spades', '2 of diamonds',
    ]);
    buttons.forEach(b => expect(b.attributes('aria-pressed')).toBe('false'));
  });

  it('keepsTheOrderTheServerSent', () => {
    // The server sorts by the strength in force; the client never re-sorts.
    const names = mountHand().findAll('button').map(b => b.attributes('data-card'));
    expect(names).toEqual(hand.map(c => c.name));
  });

  it('givenASelectedCard_thenItsNameAndPressedStateSayIt', () => {
    const wrapper = mountHand(['HEART_QUEEN']);
    const queen = wrapper.get('[data-card="HEART_QUEEN"]');
    expect(queen.attributes('aria-label')).toBe('Queen of hearts, selected');
    expect(queen.attributes('aria-pressed')).toBe('true');
    expect(queen.classes()).toContain('selected');
  });

  it('whenACardIsTapped_thenItsNameIsEmitted', async () => {
    const wrapper = mountHand();
    await wrapper.get('[data-card="SPADE_ACE"]').trigger('click');
    expect(wrapper.emitted('toggle')).toEqual([['SPADE_ACE']]);
  });

  it('givenAnEmptyHand_thenNoButtonIsShown', () => {
    const wrapper = mount(PresidentHand, { props: { cards: [], selected: [] } });
    expect(wrapper.findAll('button')).toHaveLength(0);
  });
});
