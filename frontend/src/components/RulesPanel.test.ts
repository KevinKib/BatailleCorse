import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import RulesPanel from './RulesPanel.vue';
import type { RulesMessages } from '../locales/Messages';

const rules: RulesMessages = {
  toggleLabel: 'Rules',
  panelTitle: 'How to play',
  closeLabel: 'Close',
  sections: [
    { kind: 'text', title: 'Goal', body: ['Empty your hand first.'] },
    {
      kind: 'cycle',
      title: 'The claim advances on its own',
      steps: ['A', '2', '3', '…', 'K'],
      loops: true,
      caption: 'The rank steps forward automatically.',
      note: 'A suit variant exists too.',
    },
    {
      kind: 'branch',
      title: 'Call Bullshit',
      intro: 'The cards flip:',
      outcomes: [
        { tone: 'negative', condition: "Cards don't match", result: 'The bluffer takes the pile.' },
        { tone: 'positive', condition: 'Cards match', result: 'The caller takes the pile.' },
      ],
    },
  ],
};

describe('RulesPanel', () => {
  it('renders the toggle label from the rules prop', () => {
    const wrapper = mount(RulesPanel, { props: { rules } });
    expect(wrapper.get('[data-cy="rules-toggle"]').text()).toContain('Rules');
  });

  it('toggles the panel open on click and closed again', async () => {
    const wrapper = mount(RulesPanel, { props: { rules } });
    const toggle = wrapper.get('[data-cy="rules-toggle"]');
    const displayNone = () => (wrapper.get('[data-cy="rules-panel"]').attributes('style') ?? '').includes('display: none');

    // v-show hides via inline display:none until opened (attribute is dropped when shown).
    expect(displayNone()).toBe(true);
    expect(toggle.attributes('aria-expanded')).toBe('false');

    await toggle.trigger('click');
    expect(displayNone()).toBe(false);
    expect(toggle.attributes('aria-expanded')).toBe('true');

    await wrapper.get('[data-cy="rules-close"]').trigger('click');
    expect(displayNone()).toBe(true);
  });

  it('renders a cycle section as rank chips with a wrap-around loop', () => {
    const wrapper = mount(RulesPanel, { props: { rules } });
    const cycle = wrapper.get('[data-cy="rules-cycle"]');
    expect(cycle.findAll('.value-token--rank')).toHaveLength(5);
    expect(cycle.find('.cycle-loop').exists()).toBe(true);
    expect(cycle.text()).toContain('A suit variant exists too.');
  });

  it('renders a branch section with one row per outcome and tone classes', () => {
    const wrapper = mount(RulesPanel, { props: { rules } });
    const branch = wrapper.get('[data-cy="rules-branch"]');
    expect(branch.findAll('.branch-outcome')).toHaveLength(2);
    expect(branch.find('.branch-outcome--positive').exists()).toBe(true);
    expect(branch.find('.branch-outcome--negative').exists()).toBe(true);
  });
});
