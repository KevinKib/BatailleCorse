// Summarises cypress/results/run-log.jsonl: per spec, runs, failed runs, runs with retried tests and median duration.
// Usage: node scripts/cypress-run-summary.mjs [path/to/run-log.jsonl]
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

function median(values) {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

/** Parses the JSON-lines text (blank and malformed lines are skipped) into rows, slowest median first. */
export function summarize(text) {
  const bySpec = new Map();
  for (const raw of text.split('\n')) {
    if (!raw.trim()) continue;
    let record;
    try {
      record = JSON.parse(raw);
    } catch {
      continue;
    }
    if (!record || typeof record.spec !== 'string') continue;
    if (!bySpec.has(record.spec)) bySpec.set(record.spec, []);
    bySpec.get(record.spec).push(record);
  }
  return [...bySpec.entries()]
    .map(([spec, records]) => ({
      spec,
      runs: records.length,
      failedRuns: records.filter((r) => r.failures > 0).length,
      retriedRuns: records.filter((r) => r.retriedTests?.length > 0).length,
      medianMs: median(records.map((r) => r.durationMs)),
    }))
    .sort((a, b) => b.medianMs - a.medianMs);
}

export function formatSummary(rows) {
  const header = ['spec', 'runs', 'failed', 'retried', 'median (s)'];
  const body = rows.map((r) => [r.spec, r.runs, r.failedRuns, r.retriedRuns, (r.medianMs / 1000).toFixed(1)].map(String));
  const all = [header, ...body];
  const widths = header.map((_, i) => Math.max(...all.map((row) => row[i].length)));
  return all.map((row) => row.map((cell, i) => cell.padEnd(widths[i])).join('  ').trimEnd()).join('\n');
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const path = process.argv[2] ?? fileURLToPath(new URL('../cypress/results/run-log.jsonl', import.meta.url));
  if (!existsSync(path)) {
    console.error(`No run log at ${path}`);
    process.exit(1);
  }
  console.log(formatSummary(summarize(readFileSync(path, 'utf8'))));
}
