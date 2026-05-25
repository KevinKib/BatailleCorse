declare global {
  namespace Cypress {
    interface Chainable {
      createGame(): Chainable<void>;
    }
  }
}

Cypress.Commands.add('createGame', () => {
  cy.visit('/create');
  cy.contains('button', 'Deal Cards').click();
  cy.url().should('match', /\/room\/.+/);
});
