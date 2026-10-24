import type { OrdinalForms, PluralForms } from './Messages';

// Fills `{name}` placeholders. An unknown placeholder stays visible in the output
// so a missing variable shows up in the UI instead of silently vanishing.
export function format(template: string, vars: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (placeholder, key: string) =>
    key in vars ? String(vars[key]) : placeholder);
}

// Picks the singular form for exactly one, the other form for everything else
// (including zero), and fills `{n}` with the count plus any extra variables.
export function plural(
  forms: PluralForms,
  n: number,
  vars: Record<string, string | number> = {},
): string {
  return format(n === 1 ? forms.one : forms.other, { ...vars, n });
}

const ORDINALS = new Intl.PluralRules('en', { type: 'ordinal' });

// Picks the English ordinal form of `n` (1st, 2nd, 3rd, 4th; 11th to 13th are "th") and fills `{n}`.
export function ordinal(n: number, forms: OrdinalForms): string {
  const category = ORDINALS.select(n);
  const template = category === 'one' ? forms.one
    : category === 'two' ? forms.two
      : category === 'few' ? forms.few
        : forms.other;
  return format(template, { n });
}
