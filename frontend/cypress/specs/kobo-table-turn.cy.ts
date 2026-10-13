// The Kobo table follows a turn it does not play (read only): the browser is the guest in seat 1, a scripted host
// draws, discards and ends its turn. Kept apart from kobo-table.cy.ts: a spec file that opens many rooms leaves
// enough streaming connections behind to starve the next ones.
import { dealWithTopCard, startKoboTableAsGuest } from '../support/koboRoom';
import { everySlotAtLeast44, expectNoOverlap, tableDoesNotScrollSideways } from '../support/koboGeometry';

const viewports = [
  { width: 375, height: 667, label: 'phone' },
  { width: 1280, height: 800, label: 'desktop' },
] as const;

describe('Kobo table, a turn of another player', () => {
  viewports.forEach(({ width, height, label }) => {
    [2, 6].forEach((players) => {
      it(`shows the discard top, the drawn card and the turn passing with ${players} players on a ${label}`, () => {
        cy.viewport(width, height);
        startKoboTableAsGuest(players, dealWithTopCard(players, '3S')).then(({ host, guests, mine }) => {
          cy.then(() => [host, mine, ...guests].forEach((p) => p.publish('/app/kobo/ready', {})));
          // Everybody is ready: the first turn is the host's, and nothing has been discarded yet.
          cy.get('[data-test="phase"]').should('not.have.text', 'Memorise your two cards');
          cy.get('[data-test="discard-pile"] img').should('not.exist');
          cy.get('[data-test="discard-count"]').should('have.text', '0');
          expectNoOverlap();

          cy.then(() => host.publish('/app/kobo/draw', {}));
          cy.get('[data-test="drawn-zone"]').should('have.attr', 'aria-label').and('match', /holds a drawn card$/);
          cy.get('[data-test="drawn-zone"] img').should('be.visible');
          cy.get('[data-test="opponent-seat"][data-seat="0"] [data-test="seat-current"]').should('be.visible');

          cy.then(() => host.publish('/app/kobo/discardDrawn', {}));
          cy.get('[data-test="discard-pile"] img').should('be.visible').and('have.attr', 'alt', '3 of spade');
          cy.get('[data-test="discard-count"]').should('have.text', '1');
          cy.get('[data-test="drawn-zone"] img').should('not.exist');
          expectNoOverlap();
          everySlotAtLeast44();
          tableDoesNotScrollSideways();

          // A 3 has no power: the turn is over once it is discarded, and the host ends it.
          cy.get('[data-test="phase"]').invoke('text').should('match', / is finishing the turn$/);
          cy.then(() => host.publish('/app/kobo/endTurn', {}));
          cy.get('[data-test="phase"]').should('have.text', 'Your turn: draw a card');
          cy.get('[data-test="turn-hint"]').should('be.visible');
          cy.get('[data-test="my-zone"] [data-test="seat-current"]').should('be.visible');
          expectNoOverlap();
        });
      });
    });
  });
});
