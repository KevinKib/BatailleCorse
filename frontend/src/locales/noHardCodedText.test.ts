import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import { parse } from '@vue/compiler-sfc';

// Guards the locales: no visible English may sit in a template or in an `Intl.*('en'` call
// outside `locales/`, or the French build would show it untranslated.

const SRC = resolve(process.cwd(), 'src');
const ATTRIBUTES = ['label', 'aria-label', 'title', 'placeholder', 'alt'];

// Symbols and digits pass on their own (the check needs a letter). Files allowed to carry
// literal text, each with its reason; empty on purpose, add one only when the text cannot be a message.
const ALLOWED_FILES: readonly string[] = [];

function walk(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else out.push(full);
  }
  return out;
}

const hasLetter = (text: string) => /\p{L}/u.test(text);

// Node types of the Vue compiler: 1 element, 2 text, 5 interpolation.
interface TemplateNode {
  type: number;
  tag?: string;
  content?: string;
  props?: { type: number; name: string; value?: { content: string } }[];
  children?: TemplateNode[];
  loc: { start: { line: number } };
}

function scanTemplate(nodes: TemplateNode[], found: string[]): void {
  for (const node of nodes) {
    if (node.type === 2 && hasLetter(node.content ?? '')) {
      found.push(`line ${node.loc.start.line}: text "${node.content!.trim()}"`);
    }
    if (node.type === 1) {
      for (const prop of node.props ?? []) {
        // type 6 is a static attribute; bound ones (type 7) go through the messages.
        if (prop.type === 6 && ATTRIBUTES.includes(prop.name) && hasLetter(prop.value?.content ?? '')) {
          found.push(`line ${node.loc.start.line}: ${prop.name}="${prop.value!.content}"`);
        }
      }
      scanTemplate(node.children ?? [], found);
    }
  }
}

describe('no hard-coded text', () => {
  const files = walk(SRC).filter(f => !f.includes(`${sep}locales${sep}`));

  it('no .vue template carries literal text, label, aria-label, title, placeholder or alt', { timeout: 60_000 }, () => {
    const offenders: string[] = [];
    for (const file of files.filter(f => f.endsWith('.vue'))) {
      const name = relative(SRC, file).split(sep).join('/');
      if (ALLOWED_FILES.includes(name)) continue;
      const { descriptor } = parse(readFileSync(file, 'utf8'));
      const ast = descriptor.template?.ast;
      if (!ast) continue;
      const found: string[] = [];
      scanTemplate(ast.children as unknown as TemplateNode[], found);
      found.forEach(f => offenders.push(`${name} ${f}`));
    }
    expect(offenders).toEqual([]);
  });

  it('no Intl formatter is pinned to English outside locales/', { timeout: 60_000 }, () => {
    const offenders = files
      .filter(f => /\.(vue|ts)$/.test(f) && !/\.test\.ts$/.test(f))
      .filter(f => /Intl\.\w+\(\s*['"]en['"]/.test(readFileSync(f, 'utf8')))
      .map(f => relative(SRC, f).split(sep).join('/'));
    expect(offenders).toEqual([]);
  });
});
