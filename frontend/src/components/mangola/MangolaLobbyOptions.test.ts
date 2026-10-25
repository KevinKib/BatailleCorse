import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import MangolaLobbyOptions from './MangolaLobbyOptions.vue';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';

const descriptors: OptionDescriptor[] = [
  { key: 'firstPlayer', type: 'CHOICE', values: ['host', 'guest', 'random'], defaultValue: 'random', labelKey: 'options.mangola.firstPlayer' },
  { key: 'direction', type: 'CHOICE', values: ['anticlockwise', 'clockwise'], defaultValue: 'anticlockwise', labelKey: 'options.mangola.direction' },
];
const DEFAULTS = { firstPlayer: 'random', direction: 'anticlockwise' };

function mountOptions(options: Record<string, string> = DEFAULTS, editable = true, extra = descriptors) {
  return mount(MangolaLobbyOptions, { props: { descriptors: extra, options, editable } });
}

describe('MangolaLobbyOptions', () => {
  it('givenTheDefaults_thenFirstPlayerIsShownAndTheDirectionIsHiddenBehindAClosedCustomise', () => {
    const wrapper = mountOptions();
    expect(wrapper.get('[data-test="option-firstPlayer"]').text()).toContain('First player');
    expect(wrapper.find('[data-test="option-direction"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="customise"]').attributes('aria-expanded')).toBe('false');
    expect(wrapper.get('[data-test="customise"]').text()).toContain('Customise');
  });

  it('givenCustomiseIsOpened_thenTheDirectionRowAppearsAndCanBeClosedAgain', async () => {
    const wrapper = mountOptions();
    const toggle = wrapper.get('[data-test="customise"]');
    await toggle.trigger('click');
    expect(toggle.attributes('aria-expanded')).toBe('true');
    const row = wrapper.get('[data-test="option-direction"]');
    expect(row.text()).toContain('Sowing direction');
    expect(row.get('.choice--on').text()).toBe('Anticlockwise');
    await toggle.trigger('click');
    expect(wrapper.find('[data-test="option-direction"]').exists()).toBe(false);
  });

  it('givenTheHostPicksClockwise_thenSetOptionIsEmitted', async () => {
    const wrapper = mountOptions();
    await wrapper.get('[data-test="customise"]').trigger('click');
    await wrapper.get('[data-test="option-direction-clockwise"]').setValue(true);
    expect(wrapper.emitted('set-option')).toEqual([['direction', 'clockwise']]);
  });

  it('givenAGuestAndTheDefaultDirection_thenTheRowStaysHiddenUntilCustomiseIsOpened', async () => {
    const wrapper = mountOptions(DEFAULTS, false);
    expect(wrapper.find('[data-test="option-direction"]').exists()).toBe(false);
    await wrapper.get('[data-test="customise"]').trigger('click');
    expect(wrapper.get('[data-test="option-direction"]').text()).toContain('Anticlockwise');
    expect(wrapper.find('input[type="radio"]').exists()).toBe(false);
  });

  it('givenAGuestAndANonDefaultDirection_thenCustomiseIsOpenAndTheValueIsPlainText', () => {
    const wrapper = mountOptions({ firstPlayer: 'random', direction: 'clockwise' }, false);
    expect(wrapper.get('[data-test="customise"]').attributes('aria-expanded')).toBe('true');
    const row = wrapper.get('[data-test="option-direction"]');
    expect(row.text()).toContain('Sowing direction');
    expect(row.text()).toContain('Clockwise');
    expect(row.find('input').exists()).toBe(false);
  });

  it('givenTheDirectionBecomesNonDefaultWhileClosed_thenCustomiseOpensForWhoeverWatches', async () => {
    const wrapper = mountOptions(DEFAULTS, false);
    expect(wrapper.find('[data-test="option-direction"]').exists()).toBe(false);
    await wrapper.setProps({ options: { firstPlayer: 'random', direction: 'clockwise' } });
    expect(wrapper.get('[data-test="option-direction"]').text()).toContain('Clockwise');
  });

  it('givenAHostWhoReloadsOnANonDefaultDirection_thenCustomiseIsOpenToo', () => {
    const wrapper = mountOptions({ firstPlayer: 'random', direction: 'clockwise' }, true);
    expect(wrapper.get('[data-test="option-direction-clockwise"]').exists()).toBe(true);
  });
});
