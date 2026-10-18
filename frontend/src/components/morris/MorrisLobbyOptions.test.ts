import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import MorrisLobbyOptions from './MorrisLobbyOptions.vue';
import { messagesEn } from '../../locales/en';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';

const ui = messagesEn.morrisLobby;

const descriptors: OptionDescriptor[] = [
  { key: 'variant', type: 'CHOICE', values: ['tapatan', 'achi'], defaultValue: 'tapatan', labelKey: 'options.threeMenMorris.variant' },
  { key: 'hostColour', type: 'CHOICE', values: ['white', 'black', 'random'], defaultValue: 'random', labelKey: 'options.threeMenMorris.hostColour' },
];

function mountOptions(props: { options?: Record<string, string>; editable?: boolean } = {}) {
  return mount(MorrisLobbyOptions, { props: { descriptors, options: {}, editable: true, ...props } });
}
const t = (w: ReturnType<typeof mountOptions>, key: string) => w.get(`[data-test="${key}"]`);

describe('MorrisLobbyOptions for the host', () => {
  it('offers the two variants as cards with a mini board, a name and a summary, and no Picaria card', () => {
    const w = mountOptions();
    for (const key of ['tapatan', 'achi'] as const) {
      const card = t(w, `variant-card-${key}`);
      expect(card.text()).toContain(ui.variants[key].name);
      expect(card.text()).toContain(ui.variants[key].summary);
      expect(card.find('svg').exists(), key).toBe(true);
      expect(card.findAll('circle'), key).toHaveLength(9);
      expect(card.findAll('line'), key).toHaveLength(8);
    }
    expect(w.findAll('[data-test^="variant-card-"]')).toHaveLength(2);
    expect(w.find('[data-test="variant-card-picaria"]').exists()).toBe(false);
  });

  it('checks the default variant and shows its rule line', () => {
    const w = mountOptions();
    expect((t(w, 'option-variant-tapatan').element as HTMLInputElement).checked).toBe(true);
    expect(t(w, 'variant-rule').text()).toBe(ui.variants.tapatan.rule);
  });

  it('shows the rule of the chosen variant', () => {
    const w = mountOptions({ options: { variant: 'achi' } });
    expect((t(w, 'option-variant-achi').element as HTMLInputElement).checked).toBe(true);
    expect(t(w, 'variant-rule').text()).toBe(ui.variants.achi.rule);
  });

  it('asks for a variant when a card is picked', async () => {
    const w = mountOptions();
    await t(w, 'option-variant-achi').setValue(true);
    expect(w.emitted('set-option')).toEqual([['variant', 'achi']]);
  });

  it('offers White, Black and Random under "Your colour", Random checked by default', () => {
    const w = mountOptions();
    expect(t(w, 'option-hostColour').text()).toContain(ui.colourLabel);
    for (const value of ['white', 'black', 'random'] as const) {
      expect(t(w, `colour-${value}`).text()).toContain(ui.colours[value]);
    }
    expect((t(w, 'option-hostColour-random').element as HTMLInputElement).checked).toBe(true);
    expect((t(w, 'option-hostColour-white').element as HTMLInputElement).checked).toBe(false);
  });

  it('checks Random when the lobby sends neither a colour nor a descriptor', () => {
    const w = mount(MorrisLobbyOptions, { props: { descriptors: [], options: {}, editable: true } });
    expect((t(w, 'option-hostColour-random').element as HTMLInputElement).checked).toBe(true);
  });

  it('asks for a colour when a button is picked', async () => {
    const w = mountOptions();
    await t(w, 'option-hostColour-black').setValue(true);
    expect(w.emitted('set-option')).toEqual([['hostColour', 'black']]);
  });

  it('puts the variant, its rule, then the colour, in that order', () => {
    const html = mountOptions().html();
    const at = (needle: string) => html.indexOf(needle);
    expect(at('data-test="option-variant"')).toBeLessThan(at('data-test="variant-rule"'));
    expect(at('data-test="variant-rule"')).toBeLessThan(at('data-test="option-hostColour"'));
  });
});

describe('MorrisLobbyOptions for the others', () => {
  it('reads the choices without any control', () => {
    const w = mountOptions({ editable: false, options: { variant: 'achi', hostColour: 'black' } });
    expect(w.findAll('input')).toHaveLength(0);
    expect(w.findAll('button')).toHaveLength(0);
    expect(t(w, 'option-variant').text()).toContain(ui.variants.achi.name);
    expect(t(w, 'variant-rule').text()).toBe(ui.variants.achi.rule);
    expect(t(w, 'option-hostColour').text()).toContain(ui.hostPlays);
    expect(t(w, 'option-hostColour').text()).toContain(ui.colours.black);
  });

  it('shows the defaults when the lobby sends no option', () => {
    const w = mountOptions({ editable: false });
    expect(t(w, 'option-variant').text()).toContain(ui.variants.tapatan.name);
    expect(t(w, 'option-hostColour').text()).toContain(ui.colours.random);
  });
});
