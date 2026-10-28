import { describe, it, expect } from 'vitest';
import { messagesEn } from './en';
import { messagesFr } from './fr';

// Areas whose French is written: the completeness and typography checks run on them only.
// Each translation chunk adds its own areas; the integration chunk turns this into "every area".
const TRANSLATED_AREAS: readonly (keyof typeof messagesEn)[] = [];

// French leaves that may equal the English leaf (proper nouns, symbols). Keys are dotted paths prefixes.
const SAME_AS_ENGLISH_ALLOWED: readonly string[] = ['language.names.en', 'language.names.fr'];

type Leaves = Map<string, string>;

function collect(value: unknown, path: string, out: Leaves): void {
  if (typeof value === 'string') {
    out.set(path, value);
  } else if (Array.isArray(value)) {
    value.forEach((item, index) => collect(item, `${path}[${index}]`, out));
  } else if (value !== null && typeof value === 'object') {
    for (const [key, item] of Object.entries(value)) {
      collect(item, path ? `${path}.${key}` : key, out);
    }
  } else {
    throw new Error(`Unexpected value at ${path}: ${String(value)}`);
  }
}

function leavesOf(messages: unknown, areas?: readonly string[]): Leaves {
  const out: Leaves = new Map();
  for (const [key, value] of Object.entries(messages as Record<string, unknown>)) {
    if (!areas || areas.includes(key)) collect(value, key, out);
  }
  return out;
}

function placeholders(text: string): string[] {
  return [...text.matchAll(/\{(\w+)\}/g)].map(m => m[1]).sort();
}

describe('English and French messages', () => {
  const en = leavesOf(messagesEn);
  const fr = leavesOf(messagesFr);

  it('givenBothLocales_thenTheyHaveExactlyTheSameKeysAndArrayLengths', () => {
    expect([...fr.keys()].sort()).toEqual([...en.keys()].sort());
  });

  it('givenBothLocales_thenEveryLeafHasTheSamePlaceholders', () => {
    for (const [path, text] of en) {
      expect(placeholders(fr.get(path) ?? ''), path).toEqual(placeholders(text));
    }
  });

  it('givenBothLocales_thenEveryRulesSectionKeepsItsKind', () => {
    const kinds = (leaves: Leaves) => [...leaves].filter(([p]) => p.endsWith('.kind'));
    expect(kinds(fr)).toEqual(kinds(en));
  });

  it('givenAnEmptyFrenchLeaf_thenItIsRejected', () => {
    for (const [path, text] of fr) {
      if (en.get(path)) expect(text.trim(), path).not.toBe('');
    }
  });
});

describe('translated areas', () => {
  const en = leavesOf(messagesEn, TRANSLATED_AREAS);
  const fr = leavesOf(messagesFr, TRANSLATED_AREAS);

  it('givenATranslatedArea_thenNoLeafIsStillEnglish', () => {
    for (const [path, text] of fr) {
      if (SAME_AS_ENGLISH_ALLOWED.some(allowed => path.startsWith(allowed))) continue;
      expect(text, path).not.toBe(en.get(path));
    }
  });

  it('givenATranslatedArea_thenItFollowsFrenchTypography', () => {
    for (const [path, text] of fr) {
      expect(text, path).not.toMatch(/ [;!?»]/); // U+202F before ;!?» instead of a plain space
      expect(text, path).not.toMatch(/« /); // U+202F after «
      expect(text, path).not.toMatch(/ :/); // U+00A0 before :
      expect(text, path).not.toMatch(/\w'\w/); // ’ rather than '
      expect(text, path).not.toMatch(/\.\.\./); // … rather than ...
    }
  });
});
