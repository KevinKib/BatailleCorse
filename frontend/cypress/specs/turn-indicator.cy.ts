describe('Turn indicator', () => {
  it('shows a one-time YOUR TURN hint at the start of the player turn', () => {
    cy.createGame();

    // On a fresh game it is player 0's turn, so the one-time onboarding hint
    // appears (it auto-dismisses after a couple of seconds, so assert promptly).
    cy.get('[data-cy="turn-hint"]', { timeout: 10000 })
      .should('be.visible')
      .and('contain.text', 'YOUR TURN');
  });
});
