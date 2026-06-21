import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import SeatDisconnectBadge from './SeatDisconnectBadge.vue';

describe('SeatDisconnectBadge', () => {
  it('renders the countdown seconds when provided', () => {
    const wrapper = mount(SeatDisconnectBadge, { props: { secondsRemaining: 42 } });
    expect(wrapper.get('[data-test="seat-disconnect-badge"]').text()).toContain('42');
    expect(wrapper.text().toLowerCase()).toContain('reconnecting');
  });

  it('omits the seconds when null', () => {
    const wrapper = mount(SeatDisconnectBadge, { props: { secondsRemaining: null } });
    expect(wrapper.get('[data-test="seat-disconnect-badge"]').text()).not.toMatch(/\d/);
  });
});
