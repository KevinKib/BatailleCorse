describe('Turn indicator', () => {
  it('shows the YOUR TURN caption to the player whose turn it is', () => {
    cy.createGame();

    // Wait until the game has hydrated (Send becomes enabled on player 0's turn).
    cy.contains('button', 'Send').should('not.be.disabled');

    // On a fresh game it is player 0's turn, so the self-only caption is visible.
    cy.get('[data-cy="turn-indicator"]', { timeout: 10000 })
      .should('be.visible')
      .and('contain.text', 'YOUR TURN');
  });
});
