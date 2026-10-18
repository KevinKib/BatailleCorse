import { describe, it, expect } from 'vitest';
import { mount } from '@vue/test-utils';
import MorrisLobbyOptions from './MorrisLobbyOptions.vue';
import { messagesEn } from '../../locales/en';
import type { OptionDescriptor } from '../../model/lobby/LobbyView';

const ui = messagesEn.morrisLobby;

const descriptors: OptionDescriptor[] = [
  { key: 'variant', type: 'CHOICE', values: ['tapatan', 'achi', 'picaria'], defaultValue: 'tapatan', labelKey: 'options.threeMenMorris.variant' },
  { key: 'hostColour', type: 'CHOICE', values: ['white', 'black', 'random'], defaultValue: 'white', labelKey: 'options.threeMenMorris.hostColour' },
];

function mountOptions(props: { options?: Record<string, string>; editable?: boolean } = {}) {
  return mount(MorrisLobbyOptions, { props: { descriptors, options: {}, editable: true, ...props } });
}
const t = (w: ReturnType<typeof mountOptions>, key: string) => w.get(`[data-test="${key}"]`);

describe('MorrisLobbyOptions for the host', () => {
  it('offers the three variants as cards with a mini board, a name and a summary', () => {
    const w = mountOptions();
    for (const key of ['tapatan', 'achi', 'picaria'] as const) {
      const card = t(w, `variant-card-${key}`);
      expect(card.text()).toContain(ui.variants[key].name);
      expect(card.text()).toContain(ui.variants[key].summary);
      expect(card.find('svg').exists(), key).toBe(true);
    }
  });

  it('names Picaria by its pieces and points, and draws its 13 points with the others on nine', () => {
    const w = mountOptions();
    expect(ui.variants.picaria.summary).toBe('3 pieces, 13 points');
    expect(t(w, 'variant-card-picaria').findAll('circle')).toHaveLength(13);
    expect(t(w, 'variant-card-achi').findAll('circle')).toHaveLength(9);
    expect(t(w, 'variant-card-picaria').findAll('line')).toHaveLength(28);
    expect(t(w, 'variant-card-achi').findAll('line')).toHaveLength(8);
    expect(t(w, 'variant-card-tapatan').findAll('line')).toHaveLength(8);
  });

  it('checks the default variant and shows its rule line', () => {
    const w = mountOptions();
    expect((t(w, 'option-variant-tapatan').element as HTMLInputElement).checked).toBe(true);
    expect(t(w, 'variant-rule').text()).toBe(ui.variants.tapatan.rule);
  });

  it('shows the rule of the chosen variant', () => {
    const w = mountOptions({ options: { variant: 'picaria' } });
    expect((t(w, 'option-variant-picaria').element as HTMLInputElement).checked).toBe(true);
    expect(t(w, 'variant-rule').text()).toBe(ui.variants.picaria.rule);
    expect(t(w, 'variant-rule').text()).toContain('centre is closed');
  });

  it('asks for a variant when a card is picked', async () => {
    const w = mountOptions();
    await t(w, 'option-variant-achi').setValue(true);
    expect(w.emitted('set-option')).toEqual([['variant', 'achi']]);
  });

  it('offers White, Black and Random under "Your colour", White checked by default', () => {
    const w = mountOptions();
    expect(t(w, 'option-hostColour').text()).toContain(ui.colourLabel);
    for (const value of ['white', 'black', 'random'] as const) {
      expect(t(w, `colour-${value}`).text()).toContain(ui.colours[value]);
    }
    expect((t(w, 'option-hostColour-white').element as HTMLInputElement).checked).toBe(true);
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
    expect(t(w, 'option-hostColour').text()).toContain(ui.colours.white);
  });
});
