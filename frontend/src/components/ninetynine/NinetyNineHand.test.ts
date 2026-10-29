import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import NinetyNineHand from './NinetyNineHand.vue';
import NinetyNineAceChooser from './NinetyNineAceChooser.vue';
import { buildNinetyNineCard } from '../../model/fixtures/ninetynine';
import type { NinetyNinePlayable } from '../../model/ninetynine/NinetyNineState';

const king = buildNinetyNineCard('KING', 'SPADE');
const seven = buildNinetyNineCard('7', 'DIAMOND');
const ace = buildNinetyNineCard('ACE', 'CLUB');
const playable: NinetyNinePlayable[] = [
  { card: king, safe: true, aceValues: [] },
  { card: seven, safe: false, aceValues: [] },
  { card: ace, safe: true, aceValues: [1, 11] },
];

function mountHand(props: Record<string, unknown> = {}) {
  return mount(NinetyNineHand, { props: { cards: [king, seven, ace], playable, canPlay: true, ...props } });
}

describe('NinetyNineHand', () => {
  it('givenMyTurn_thenASafeCardIsNamedAfterItsEffectAndCanBePlayed', async () => {
    const wrapper = mountHand();
    const card = wrapper.get('[data-card="SPADE_KING"]');
    expect(card.attributes('aria-label')).toBe('King of spades: the total becomes 99');
    expect(card.attributes('disabled')).toBeUndefined();
    await card.trigger('click');
    expect(wrapper.emitted('play')).toEqual([[king]]);
  });

  it('givenMyTurn_thenAnUnsafeCardIsDisabledWithTheReasonInItsNameAndUnderIt', async () => {
    const wrapper = mountHand();
    const card = wrapper.get('[data-card="DIAMOND_7"]');
    expect(card.attributes('disabled')).toBeDefined();
    expect(card.attributes('aria-label')).toBe('7 of diamonds, over 99');
    expect(wrapper.get('[data-test="over-limit-DIAMOND_7"]').text()).toBe('Over 99');
    await card.trigger('click');
    expect(wrapper.emitted('play')).toBeUndefined();
  });

  it('givenAnyCard_thenItsEffectIsRemindedUnderItInAFixedSlot', () => {
    const wrapper = mountHand();
    expect(wrapper.get('[data-test="effect-tag-SPADE_KING"]').text()).toBe('=99');
    expect(wrapper.get('[data-test="effect-tag-DIAMOND_7"]').text()).toBe('+7');
    expect(wrapper.get('[data-test="effect-tag-CLUB_ACE"]').text()).toBe('+1 / +11');
    // The reminder and the over-99 reason live in one slot that exists for every card, so nothing moves when one shows.
    expect(wrapper.findAll('[data-test="card-notes"]')).toHaveLength(3);
  });

  it.each([['4', 'Reverses'], ['9', '+0'], ['10', '−10'], ['JACK', '+10'], ['QUEEN', '+10']])(
    'givenTheRank%s_thenTheReminderIs%s', (rank, text) => {
      const card = buildNinetyNineCard(rank, 'HEART');
      const wrapper = mountHand({ cards: [card], playable: [{ card, safe: true, aceValues: [] }] });
      expect(wrapper.get(`[data-test="effect-tag-HEART_${rank}"]`).text()).toBe(text);
    });

  it('givenASafeCard_thenNoReasonIsShownUnderIt', () => {
    expect(mountHand().find('[data-test="over-limit-SPADE_KING"]').exists()).toBe(false);
  });

  it('givenNotMyTurn_thenEveryCardIsDisabledAndNoCardIsCalledUnsafe', () => {
    const wrapper = mountHand({ canPlay: false });
    for (const name of ['SPADE_KING', 'DIAMOND_7', 'CLUB_ACE']) {
      expect(wrapper.get(`[data-card="${name}"]`).attributes('disabled'), name).toBeDefined();
    }
    expect(wrapper.find('[data-test^="over-limit-"]').exists()).toBe(false);
    expect(wrapper.get('[data-card="DIAMOND_7"]').attributes('aria-label')).toBe('7 of diamonds: adds 7');
  });

  it('givenTheAceAwaitingItsValue_thenOnlyTheAceIsPressedAndOtherCardsHaveNoPressedState', () => {
    const wrapper = mountHand({ aceName: 'CLUB_ACE' });
    expect(wrapper.get('[data-card="CLUB_ACE"]').attributes('aria-pressed')).toBe('true');
    expect(wrapper.get('[data-card="SPADE_KING"]').attributes('aria-pressed')).toBeUndefined();
    expect(wrapper.get('[data-card="DIAMOND_7"]').attributes('aria-pressed')).toBeUndefined();
  });

  it('givenAnAceWithTwoValuesAndNoChoiceOpen_thenItIsNotPressedYet', () => {
    const wrapper = mountHand({ aceName: null });
    expect(wrapper.get('[data-card="CLUB_ACE"]').attributes('aria-pressed')).toBe('false');
    expect(wrapper.get('[data-card="SPADE_KING"]').attributes('aria-pressed')).toBeUndefined();
  });

  it('givenAnAceWithASingleValue_thenItHasNoPressedState', () => {
    const wrapper = mountHand({
      aceName: null,
      playable: [
        { card: king, safe: true, aceValues: [] },
        { card: seven, safe: false, aceValues: [] },
        { card: ace, safe: true, aceValues: [1] },
      ],
    });
    expect(wrapper.get('[data-card="CLUB_ACE"]').attributes('aria-pressed')).toBeUndefined();
  });

  it('givenTheCardsOfTheHand_thenTheyKeepTheServerOrder', () => {
    const names = mountHand().findAll('[data-card]').map(b => b.attributes('data-card'));
    expect(names).toEqual(['SPADE_KING', 'DIAMOND_7', 'CLUB_ACE']);
  });

  it('givenAHandOfThreeCardsBeforeTheDealArrives_thenItKeepsItsHeight', () => {
    expect(mountHand({ cards: [] }).find('[data-test="hand"]').exists()).toBe(true);
  });
});

