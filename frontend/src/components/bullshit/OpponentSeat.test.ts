import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import OpponentSeat from './OpponentSeat.vue';

describe('OpponentSeat', () => {
  it('renders the label and hand-count chip', () => {
    const wrapper = mount(OpponentSeat, { props: { label: 'Player 2', handCount: 4, active: false } });
    expect(wrapper.text()).toContain('Player 2');
    expect(wrapper.get('[data-test="seat-count"]').text()).toContain('4');
  });

  it('applies the active class only when active', () => {
    const inactive = mount(OpponentSeat, { props: { label: 'Player 2', handCount: 4, active: false } });
    expect(inactive.get('[data-test="seat-label"]').classes()).not.toContain('seat-label--active');

    const activeSeat = mount(OpponentSeat, { props: { label: 'Player 2', handCount: 4, active: true } });
    expect(activeSeat.get('[data-test="seat-label"]').classes()).toContain('seat-label--active');
  });

  it('shows the disconnect badge with countdown only when disconnected', () => {
    const connected = mount(OpponentSeat, { props: { label: 'Player 2', handCount: 4, active: false } });
    expect(connected.find('[data-test="seat-disconnect-badge"]').exists()).toBe(false);

    const disconnected = mount(OpponentSeat, {
      props: { label: 'Player 2', handCount: 4, active: false, disconnected: true, secondsRemaining: 30 },
    });
    const badge = disconnected.get('[data-test="seat-disconnect-badge"]');
    expect(badge.text()).toContain('30');
    expect(disconnected.get('.opponent-seat').classes()).toContain('opponent-seat--disconnected');
  });
});
