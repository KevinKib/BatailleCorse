import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import PresidentLobbyOptions from './PresidentLobbyOptions.vue';
import { CLASSIC_PRESIDENT_OPTIONS } from '../../model/fixtures/president';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';

const VALUES: Record<string, string[]> = {
  afterPass: ['reenter', 'out'],
  exchange: ['two', 'one', 'scaled', 'none'],
  giveBack: ['free', 'lowest'],
  nextLead: ['scum', 'president'],
  scoring: ['rank', 'titles', 'zeroSum'],
  length: ['rounds-5', 'rounds-3', 'rounds-10', 'points-11', 'points-21', 'endless'],
  mustPlay: ['off', 'on'],
  reducedCut: ['off', 'on'],
};
const descriptors: OptionDescriptor[] = Object.entries(VALUES).map(([key, values]) => ({
  key, type: 'CHOICE', values, defaultValue: CLASSIC_PRESIDENT_OPTIONS[key], labelKey: `options.president.${key}`,
}));

function mountOptions(options: Record<string, string> = { ...CLASSIC_PRESIDENT_OPTIONS }, editable = true, extra = descriptors) {
  return mount(PresidentLobbyOptions, { props: { descriptors: extra, options, editable } });
}

describe('PresidentLobbyOptions', () => {
  it('givenTheDefaults_thenTheClassicCardIsShown', () => {
    const wrapper = mountOptions();
    expect(wrapper.get('[data-test="preset-title"]').text()).toBe('Classic');
    expect(wrapper.find('[data-test="preset-reset"]').exists()).toBe(false);
  });

  it('givenAChangedOption_thenTheCardSaysCustomAndTheHostCanGoBackToClassic', async () => {
    const wrapper = mountOptions({ ...CLASSIC_PRESIDENT_OPTIONS, afterPass: 'out', scoring: 'titles' });
    expect(wrapper.get('[data-test="preset-title"]').text()).toBe('Custom');
    await wrapper.get('[data-test="preset-reset"]').trigger('click');
    expect(wrapper.emitted('set-option')).toEqual([['afterPass', 'reenter'], ['scoring', 'rank']]);
  });

  it('givenNoValueForAnOption_thenItsDefaultCounts', () => {
    expect(mountOptions({}).get('[data-test="preset-title"]').text()).toBe('Classic');
  });

  it('keepsTheOptionsBehindCustomiseUntilOpened', async () => {
    const wrapper = mountOptions();
    const toggle = wrapper.get('[data-test="customise"]');
    expect(toggle.attributes('aria-expanded')).toBe('false');
    expect(wrapper.find('[data-test="game-options"]').exists()).toBe(false);
    await toggle.trigger('click');
    expect(toggle.attributes('aria-expanded')).toBe('true');
    for (const key of Object.keys(VALUES)) expect(wrapper.find(`[data-test="option-${key}"]`).exists(), key).toBe(true);
  });

  it('givenTheHostPicksAValue_thenSetOptionIsEmitted', async () => {
    const wrapper = mountOptions();
    await wrapper.get('[data-test="customise"]').trigger('click');
    await wrapper.get('[data-test="option-length-endless"]').setValue(true);
    expect(wrapper.emitted('set-option')).toEqual([['length', 'endless']]);
  });

  it('givenTheTrickVariants_thenOnlyCompulsoryPlayHasAGroupInTheCustomisePanel', async () => {
    const wrapper = mountOptions();
    await wrapper.get('[data-test="customise"]').trigger('click');
    // equal play, the king of hearts lead, the forbidden finish, the 2 that closes the trick and the four are fixed rules
    expect(wrapper.find('[data-test="option-twoCuts"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="option-quadClears"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="option-mustPlay"]').text()).toContain('Compulsory play');
  });

  it('givenTheHostPicksCompulsoryPlay_thenSetOptionIsEmittedAndTheCardSaysCustom', async () => {
    const wrapper = mountOptions();
    await wrapper.get('[data-test="customise"]').trigger('click');
    await wrapper.get('[data-test="option-mustPlay-on"]').setValue(true);
    expect(wrapper.emitted('set-option')).toEqual([['mustPlay', 'on']]);
    await wrapper.setProps({ options: { ...CLASSIC_PRESIDENT_OPTIONS, mustPlay: 'on' } });
    expect(wrapper.get('[data-test="preset-title"]').text()).toBe('Custom');
  });

  it('givenAKeyTheClientHasNoTextFor_thenTheGenericGroupShowsItsRawKeyAndValues', async () => {
    const future: OptionDescriptor = { key: 'suitLock', type: 'CHOICE', values: ['off', 'on'], defaultValue: 'off', labelKey: 'options.president.suitLock' };
    const wrapper = mountOptions({ ...CLASSIC_PRESIDENT_OPTIONS }, true, [...descriptors, future]);
    await wrapper.get('[data-test="customise"]').trigger('click');
    expect(wrapper.get('[data-test="option-suitLock"]').text()).toContain('suitLock');
    expect(wrapper.get('[data-test="option-suitLock-on"]').exists()).toBe(true);
  });

  it('givenAGuest_thenTheOptionsAreReadOnlyAndThereIsNoReset', async () => {
    const wrapper = mountOptions({ ...CLASSIC_PRESIDENT_OPTIONS, afterPass: 'out' }, false);
    expect(wrapper.find('[data-test="preset-reset"]').exists()).toBe(false);
    await wrapper.get('[data-test="customise"]').trigger('click');
    expect(wrapper.findAll('input[type="radio"]')).toHaveLength(0);
    expect(wrapper.get('[data-test="option-afterPass"]').text()).toContain('Out of the trick');
  });

  it('puts the Private game switch at the top of the Customise panel when given a privacy', async () => {
    const set = vi.fn();
    const wrapper = mount(PresidentLobbyOptions, {
      props: { descriptors, options: { ...CLASSIC_PRESIDENT_OPTIONS }, editable: true, privacy: { value: true, editable: true, set } },
    });
    expect(wrapper.find('[data-test="private-switch"]').exists()).toBe(false);
    await wrapper.get('[data-test="customise"]').trigger('click');
    const panel = wrapper.get('#president-all-options');
    expect(panel.element.firstElementChild?.querySelector('[data-test="private-switch"]')).not.toBeNull();
    expect(panel.get('[data-test="private-switch"]').attributes('aria-checked')).toBe('true');
    await panel.get('[data-test="private-switch"]').trigger('click');
    expect(set).toHaveBeenCalledWith(false);
  });
});
