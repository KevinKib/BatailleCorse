import type { OrdinalForms, PluralForms } from './Messages';
import { currentLocale } from './locale';

// Fills `{name}` placeholders. An unknown placeholder stays visible in the output
// so a missing variable shows up in the UI instead of silently vanishing.
// In French, "de" before a value that starts with a vowel is elided ("En attente d’Alice"), since a template cannot
// know the name it will hold. A leading "h" is left alone: it may be aspirated ("de Hugo").
export function format(template: string, vars: Record<string, string | number>): string {
  const filled = template.replace(/\{(\w+)\}/g, (placeholder, key: string) =>
    key in vars ? `${PLACEHOLDER_START}${String(vars[key])}` : placeholder);
  const elided = currentLocale() === 'fr'
    ? filled.replace(FRENCH_DE_BEFORE_VOWEL, (_match, before: string, d: string) => `${before}${d}’`)
    : filled;
  return elided.split(PLACEHOLDER_START).join('');
}

// Marks where a filled value starts, so only "de" right before a value is elided, never "de" before the template's own words.
const PLACEHOLDER_START = '\u0000';
const FRENCH_DE_BEFORE_VOWEL = /(^|[^\p{L}])([dD])e \u0000(?=[aeiouyàâäéèêëîïôöùûüAEIOUYÀÂÄÉÈÊËÎÏÔÖÙÛÜ])/gu;

// Picks the singular form where the running locale's rules say "one" (English: exactly 1; French: 0 and 1),
// the other form otherwise, and fills `{n}` with the count plus any extra variables.
export function plural(
  forms: PluralForms,
  n: number,
  vars: Record<string, string | number> = {},
): string {
  return format(PLURALS.get().select(n) === 'one' ? forms.one : forms.other, { ...vars, n });
}

// Joins items as a spoken list in the running locale: "a, b, and c" / "a, b et c" (conjunction),
// "a, b, or c" / "a, b ou c" (disjunction).
export function formatList(items: readonly string[], type: 'conjunction' | 'disjunction'): string {
  return new Intl.ListFormat(currentLocale(), { style: 'long', type }).format(items);
}

// Plural rules of the running locale, rebuilt when the locale changes (tests switch it).
class PerLocale {
  private cached: { locale: string; rules: Intl.PluralRules } | undefined;
  constructor(private readonly type: Intl.PluralRulesOptions['type']) {}
  get(): Intl.PluralRules {
    const locale = currentLocale();
    if (this.cached?.locale !== locale) {
      this.cached = { locale, rules: new Intl.PluralRules(locale, { type: this.type }) };
    }
    return this.cached.rules;
  }
}

const PLURALS = new PerLocale('cardinal');
const ORDINALS = new PerLocale('ordinal');

// Picks the ordinal form of `n` for the running locale and fills `{n}`. English: 1st, 2nd, 3rd, 4th,
// 11th to 13th "th". French: 1er, then 2e, 3e... (the forms carry the suffixes).
export function ordinal(n: number, forms: OrdinalForms): string {
  const category = ORDINALS.get().select(n);
  const template = category === 'one' ? forms.one
    : category === 'two' ? forms.two
      : category === 'few' ? forms.few
        : forms.other;
  return format(template, { n });
}
