import { describe, it, expect, vi } from 'vitest';
import { mount } from '@vue/test-utils';
import NinetyNineLobbyOptions from './NinetyNineLobbyOptions.vue';
import { DEFAULT_NINETY_NINE_OPTIONS } from '../../model/fixtures/ninetynine';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';

const descriptors: OptionDescriptor[] = [
  { key: 'lives', type: 'CHOICE', values: ['3', '2', '4'], defaultValue: '3', labelKey: 'options.ninetyNine.lives' },
  { key: 'quick', type: 'CHOICE', values: ['off', 'on'], defaultValue: 'off', labelKey: 'options.ninetyNine.quick' },
];

function mountOptions(options: Record<string, string> = { ...DEFAULT_NINETY_NINE_OPTIONS }, editable = true, extra: Record<string, unknown> = {}) {
  return mount(NinetyNineLobbyOptions, { props: { descriptors, options, editable, ...extra } });
}

describe('NinetyNineLobbyOptions', () => {
  it('givenTheHost_thenLivesAndQuickModeAreRadioGroupsWithTheirTexts', () => {
    const wrapper = mountOptions();
    expect(wrapper.get('[data-test="option-lives"]').text()).toContain('Lives');
    expect(wrapper.get('[data-test="option-quick"]').text()).toContain('Quick mode');
    expect(wrapper.get('[data-test="option-quick"]').text()).toContain('Off');
    expect(wrapper.get('[data-test="option-quick"]').text()).toContain('On');
  });

  it('givenTheLivesChoices_thenTheyAreListedInAscendingOrder', () => {
    const labels = mountOptions().findAll('[data-test="option-lives"] label').map(l => l.text());
    expect(labels).toEqual(['2', '3', '4']);
  });

  it('givenTheCurrentValues_thenTheyAreChecked', () => {
    const wrapper = mountOptions({ lives: '4', quick: 'on' });
    expect((wrapper.get('[data-test="option-lives-4"]').element as HTMLInputElement).checked).toBe(true);
    expect((wrapper.get('[data-test="option-quick-on"]').element as HTMLInputElement).checked).toBe(true);
  });

  it('givenTheHostPicksAValue_thenSetOptionIsEmitted', async () => {
    const wrapper = mountOptions();
    await wrapper.get('[data-test="option-lives-2"]').setValue(true);
    await wrapper.get('[data-test="option-quick-on"]').setValue(true);
    expect(wrapper.emitted('set-option')).toEqual([['lives', '2'], ['quick', 'on']]);
  });

  it('givenTheQuickModeGroup_thenItsHintExplainsTheMode', () => {
    expect(mountOptions().get('[data-test="quick-hint"]').text()).toBe('On: ends at the first player out, the rest ranked by lives left.');
  });

  it('givenAGuest_thenTheOptionsAreReadOnly', () => {
    const wrapper = mountOptions({ lives: '2', quick: 'on' }, false);
    expect(wrapper.findAll('input[type="radio"]')).toHaveLength(0);
    expect(wrapper.get('[data-test="option-lives"]').text()).toContain('2');
    expect(wrapper.get('[data-test="option-quick"]').text()).toContain('On');
  });

  it('givenAPrivacy_thenThePrivateSwitchComesFirst', async () => {
    const set = vi.fn();
    const wrapper = mountOptions({ ...DEFAULT_NINETY_NINE_OPTIONS }, true, { privacy: { value: false, editable: true, set } });
    const first = wrapper.element.firstElementChild!;
    expect(first.querySelector('[data-test="private-switch"]')).not.toBeNull();
    await wrapper.get('[data-test="private-switch"]').trigger('click');
    expect(set).toHaveBeenCalledWith(true);
  });
});
