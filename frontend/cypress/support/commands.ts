declare global {
  namespace Cypress {
    interface Chainable {
      /** A Bataille Corse table against a server bot: New game, Add bot at `tier` (Training by default), Start. */
      createGame(options?: { tier?: string }): Chainable<void>;
      /** Opens a Bataille Corse lobby from the Game home and resolves its game id. */
      openBatailleCorseLobby(name?: string): Chainable<string>;
      /** Waits until the server holds `tier` for the bot of seat 1 of game `gameId` (read with my stored token). */
      botTierIs(gameId: string, tier: string): Chainable<void>;
    }
  }
}

Cypress.Commands.add('openBatailleCorseLobby', (name?: string) => {
  cy.visit('/games/bataillecorse');
  if (name) cy.get('[data-test="name"]').clear().type(name);
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/bataillecorse\/room\/.+/);
  // The lobby is drawn from the server's answer on my seat channel: the room is live once it shows.
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  return cy.location('pathname').then((path) => path.split('/room/')[1]);
});

Cypress.Commands.add('botTierIs', (gameId: string, tier: string) => {
  cy.window().then((win) => {
    const token = Object.values(JSON.parse(win.localStorage.getItem(`tokens:${gameId}`)!))[0] as string;
    const check = (attempt: number): void => {
      cy.request(`/api/game/${gameId}?token=${token}`).then((res) => {
        const bot = (res.body.players as Array<{ seat: number; seatOptions?: Record<string, string> }>)
          .find((p) => p.seat === 1);
        if (bot?.seatOptions?.tier === tier) return;
        if (attempt >= 50) throw new Error(`bot tier never became ${tier}`);
        cy.wait(100);
        check(attempt + 1);
      });
    };
    check(0);
  });
});

Cypress.Commands.add('createGame', (options?: { tier?: string }) => {
  const tier = options?.tier ?? '0';
  cy.openBatailleCorseLobby().then((gameId) => {
    cy.get('[data-test="add-bot"]').click();
    cy.get('[data-test="seat-option-1-tier"]').should('be.visible').select(tier);
    // Start only once the server holds the tier: the two commands travel on separate broker threads.
    cy.botTierIs(gameId, tier);
    cy.get('[data-test="start"]').should('not.be.disabled').click();
    cy.get('[data-cy="player-card-count"]', { timeout: 10000 }).should('contain.text', '26');
  });
});

export {};
