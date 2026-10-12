import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import GameTimer from './GameTimer.vue';

describe('GameTimer', () => {
  it('shows the time with the clock icon, pinned to the corner by default', () => {
    const wrapper = mount(GameTimer, { props: { time: '01:05' } });
    expect(wrapper.get('[data-cy="game-timer"]').text()).toBe('01:05');
    expect(wrapper.find('.pi-clock').exists()).toBe(true);
    expect(wrapper.get('[data-cy="game-timer"]').classes()).not.toContain('game-timer--inline');
  });

  it('can flow inside a row of the table instead of being pinned', () => {
    const wrapper = mount(GameTimer, { props: { time: '00:00', inline: true } });
    expect(wrapper.get('[data-cy="game-timer"]').classes()).toContain('game-timer--inline');
  });
});
