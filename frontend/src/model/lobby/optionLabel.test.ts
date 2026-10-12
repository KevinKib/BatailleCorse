import { describe, it, expect } from 'vitest';
import { optionLabel, optionGroupLabel } from './optionLabel';
import { messagesEn } from '../../locales/en';
import type { OptionDescriptor } from './LobbyView';

const claimMode: OptionDescriptor = {
  key: 'claimMode', type: 'CHOICE', values: ['rank', 'suit'], defaultValue: 'rank', labelKey: 'options.bullshit.claimMode',
};
const unknown: OptionDescriptor = {
  key: 'speed', type: 'CHOICE', values: ['fast'], defaultValue: 'fast', labelKey: 'options.somegame.speed',
};

describe('optionLabel', () => {
  it('resolves the text of a value from the descriptor label key', () => {
    expect(optionLabel(messagesEn, claimMode, 'suit', { claimMode: 'suit', deckSize: '32' })).toBe(messagesEn.options.bullshit.claimMode.suit);
  });

  it('quotes the rank range of the deck in play (A to K for 52 cards, 7 to A for 32)', () => {
    expect(optionLabel(messagesEn, claimMode, 'rank', { claimMode: 'rank', deckSize: '32' })).toContain('7→A');
    expect(optionLabel(messagesEn, claimMode, 'rank', { claimMode: 'rank', deckSize: '52' })).toContain('A→K');
  });

  it('falls back to the raw value when the label is not translated', () => {
    expect(optionLabel(messagesEn, unknown, 'fast', { speed: 'fast' })).toBe('fast');
    expect(optionLabel(messagesEn, claimMode, 'mystery', {})).toBe('mystery');
  });

  it('survives a missing deck size by leaving the range placeholder out of the way', () => {
    expect(optionLabel(messagesEn, claimMode, 'rank', {})).not.toContain('{');
  });

  it('resolves the group label, falling back to the key', () => {
    expect(optionGroupLabel(messagesEn, claimMode)).toBe(messagesEn.options.bullshit.claimMode.label);
    expect(optionGroupLabel(messagesEn, unknown)).toBe('speed');
  });
});
