import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { primeVueConfig } from './primeVueConfig';

// The app has a single, dark look. PrimeVue must not follow the computer's theme ('system'), or a laptop set to a
// light theme gets light tokens and a `color-scheme: light` that turns every inherited text black on the felt.
describe('PrimeVue theme config', () => {
  const selector = primeVueConfig.theme.options.darkModeSelector;

  it('does not follow the OS colour scheme', () => {
    expect(selector).not.toBe('system');
  });

  it('is the class that index.html puts on <html>, so the dark tokens always apply', () => {
    const html = readFileSync(resolve(__dirname, '../index.html'), 'utf8');
    const htmlClass = /<html[^>]*\sclass="([^"]+)"/.exec(html)?.[1];
    expect(htmlClass).toBeDefined();
    expect(selector).toBe(`.${htmlClass}`);
  });
});
