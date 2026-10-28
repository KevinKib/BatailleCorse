import { describe, expect, it } from 'vitest';
// @ts-expect-error plain ESM script without types
import { summarize, formatSummary } from './cypress-run-summary.mjs';

const line = (spec: string, durationMs: number, failures = 0, retriedTests: string[] = []) =>
  JSON.stringify({ spec, durationMs, tests: 2, passes: 2 - failures, failures, retriedTests, branch: 'b', commit: 'c', date: 'd' });

describe('summarize', () => {
  it('groups by spec with runs, failures, retries and median duration', () => {
    const text = [line('a', 1000), line('a', 3000, 1, ['t']), line('a', 2000), line('b', 500)].join('\n') + '\n';
    const rows = summarize(text);
    expect(rows.find((r: any) => r.spec === 'a')).toEqual({ spec: 'a', runs: 3, failedRuns: 1, retriedRuns: 1, medianMs: 2000 });
    expect(rows.find((r: any) => r.spec === 'b')).toMatchObject({ runs: 1, medianMs: 500 });
  });

  it('uses the mean of the two middle values for an even count', () => {
    expect(summarize([line('a', 1000), line('a', 3000)].join('\n'))[0].medianMs).toBe(2000);
  });

  it('skips blank and malformed lines', () => {
    expect(summarize(`\nnot json\n${line('a', 1)}\n`)).toHaveLength(1);
  });

  it('sorts the slowest median first', () => {
    expect(summarize([line('fast', 1), line('slow', 9)].join('\n')).map((r: any) => r.spec)).toEqual(['slow', 'fast']);
  });
});

describe('formatSummary', () => {
  it('prints one row per spec', () => {
    const out = formatSummary(summarize(line('a.cy.ts', 1500)));
    expect(out).toContain('a.cy.ts');
    expect(out).toContain('1.5');
  });
});
