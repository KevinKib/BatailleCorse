import type { PluralForms } from './Messages';

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
