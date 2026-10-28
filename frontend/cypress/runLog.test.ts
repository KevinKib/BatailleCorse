import { describe, expect, it } from 'vitest';
import { buildRunLogRecord, formatRetriedReport, type RunLogRecord, type SpecResultsLike } from './runLog';

const meta = { branch: 'test/x', commit: 'abc1234', date: '2026-10-28T10:00:00.000Z' };

const results: SpecResultsLike = {
  stats: { duration: 4200, tests: 3, passes: 2, failures: 1 },
  tests: [
    { title: ['Lobby', 'creates a game'], attempts: [{ state: 'passed' }] },
    { title: ['Lobby', 'joins a game'], attempts: [{ state: 'failed' }, { state: 'passed' }] },
    { title: ['Lobby', 'starts a game'], attempts: [{ state: 'failed' }, { state: 'failed' }] },
  ],
};

describe('buildRunLogRecord', () => {
  it('maps the spec stats and metadata', () => {
    const record = buildRunLogRecord('cypress/specs/lobby.cy.ts', results, meta);
    expect(record).toMatchObject({ spec: 'cypress/specs/lobby.cy.ts', durationMs: 4200, tests: 3, passes: 2, failures: 1, ...meta });
  });

  it('lists every test that needed more than one attempt, passed or not, by its full title', () => {
    const record = buildRunLogRecord('s', results, meta);
    expect(record.retriedTests).toEqual(['Lobby > joins a game', 'Lobby > starts a game']);
  });

  it('has no retried tests when every test passed first time', () => {
    const record = buildRunLogRecord('s', { stats: results.stats, tests: [results.tests![0]] }, meta);
    expect(record.retriedTests).toEqual([]);
  });

  it('tolerates a spec without tests (e.g. a compile error)', () => {
    const record = buildRunLogRecord('s', { stats: { duration: 10, tests: 0, passes: 0, failures: 0 } }, meta);
    expect(record.retriedTests).toEqual([]);
  });
});

describe('formatRetriedReport', () => {
  const rec = (spec: string, retriedTests: string[]): RunLogRecord => ({
    spec, durationMs: 1, tests: 1, passes: 1, failures: 0, retriedTests, ...meta,
  });

  it('prints each retried test under its spec', () => {
    const report = formatRetriedReport([rec('a.cy.ts', ['T > one']), rec('b.cy.ts', []), rec('c.cy.ts', ['T > two'])]);
    expect(report).toContain('2 retried test(s)');
    expect(report).toContain('a.cy.ts');
    expect(report).toContain('T > one');
    expect(report).toContain('T > two');
    expect(report).not.toContain('b.cy.ts');
  });

  it('says nothing was retried when nothing was', () => {
    expect(formatRetriedReport([rec('a.cy.ts', [])])).toContain('No retried test');
  });
});
