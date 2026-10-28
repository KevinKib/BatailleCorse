import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import DraughtsLobbyOptions from './DraughtsLobbyOptions.vue';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';
import { messagesEn } from '../../locales/en';

const DESCRIPTORS: OptionDescriptor[] = [
  { key: 'hostColour', type: 'CHOICE', values: ['white', 'black', 'random'], defaultValue: 'random', labelKey: 'options.draughts.hostColour' },
  { key: 'drawOffers', type: 'CHOICE', values: ['on', 'off'], defaultValue: 'on', labelKey: 'options.draughts.drawOffers' },
];

const options = (props: Record<string, unknown> = {}) => mount(DraughtsLobbyOptions, {
  props: { descriptors: DESCRIPTORS, options: { hostColour: 'random', drawOffers: 'on' }, editable: true, ...props },
});

describe('DraughtsLobbyOptions', () => {
  it('draws the opening position on a small board next to the one-line description', () => {
    const w = options();
    expect(w.get('[data-test="draughts-mini-board"]').attributes('aria-hidden')).toBe('true');
    expect(w.findAll('[data-test="mini-piece"][data-colour="black"]')).toHaveLength(20);
    expect(w.findAll('[data-test="mini-piece"][data-colour="white"]')).toHaveLength(20);
    expect(w.get('[data-test="draughts-summary"]').text()).toBe(messagesEn.draughtsUi.lobby.summary);
  });

  it('lets the host pick who plays White and whether draw offers are allowed, in words', () => {
    const w = options();
    const colour = w.get('[data-test="option-hostColour"]');
    expect(colour.findAll('label').map(l => l.text())).toEqual(['White', 'Black', 'Random']);
    expect(w.get('[data-test="option-drawOffers"]').findAll('label').map(l => l.text())).toEqual(['Allowed', 'Off']);
    expect(w.get('[data-test="option-hostColour"] legend').text()).toBe('Host plays');
  });

  it('asks for the change the host makes', async () => {
    const w = options();
    await w.get('[data-test="option-drawOffers-off"]').setValue(true);
    expect(w.emitted('set-option')).toEqual([['drawOffers', 'off']]);
  });

  it('shows the choices as plain values to a guest', () => {
    const w = options({ editable: false, options: { hostColour: 'black', drawOffers: 'off' } });
    expect(w.findAll('input[type="radio"]')).toHaveLength(0);
    expect(w.get('[data-test="option-hostColour"]').text()).toContain('Black');
    expect(w.get('[data-test="option-drawOffers"]').text()).toContain('Off');
  });
});
