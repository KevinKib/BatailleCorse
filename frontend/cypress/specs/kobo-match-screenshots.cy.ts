// Screenshots of the Kobo match flow for the PR (docs/screenshots/kobo-match-option-b/). Skipped in a normal run:
//   npx cypress run --env screenshots=true --spec cypress/specs/kobo-match-screenshots.cy.ts \
//     --config screenshotsFolder=../docs/screenshots/kobo-match-option-b/after
// Written to work on the table before and after the change (the first-time note only exists after it).
import { dealWithTopCard, startKoboTable } from '../support/koboRoom';

const cases = [
  { width: 375, height: 667 },
  { width: 1280, height: 800 },
] as const;

const describeIf = Cypress.env('screenshots') ? describe : describe.skip;

describeIf('Kobo match flow screenshots (6 players)', () => {
  cases.forEach(({ width, height }) => {
    it(`captures the idle table and the rival pick at ${width}x${height}`, () => {
      cy.viewport(width, height);
      startKoboTable(6, dealWithTopCard(6, '3S')).then(({ guests }) => {
        cy.get('[data-test="action-ready"]').click();
        cy.then(() => guests.forEach((g) => g.publish('/app/kobo/ready', {})));
        cy.get('[data-test="action-draw"]').should('be.visible').click();
        cy.get('[data-test="action-discard"]').click();
        cy.get('[data-test="action-endTurn"]').should('be.visible');
        cy.wait(700);
        cy.screenshot(`${width}x${height}-1-idle`, { capture: 'viewport', overwrite: true });
        cy.get('body').then(($body) => {
          if ($body.find('[data-test="match-coach-dismiss"]').length > 0) {
            cy.get('[data-test="match-coach-dismiss"]').click();
            cy.screenshot(`${width}x${height}-2-idle-after-got-it`, { capture: 'viewport', overwrite: true });
          }
        });
        cy.get('[data-test="opponent-seat"][data-seat="1"] button').first().scrollIntoView().click();
        cy.get('[data-test="prompt"]').should('have.text', 'Pick one of your cards to give');
        cy.screenshot(`${width}x${height}-3-rival-picked`, { capture: 'viewport', overwrite: true });
      });
    });
  });
});
