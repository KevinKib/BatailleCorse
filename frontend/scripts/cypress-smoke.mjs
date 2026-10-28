// Runs the smoke subset listed in cypress/smoke-specs.json; extra CLI arguments go to `cypress run`.
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

const { specs } = JSON.parse(readFileSync(new URL('../cypress/smoke-specs.json', import.meta.url), 'utf8'));
const result = spawnSync('npx', ['cypress', 'run', '--spec', specs.join(','), ...process.argv.slice(2)], {
  stdio: 'inherit',
  shell: true,
});
process.exit(result.status ?? 1);
