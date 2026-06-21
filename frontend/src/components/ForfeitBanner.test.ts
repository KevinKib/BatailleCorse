import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import ForfeitBanner from './ForfeitBanner.vue';

describe('ForfeitBanner', () => {
  it('renders the given label', () => {
    const wrapper = mount(ForfeitBanner, { props: { label: 'Player 3 forfeited' } });
    expect(wrapper.get('[data-test="forfeit-banner"]').text()).toBe('Player 3 forfeited');
  });
});
