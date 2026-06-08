describe('Turn indicator', () => {
  it('shows a one-time YOUR TURN hint at the start of the player turn', () => {
    cy.createGame();

    // On a fresh game it is player 0's turn, so the one-time onboarding hint
    // appears and stays for the duration of that first turn (no Send issued here).
    cy.get('[data-cy="turn-hint"]', { timeout: 10000 })
      .should('be.visible')
      .and('contain.text', 'YOUR TURN');
  });
});
