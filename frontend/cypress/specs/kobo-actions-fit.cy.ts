// The action bar costs table height. This spec keeps it reachable (never below the screen, never under the
// footer, 44 px buttons) at 2, 4 and 6 players on a phone and at 6 on a desktop, in the memorising phase where
// the bar carries its one button, and records what is left of the table. Four rooms: a spec that opens many
// leaves connections behind that starve the next ones.
import { DECK_CODES, startKoboTable } from '../support/koboRoom';
import { actionBarIsReachable, expectNoOverlap, measureTable, tableDoesNotScrollSideways } from '../support/koboGeometry';

const cases = [
  { width: 375, height: 667, players: 2 },
  { width: 375, height: 667, players: 4 },
  { width: 375, height: 667, players: 6 },
  { width: 1280, height: 800, players: 6 },
] as const;

describe('Kobo action bar fit', () => {
  cases.forEach(({ width, height, players }) => {
    it(`stays reachable with ${players} players at ${width}x${height}`, () => {
      cy.viewport(width, height);
      startKoboTable(players, DECK_CODES.slice(0, 4 * players + 1)).then(() => {
        cy.get('[data-test="action-ready"]').should('be.visible');
        actionBarIsReachable();
        expectNoOverlap();
        tableDoesNotScrollSideways();
        measureTable(`memorising, ${players} players`);
      });
    });
  });
});
