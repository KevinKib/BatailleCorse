import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentHand from './PresidentHand.vue';
import { buildPresidentHand } from '../../model/fixtures/president';

const hand = buildPresidentHand(['3', 'CLUB'], ['QUEEN', 'HEART'], ['ACE', 'SPADE'], ['2', 'DIAMOND']);

function mountHand(selected: string[] = [], received: string[] = []) {
  return mount(PresidentHand, { props: { cards: hand, selected, received } });
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

  it('givenReceivedCards_thenTheyAreTaggedInTextAndInTheirName', () => {
    const wrapper = mountHand([], ['SPADE_ACE']);
    const ace = wrapper.get('[data-card="SPADE_ACE"]');
    expect(ace.attributes('data-received')).toBe('true');
    expect(ace.attributes('aria-label')).toBe('Ace of spades, received');
    expect(ace.get('[data-test="received-tag"]').text()).toBe('New');
    expect(wrapper.findAll('[data-test="received-tag"]')).toHaveLength(1);
    expect(wrapper.get('[data-card="CLUB_3"]').attributes('data-received')).toBeUndefined();
  });

  it('aReceivedCardThatIsSelectedSaysBoth', () => {
    const wrapper = mountHand(['SPADE_ACE'], ['SPADE_ACE']);
    expect(wrapper.get('[data-card="SPADE_ACE"]').attributes('aria-label')).toBe('Ace of spades, selected, received');
  });
  it('givenCardsLeaveTheHand_thenItKeepsTheHeightOfTheFullHand_soTheTableAboveNeverMoves', async () => {
    const wrapper = mountHand();
    const full = (wrapper.get('[data-test="hand"]').element as HTMLElement).style.minHeight;
    expect(full).toContain('calc(');
    await wrapper.setProps({ cards: hand.slice(0, 2) });
    expect((wrapper.get('[data-test="hand"]').element as HTMLElement).style.minHeight).toBe(full);
  });

  it('givenACardStillFlyingIn_thenItsPlaceIsKeptButItIsHiddenUntilItLands', async () => {
    const wrapper = mount(PresidentHand, { props: { cards: hand, selected: [], received: [], landing: ['HEART_QUEEN'] } });
    expect(wrapper.get('[data-card="HEART_QUEEN"]').classes()).toContain('landing');
    expect(wrapper.get('[data-card="CLUB_3"]').classes()).not.toContain('landing');
    await wrapper.setProps({ landing: [] });
    expect(wrapper.get('[data-card="HEART_QUEEN"]').classes()).not.toContain('landing');
  });
});
