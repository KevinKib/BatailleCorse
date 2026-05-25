declare global {
  namespace Cypress {
    interface Chainable {
      createGame(): Chainable<void>;
    }
  }
}

Cypress.Commands.add('createGame', () => {
  // Visit the lobby first so the WebSocket connection is established before navigating
  // to /create via client-side routing. A direct cy.visit('/create') would reload the
  // page mid-handshake, causing publish() to throw "no underlying STOMP connection".
  cy.visit('/');
  cy.contains('button', 'New Game').click();
  cy.contains('button', 'Deal Cards').click();
  cy.url({ timeout: 10000 }).should('match', /\/room\/.+/);
});
