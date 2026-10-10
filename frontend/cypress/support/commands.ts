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
  cy.visit('/games/bataillecorse');
  cy.contains('button', 'New Game').click();
  // Deliberate fixed wait: no "connected" signal is exposed and a create sent before the
  // WebSocket handshake ends throws "no underlying STOMP connection".
  cy.wait(1500);
  // Human is the default opponent; the solo flow ("Deal Cards") needs the Computer.
  cy.contains('button', 'Computer').click();
  cy.contains('button', 'Deal Cards').click();
  cy.url({ timeout: 10000 }).should('match', /\/room\/.+/);
});
