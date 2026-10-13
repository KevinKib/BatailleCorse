import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import PlayingCard from './PlayingCard.vue';

describe('PlayingCard', () => {
  it('givenNoAlt_thenTheImageIsDecorativeWithAnEmptyAlt', () => {
    const wrapper = mount(PlayingCard, { props: { rank: 'ace', suit: 'spade' } });
    expect(wrapper.get('img').attributes('alt')).toBe('');
  });

  it('givenAnAlt_thenTheImageCarriesIt', () => {
    const wrapper = mount(PlayingCard, { props: { rank: 'ace', suit: 'spade', alt: 'Ace of spades' } });
    expect(wrapper.get('img').attributes('alt')).toBe('Ace of spades');
  });
});
