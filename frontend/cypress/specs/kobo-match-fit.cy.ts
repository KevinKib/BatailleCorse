// The widest the action bar gets with the matching discard: at the end of a turn it offers End turn, Kobo! and
// Match, and Cancel joins them once Match is armed, under the prompt and the event-log line. The bar must stay
// reachable (on screen, above the footer, 44 px buttons), the table keeps its opponents without overlap, and the
// log line must not push the table: the numbers are recorded for the report (see kobo-actions-fit.cy.ts).
// Three rooms only: a spec that opens many leaves connections behind that starve the next ones.
import { dealWithTopCard, startKoboTable } from '../support/koboRoom';
import { actionBarIsReachable, expectNoOverlap, measureTable, tableDoesNotScrollSideways } from '../support/koboGeometry';

const cases = [
  { width: 375, height: 667, players: 4 },
  { width: 375, height: 667, players: 6 },
  { width: 1280, height: 800, players: 6 },
] as const;

describe('Kobo action bar with the matching discard', () => {
  cases.forEach(({ width, height, players }) => {
    it(`keeps End turn, Kobo!, Match and Cancel reachable with ${players} players at ${width}x${height}`, () => {
      cy.viewport(width, height);
      // The first card drawn is a 3: no power, so discarding it leaves the end of the turn.
      startKoboTable(players, dealWithTopCard(players, '3S')).then(({ guests }) => {
        cy.get('[data-test="action-ready"]').click();
        cy.then(() => guests.forEach((g) => g.publish('/app/kobo/ready', {})));
        cy.get('[data-test="action-draw"]').should('be.visible').click();
        cy.get('[data-test="action-discard"]').click();
        cy.get('[data-test="action-endTurn"]').should('be.visible');
        cy.get('[data-test="action-match"]').should('be.visible');
        cy.get('[data-test="event-log-line"]').should('be.visible').and('have.text', 'You discarded 3 of spade');
        actionBarIsReachable();
        expectNoOverlap();
        measureTable(`turn end: End turn + Kobo! + Match + log line, ${players} players`);

        cy.get('[data-test="action-match"]').click();
        cy.get('[data-test="action-cancel"]').should('be.visible');
        cy.get('[data-test="prompt"]').should('have.text', 'Pick the card to match');
        actionBarIsReachable();
        expectNoOverlap();
        tableDoesNotScrollSideways();
        measureTable(`match armed: prompt + End turn + Kobo! + Match + Cancel + log line, ${players} players`);

        // The second step has the same bar: pick a card of another player, then the prompt asks for mine.
        cy.get('[data-test="opponent-seat"][data-seat="1"] button').first().scrollIntoView().click();
        cy.get('[data-test="prompt"]').should('have.text', 'Pick one of your cards to give');
        actionBarIsReachable();
        expectNoOverlap();
        measureTable(`match give step, ${players} players`);
      });
    });
  });
});
