import { describe, expect, it } from 'vitest';
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

// The import rules of the shared animation module, checked like the backend's ArchUnit rules:
//   core/  pure TypeScript: no Vue, no `dom/`, no `vue/`.
//   dom/   measures places: no Vue, no `vue/`.
//   animation/ never imports a game (`view/`, `state/`, `model/<game>/`, `components/<game>/`).
//   everything else imports the barrel `animation/index.ts`, never a file inside the module.
const ANIMATION = dirname(fileURLToPath(import.meta.url));
const SRC = resolve(ANIMATION, '..');

function filesUnder(dir: string): string[] {
  return readdirSync(dir).flatMap(name => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return name === 'node_modules' ? [] : filesUnder(path);
    return /\.(ts|vue)$/.test(name) ? [path] : [];
  });
}

/** Every module specifier a file imports or re-exports (static and dynamic, not `vi.mock`). */
function specifiers(file: string): string[] {
  const text = readFileSync(file, 'utf8');
  const found: string[] = [];
  for (const match of text.matchAll(/(?:\bfrom\s+|\bimport\s*\(\s*|^\s*import\s+)['"]([^'"]+)['"]/gm)) found.push(match[1]);
  return found;
}

const here = (file: string) => relative(SRC, file).split(sep).join('/');
const target = (file: string, specifier: string) => resolve(dirname(file), specifier);
const isRelative = (specifier: string) => specifier.startsWith('.');
const within = (path: string, dir: string) => path === dir || path.startsWith(dir + sep);

const insideFiles = (layer: string) => filesUnder(join(ANIMATION, layer));
const violations = (files: string[], bad: (file: string, specifier: string) => boolean) =>
  files.flatMap(file => specifiers(file).filter(s => bad(file, s)).map(s => `${here(file)} imports ${s}`));

describe('the shared animation module', () => {
  it('givenTheModule_thenItHasABarrelAndItsThreeLayers', () => {
    expect(existsSync(join(ANIMATION, 'index.ts'))).toBe(true);
    for (const layer of ['core', 'dom', 'vue']) expect(existsSync(join(ANIMATION, layer))).toBe(true);
  });

  it('givenCore_thenItImportsNeitherVueNorDomNorTheVueLayer', () => {
    const bad = violations(insideFiles('core'), (file, s) =>
      s === 'vue' || s.startsWith('@vue/') || s.startsWith('vue-')
      || (isRelative(s) && (within(target(file, s), join(ANIMATION, 'dom')) || within(target(file, s), join(ANIMATION, 'vue')))));
    expect(bad).toEqual([]);
  });

  it('givenDom_thenItImportsNeitherVueNorTheVueLayer', () => {
    const bad = violations(insideFiles('dom'), (file, s) =>
      s === 'vue' || s.startsWith('@vue/') || (isRelative(s) && within(target(file, s), join(ANIMATION, 'vue'))));
    expect(bad).toEqual([]);
  });

  it('givenTheModule_thenItImportsNoGame', () => {
    // Shared leaves (`model/Card`, `components/PlayingCard.vue`) are fine; a folder under model/ or components/ is a game's.
    const inSubfolder = (path: string, dir: string) => within(path, join(SRC, dir)) && relative(join(SRC, dir), path).includes(sep);
    const isGame = (path: string) => within(path, join(SRC, 'view')) || within(path, join(SRC, 'state'))
      || inSubfolder(path, 'model') || inSubfolder(path, 'components');
    const bad = violations(filesUnder(ANIMATION), (file, s) => isRelative(s) && !within(target(file, s), ANIMATION) && isGame(target(file, s)));
    expect(bad).toEqual([]);
  });

  it('givenAnyOtherFile_thenItImportsTheBarrelNeverAFileInsideTheModule', () => {
    const outside = filesUnder(SRC).filter(file => !within(file, ANIMATION));
    const bad = violations(outside, (file, s) => {
      if (!isRelative(s)) return false;
      const path = target(file, s);
      return within(path, ANIMATION) && path !== ANIMATION && path !== join(ANIMATION, 'index') && path !== join(ANIMATION, 'index.ts');
    });
    expect(bad).toEqual([]);
  });
});
