import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KiloDeMerdeHand from './KiloDeMerdeHand.vue';
import { kiloDeMerdeStepState } from '../../model/kilodemerde/fixtures';

const cards = kiloDeMerdeStepState.hand;
const mountHand = (extra: Record<string, unknown> = {}) =>
  mount(KiloDeMerdeHand, { props: { cards, myChoice: null, canPass: true, received: null, ...extra } });

describe('KiloDeMerdeHand', () => {
  it('givenFourCards_thenEachIsAButtonNamedByItsPass', () => {
    const wrapper = mountHand();
    const buttons = wrapper.findAll('[data-test="hand"] button');
    expect(buttons).toHaveLength(4);
    expect(buttons[0].attributes('aria-label')).toBe('Pass Jack of clubs');
    expect(buttons.every(b => b.attributes('disabled') === undefined)).toBe(true);
  });

  it('givenATap_thenThePassIsEmittedWithTheCard', async () => {
    const wrapper = mountHand();
    await wrapper.get('[data-card="HEART_JACK"]').trigger('click');
    expect(wrapper.emitted('pass')).toEqual([[cards[1]]]);
  });

  it('givenIPassedACard_thenItSaysPassingAndEveryCardIsDisabled', async () => {
    const wrapper = mountHand({ myChoice: cards[2] });
    expect(wrapper.get('[data-card="DIAMOND_QUEEN"]').attributes('aria-label')).toBe('Queen of diamonds, passed');
    expect(wrapper.get('[data-test="passing"]').text()).toBe('Passing…');
    expect(wrapper.findAll('[data-test="hand"] button').every(b => b.attributes('disabled') !== undefined)).toBe(true);
    await wrapper.get('[data-card="HEART_JACK"]').trigger('click');
    expect(wrapper.emitted('pass')).toBeUndefined();
  });

  it('givenTheServerAllowsNoPass_thenTheCardsAreDisabled', () => {
    const wrapper = mountHand({ canPass: false });
    expect(wrapper.findAll('[data-test="hand"] button').every(b => b.attributes('disabled') !== undefined)).toBe(true);
    expect(wrapper.find('[data-test="passing"]').exists()).toBe(false);
  });

  it('givenAReceivedCard_thenItGlowsAndNamesTheGiver', () => {
    const wrapper = mountHand({ received: { card: cards[1], fromName: 'Alice' } });
    expect(wrapper.get('[data-card="HEART_JACK"]').classes()).toContain('hand-card--received');
    expect(wrapper.get('[data-test="received"]').text()).toBe('from Alice');
    expect(wrapper.get('[data-card="CLUB_JACK"]').classes()).not.toContain('hand-card--received');
  });
});
