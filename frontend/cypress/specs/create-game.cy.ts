describe('Create game', () => {
  it('New game opens the lobby, a bot fills the second seat, Start deals the cards', () => {
    cy.openBatailleCorseLobby().then((gameId) => {
      cy.url().should('include', `/bataillecorse/room/${gameId}`);
      cy.get('[data-test="start"]').should('be.disabled');
      cy.get('[data-test="add-bot"]').click();
      cy.get('[data-test="start"]').should('not.be.disabled').click();
    });

    // Initial state: 52 cards split evenly, pile empty.
    cy.get('[data-cy="player-card-count"]', { timeout: 10000 }).should('contain.text', '26');
    cy.get('[data-cy="opponent-card-count"]').should('contain.text', '26');
    cy.get('[data-cy="pile-card-count"]').should('contain.text', '0');
    // The default tier of a new bot is Bronze.
    cy.get('.player_tag').first().should('have.text', 'Bot 1 (Bronze)');
  });
});
