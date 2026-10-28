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

/**
 * Registers the run log. Stub from the shared-interfaces chunk: does nothing yet, the run-log chunk fills it
 * (append one line per run on `after:run`, print the retried tests at the end of the run).
 */
export function registerRunLog(_on: Cypress.PluginEvents, _config: Cypress.PluginConfigOptions): void {}