describe('NinetyNineAceChooser', () => {
  it('givenTwoValuesAllowed_thenBothButtonsAreEnabledAndTheValueIsEmitted', async () => {
    const wrapper = mount(NinetyNineAceChooser, { props: { values: [1, 11] } });
    expect(wrapper.get('[role="group"]').attributes('aria-label')).toBe('Choose the value of the Ace');
    expect(wrapper.get('[data-test="ace-1"]').text()).toBe('+1');
    expect(wrapper.get('[data-test="ace-11"]').text()).toBe('+11');
    await wrapper.get('[data-test="ace-11"]').trigger('click');
    expect(wrapper.emitted('choose')).toEqual([[11]]);
  });

  it('givenOnlyOneValueAllowed_thenTheOtherIsDisabledAndStruckThrough', () => {
    const wrapper = mount(NinetyNineAceChooser, { props: { values: [1] } });
    expect(wrapper.get('[data-test="ace-11"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-test="ace-11"]').classes()).toContain('ace-button--off');
    expect(wrapper.get('[data-test="ace-1"]').attributes('disabled')).toBeUndefined();
  });

  it('givenEscape_thenItAsksToCancel', async () => {
    const wrapper = mount(NinetyNineAceChooser, { props: { values: [1, 11] } });
    await wrapper.get('[role="group"]').trigger('keydown', { key: 'Escape' });
    expect(wrapper.emitted('cancel')).toHaveLength(1);
  });

  it('givenTheCancelButton_thenItAsksToCancel', async () => {
    const wrapper = mount(NinetyNineAceChooser, { props: { values: [1, 11] } });
    await wrapper.get('[data-test="ace-cancel"]').trigger('click');
    expect(wrapper.emitted('cancel')).toHaveLength(1);
  });
});
