import { execFileSync } from 'node:child_process';
import { appendFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

/** One line of `cypress/results/run-log.jsonl`: the outcome of one spec in one `cypress run`. */
export interface RunLogRecord {
  spec: string;
  durationMs: number;
  tests: number;
  passes: number;
  failures: number;
  /** Titles of the tests that needed more than one attempt (retried). */
  retriedTests: string[];
  branch: string;
  commit: string;
  /** ISO 8601 date of the run. */
  date: string;
}

/** The part of Cypress's per-spec results this log reads (kept structural so it is testable without Cypress). */
export interface SpecResultsLike {
  stats: { duration?: number; tests: number; passes: number; failures: number };
  tests?: { title: string[]; attempts: { state: string }[] }[];
}

export type RunMeta = Pick<RunLogRecord, 'branch' | 'commit' | 'date'>;

export function buildRunLogRecord(spec: string, results: SpecResultsLike, meta: RunMeta): RunLogRecord {
  const retriedTests = (results.tests ?? [])
    .filter((test) => test.attempts.length > 1)
    .map((test) => test.title.join(' > '));
  return {
    spec,
    durationMs: results.stats.duration ?? 0,
    tests: results.stats.tests,
    passes: results.stats.passes,
    failures: results.stats.failures,
    retriedTests,
    ...meta,
  };
}

export function formatRetriedReport(records: RunLogRecord[]): string {
  const retried = records.filter((record) => record.retriedTests.length > 0);
  if (retried.length === 0) return 'Run log: No retried test.';
  const count = retried.reduce((sum, record) => sum + record.retriedTests.length, 0);
  const lines = retried.flatMap((record) => [`  ${record.spec}`, ...record.retriedTests.map((title) => `    - ${title}`)]);
  return [`Run log: ${count} retried test(s) (flaky or slow stack, check them):`, ...lines].join('\n');
}

function git(args: string[], cwd: string): string | undefined {
  try {
    return execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim() || undefined;
  } catch {
    return undefined;
  }
}

function runMeta(projectRoot: string): RunMeta {
  return {
    branch: process.env.GITHUB_HEAD_REF || process.env.GITHUB_REF_NAME || git(['rev-parse', '--abbrev-ref', 'HEAD'], projectRoot) || 'unknown',
    commit: process.env.GITHUB_SHA || git(['rev-parse', 'HEAD'], projectRoot) || 'unknown',
    date: new Date().toISOString(),
  };
}

/**
 * Registers the run log: after each spec, appends one JSON line to `cypress/results/run-log.jsonl`; at the end of the
 * run, prints the retried tests so that a retry (`retries.runMode`) is never silent.
 */
export function registerRunLog(on: Cypress.PluginEvents, config: Cypress.PluginConfigOptions): void {
  const logPath = resolve(config.projectRoot, 'cypress/results/run-log.jsonl');
  const records: RunLogRecord[] = [];
  on('after:spec', (spec, results) => {
    if (!results?.stats) return;
    const record = buildRunLogRecord(spec.relative, results as unknown as SpecResultsLike, runMeta(config.projectRoot));
    records.push(record);
    try {
      mkdirSync(dirname(logPath), { recursive: true });
      appendFileSync(logPath, JSON.stringify(record) + '\n');
    } catch (error) {
      console.warn(`Run log: could not write ${logPath}: ${error}`);
    }
  });
  on('after:run', () => {
    console.log('\n' + formatRetriedReport(records));
  });
}
