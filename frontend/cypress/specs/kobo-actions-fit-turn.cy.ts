// The widest the action bar gets: after a peek the end of the turn offers End turn, Kobo! and Hide, which do not
// fit on one line of a phone. The bar must stay reachable (on screen, above the footer, 44 px buttons) at 4 and 6
// players, and the table keeps its first row of opponents. Two rooms only (see kobo-actions-fit.cy.ts).
import { DECK_CODES, startKoboTable } from '../support/koboRoom';
import { actionBarIsReachable, expectNoOverlap, measureTable, tableDoesNotScrollSideways } from '../support/koboGeometry';

[4, 6].forEach((players) => {
  describe(`Kobo action bar at the end of a turn with a peek, ${players} players`, () => {
    it('keeps End turn, Kobo! and Hide reachable on a phone', () => {
      cy.viewport(375, 667);
      // The first card drawn is a 7: its power looks at one of my own cards.
      startKoboTable(players, [...DECK_CODES.slice(0, 4 * players), '7S']).then(({ guests }) => {
        cy.get('[data-test="action-ready"]').click();
        cy.then(() => guests.forEach((g) => g.publish('/app/kobo/ready', {})));
        cy.get('[data-test="action-draw"]').should('be.visible').click();
        cy.get('[data-test="action-discard"]').click();
        cy.get('[data-test="prompt"]').should('have.text', 'Look at one of your cards');
        measureTable(`peek prompt + Skip, ${players} players`);
        actionBarIsReachable();
        cy.get('[data-test="slot-0-0"]').click();
        cy.get('[data-test="action-hide"]').should('be.visible');
        cy.get('[data-test="action-endTurn"]').should('be.visible');
        cy.get('[data-test="action-announce"]').should('be.visible');
        actionBarIsReachable();
        expectNoOverlap();
        tableDoesNotScrollSideways();
        measureTable(`End turn + Kobo! + Hide, ${players} players`);
      });
    });
  });
});
