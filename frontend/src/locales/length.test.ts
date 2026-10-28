import { writeFileSync } from 'node:fs';
import { describe, it, expect } from 'vitest';
import { messagesEn } from './en';
import { messagesFr } from './fr';

// French runs longer than English and the UI must fit at 375 px. Without a browser, this lists the short strings
// (buttons, labels, titles) where French is more than 60% longer, so they can be checked by hand on a phone.
const MAX_ENGLISH_LENGTH = 40; // longer strings are sentences, which wrap
const GROWTH_LIMIT = 1.6;
const MIN_EXTRA = 6; // a few extra characters on a 3-letter word are not a layout risk

function strings(value: unknown, path: string, out: Map<string, string>): void {
  if (typeof value === 'string') out.set(path, value);
  else if (Array.isArray(value)) value.forEach((item, i) => strings(item, `${path}[${i}]`, out));
  else if (value && typeof value === 'object') {
    for (const [k, v] of Object.entries(value)) strings(v, path ? `${path}.${k}` : k, out);
  }
}

export function longFrenchStrings(en: Map<string, string>, fr: Map<string, string>) {
  const rows: { key: string; en: string; fr: string; ratio: number }[] = [];
  for (const [key, english] of en) {
    const french = fr.get(key);
    if (french === undefined || /\.(kind|tone|id)$/.test(key)) continue;
    const e = english.replace(/\{\w+\}/g, '').length;
    const f = french.replace(/\{\w+\}/g, '').length;
    if (english.length > MAX_ENGLISH_LENGTH || e === 0) continue;
    if (f > e * GROWTH_LIMIT && f - e >= MIN_EXTRA) rows.push({ key, en: english, fr: french, ratio: f / e });
  }
  return rows.sort((a, b) => b.ratio - a.ratio);
}

describe('French string length', () => {
  const en = new Map<string, string>();
  const fr = new Map<string, string>();
  strings(messagesEn, '', en);
  strings(messagesFr, '', fr);

  it('givenAShortEnglishString_whenFrenchIsOverSixtyPercentLonger_thenItIsListed', () => {
    const rows = longFrenchStrings(en, fr);
    // Informative: the owner checks these at 375 px. Set LIST_LONG_FRENCH=<file> to write the list there.
    const target = process.env.LIST_LONG_FRENCH;
    if (target) {
      writeFileSync(target, rows.map(r => `${r.ratio.toFixed(2)}\t${r.key}\t${r.en} -> ${r.fr}`).join('\n'), 'utf8');
    }
    expect(rows.length).toBeGreaterThanOrEqual(0);
  });

  it('givenTheHelper_thenItFlagsOnlyLongGrowth', () => {
    const a = new Map([['x', 'Back'], ['y', 'Send'], ['z', 'Go']]);
    const b = new Map([['x', 'Retour au jeu'], ['y', 'Envoi'], ['z', 'Aller']]);
    expect(longFrenchStrings(a, b).map(r => r.key)).toEqual(['x']);
  });
});
