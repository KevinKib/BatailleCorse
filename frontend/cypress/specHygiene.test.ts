import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';

// Hygiene guard for the Cypress suite. Paths resolve from frontend/ (this file is frontend/cypress/).
const frontendDir = join(dirname(fileURLToPath(import.meta.url)), '..');
const specsDir = join(frontendDir, 'cypress', 'specs');

// Existing offenders, kept as they are (nothing renamed or deleted here). Each entry says why it is tolerated.
// Scratch/capture-style specs: opt-in screenshot or frame capture runs, skipped unless a flag is set.
const SCRATCH_ALLOW_LIST: Record<string, string> = {
  'kobo-match-screenshots.cy.ts': 'opt-in screenshot run (Cypress.env screenshots), describe.skip otherwise',
  'kobo-shared-animation-capture.cy.ts': 'opt-in animation capture run, describe.skip unless a phase is given',
};
// Conditional skips without an issue number: the skip is a runtime switch, not a parked test.
const SKIP_ALLOW_LIST: Record<string, string> = {
  'kobo-match-screenshots.cy.ts': 'opt-in switch (see above)',
  'kobo-shared-animation-capture.cy.ts': 'opt-in switch (see above)',
  'president-stillness.cy.ts': 'it.skip used to run a single titled case via an "only" filter',
};

const SCRATCH_NAME = /(scratch|capture|debug|screenshot|shots)/i;
const ONLY = /\b(?:it|describe|context)\.only\s*\(/;
const SKIP = /\b(?:it|describe|context)\.skip\b|\bxit\s*\(|\bxdescribe\s*\(|\bxcontext\s*\(/;
const ISSUE_REF = /#\d+/;

/** True when the file name looks like a scratch, capture or debug spec. */
export function isScratchName(fileName: string): boolean {
  return SCRATCH_NAME.test(fileName);
}

/** Lines using `.only`. */
export function findOnly(source: string): number[] {
  return lineNumbers(source, (line) => ONLY.test(line));
}

/** Lines that skip a test without an issue number (`#123`) on the same line or the line just above. */
export function findUntrackedSkips(source: string): number[] {
  const lines = source.split('\n');
  return lineNumbers(source, (line, i) => SKIP.test(line) && !ISSUE_REF.test(line) && !(i > 0 && ISSUE_REF.test(lines[i - 1])));
}

function lineNumbers(source: string, test: (line: string, index: number) => boolean): number[] {
  return source.split('\n').flatMap((line, i) => (test(line, i) ? [i + 1] : []));
}

const specFiles = readdirSync(specsDir).filter((f) => f.endsWith('.cy.ts'));

describe('spec hygiene detectors', () => {
  it('flags scratch-style names', () => {
    expect(isScratchName('zz-shots.cy.ts')).toBe(true);
    expect(isScratchName('debug-foo.cy.ts')).toBe(true);
    expect(isScratchName('kobo-table.cy.ts')).toBe(false);
  });

  it('flags .only', () => {
    expect(findOnly("it.only('a', () => {})")).toEqual([1]);
    expect(findOnly("describe.only('a', () => {})")).toEqual([1]);
    expect(findOnly("it('only a', () => {})")).toEqual([]);
  });

  it('flags skips without an issue number, accepts tracked ones', () => {
    expect(findUntrackedSkips("it.skip('a', () => {})")).toEqual([1]);
    expect(findUntrackedSkips("xit('a', () => {})")).toEqual([1]);
    expect(findUntrackedSkips("it.skip('a', () => {}) // #312")).toEqual([]);
    expect(findUntrackedSkips("// flaky, see #312\nit.skip('a', () => {})")).toEqual([]);
    expect(findUntrackedSkips("it('a', () => {})")).toEqual([]);
  });
});

describe('Cypress spec hygiene', () => {
  it('has specs to check', () => {
    expect(specFiles.length).toBeGreaterThan(0);
  });

  it('has no scratch, capture or debug specs outside the allow-list', () => {
    const offenders = specFiles.filter((f) => isScratchName(f) && !(f in SCRATCH_ALLOW_LIST));
    expect(offenders).toEqual([]);
  });

  it('has no .only', () => {
    const offenders = specFiles.flatMap((f) =>
      findOnly(readFileSync(join(specsDir, f), 'utf8')).map((n) => `${f}:${n}`));
    expect(offenders).toEqual([]);
  });

  it('has no skip without an issue number outside the allow-list', () => {
    const offenders = specFiles
      .filter((f) => !(f in SKIP_ALLOW_LIST))
      .flatMap((f) => findUntrackedSkips(readFileSync(join(specsDir, f), 'utf8')).map((n) => `${f}:${n}`));
    expect(offenders).toEqual([]);
  });

  it('keeps allow-list entries pointing at existing specs', () => {
    const stale = [...Object.keys(SCRATCH_ALLOW_LIST), ...Object.keys(SKIP_ALLOW_LIST)].filter((f) => !specFiles.includes(f));
    expect(stale).toEqual([]);
  });

  it('lists only existing specs in smoke-specs.json', () => {
    const { specs } = JSON.parse(readFileSync(join(frontendDir, 'cypress', 'smoke-specs.json'), 'utf8')) as { specs: string[] };
    expect(specs.length).toBeGreaterThan(0);
    expect(specs.filter((s) => !existsSync(join(frontendDir, s)))).toEqual([]);
  });
});
