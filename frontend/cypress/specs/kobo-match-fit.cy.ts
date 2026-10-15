// The widest the table gets with the matching discard (option B): at the end of a turn the bar offers End turn and
// Kobo! under the event-log line, with the match hint (or the first-time note) under the piles; Cancel joins the bar
// once a rival card is picked, under the prompt. The bar must stay
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
        cy.get('[data-test="match-coach"]').should('be.visible');
        cy.get('[data-test="action-match"]').should('not.exist');
        cy.get('[data-test="event-log-line"]').should('be.visible').and('have.text', 'You discarded 3 of spade');
        actionBarIsReachable();
        expectNoOverlap();
        measureTable(`turn end: End turn + Kobo! + first-time match note + log line, ${players} players`);
        // The note's button and my cards stay 44 px targets.
        cy.get('[data-test="match-coach-dismiss"]').invoke('outerHeight').should('be.gte', 44);
        cy.get('[data-test="my-zone"] button[data-test^="slot-"]').each(($b) => {
          expect($b.outerHeight(), 'my card target').to.be.gte(44);
          expect($b.outerWidth(), 'my card target').to.be.gte(44);
        });
        cy.get('[data-test="match-coach-dismiss"]').click();
        cy.get('[data-test="match-hint"]').should('be.visible');
        actionBarIsReachable();
        expectNoOverlap();
        tableDoesNotScrollSideways();
        measureTable(`turn end: End turn + Kobo! + hint line + log line, ${players} players`);

        // A tap on a rival card only picks it: the prompt asks for mine, Cancel joins the bar.
        cy.get('[data-test="opponent-seat"][data-seat="1"] button').first().scrollIntoView().click();
        cy.get('[data-test="prompt"]').should('have.text', 'Pick one of your cards to give');
        cy.get('[data-test="action-cancel"]').should('be.visible');
        cy.get('[data-test="opponent-seat"][data-seat="1"] button').first().should('have.attr', 'aria-pressed', 'true');
        actionBarIsReachable();
        expectNoOverlap();
        tableDoesNotScrollSideways();
        measureTable(`match give step: prompt + End turn + Kobo! + Cancel + log line, ${players} players`);
        cy.get('body').type('{esc}');
        cy.get('[data-test="action-cancel"]').should('not.exist');
      });
    });
  });
});
