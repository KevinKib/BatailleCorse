import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import DisconnectOverlay from './DisconnectOverlay.vue';

describe('DisconnectOverlay', () => {
  it('givenAnOpponentAndACountdown_thenShowsTheEnglishTextsFromTheLocales', () => {
    const wrapper = mount(DisconnectOverlay, { props: { opponentLabel: 'Alice', secondsRemaining: 12 } });
    expect(wrapper.get('.disconnect-title').text()).toBe('Alice disconnected');
    expect(wrapper.get('.disconnect-sub').text()).toBe('Waiting for them to return…');
    expect(wrapper.get('[data-cy="disconnect-countdown"]').text()).toBe('You win in 12s');
  });
});
