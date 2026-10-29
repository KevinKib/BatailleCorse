import fs from 'node:fs';
import { describe, expect, it } from 'vitest';
import { WIRE_CONTRACTS } from './games';
import { checkWireContract, CONTRACT_DIR, scenarioFiles } from './wireContract';

// The TypeScript program over every golden file and the model types takes a few seconds to build.
const TIMEOUT_MS = 60_000;

describe('the server/frontend wire contract (contract/)', () => {
  it('has one manifest entry per golden folder, each with at least one scenario', () => {
    const folders = fs.readdirSync(CONTRACT_DIR, { withFileTypes: true })
      .filter((d) => d.isDirectory()).map((d) => d.name).sort();

    expect(WIRE_CONTRACTS.map((g) => g.folder).sort()).toEqual(folders);
    for (const game of WIRE_CONTRACTS) expect(scenarioFiles(game.folder), game.folder).not.toEqual([]);
  });

  it('every payload the server sends type-checks against the frontend types', () => {
    expect(checkWireContract(WIRE_CONTRACTS)).toEqual([]);
  }, TIMEOUT_MS);
});
