declare global {
  namespace Cypress {
    interface Chainable {
      /** A Bataille Corse table against a server bot: New game, Add bot at `tier` (Training by default), Start. */
      createGame(options?: { tier?: string }): Chainable<void>;
      /** Opens a Bataille Corse lobby from the Game home and resolves its game id. */
      openBatailleCorseLobby(name?: string): Chainable<string>;
    }
  }
}

Cypress.Commands.add('openBatailleCorseLobby', (name?: string) => {
  cy.visit('/bataillecorse');
  if (name) cy.get('[data-test="name"]').clear().type(name);
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/bataillecorse\/room\/.+/);
  // The lobby is drawn from the server's answer on my seat channel: the room is live once it shows.
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  return cy.location('pathname').then((path) => path.split('/room/')[1]);
});

Cypress.Commands.add('createGame', (options?: { tier?: string }) => {
  const tier = options?.tier ?? '0';
  cy.openBatailleCorseLobby().then(() => {
    cy.get('[data-test="add-bot"]').click();
    cy.get('[data-test="seat-option-1-tier"]').should('be.visible').select(tier);
    // The server handles one client's commands in the order sent: Start cannot overtake the tier change.
    cy.get('[data-test="start"]').should('not.be.disabled').click();
    cy.get('[data-cy="player-card-count"]', { timeout: 10000 }).should('contain.text', '26');
    // The lobby fades out while the table fades in: the table is settled (and measurable) once it is alone.
    cy.get('[data-test="lobby"]').should('not.exist');
  });
});

export {};
