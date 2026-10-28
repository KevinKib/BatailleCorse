import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import KiloDeMerdeLobbyOptions from './KiloDeMerdeLobbyOptions.vue';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';

const descriptors: OptionDescriptor[] = [
  { key: 'rounds', type: 'CHOICE', values: ['10', '5', '15', '20'], defaultValue: '10', labelKey: 'options.kiloDeMerde.rounds' },
];
const mountOptions = (options: Record<string, string> = { rounds: '10' }, editable = true) =>
  mount(KiloDeMerdeLobbyOptions, { props: { descriptors, options, editable } });

describe('KiloDeMerdeLobbyOptions', () => {
  it('givenTheRoundsOption_thenItsChoicesAreListedInAscendingOrder', () => {
    const wrapper = mountOptions();
    expect(wrapper.get('[data-test="option-rounds"]').text()).toContain('Rounds');
    expect(wrapper.findAll('[data-test="option-rounds"] label').map(l => l.text())).toEqual(['5', '10', '15', '20']);
    expect((wrapper.get('[data-test="option-rounds-10"]').element as HTMLInputElement).checked).toBe(true);
  });

  it('givenTheHostPicksAValue_thenSetOptionIsEmitted', async () => {
    const wrapper = mountOptions();
    await wrapper.get('[data-test="option-rounds-15"]').setValue(true);
    expect(wrapper.emitted('set-option')).toEqual([['rounds', '15']]);
  });

  it('givenAGuest_thenTheChoicesCannotBeEdited', () => {
    const wrapper = mountOptions({ rounds: '10' }, false);
    expect(wrapper.find('[data-test="option-rounds-5"]').exists()).toBe(false);
    expect(wrapper.get('[data-test="option-rounds"]').text()).toContain('10');
  });
});
