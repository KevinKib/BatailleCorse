describe('Rehydrate game from URL', () => {
  it('loads game state when navigating directly to /room/:id', () => {
    cy.createGame();

    // Wait for onMounted's REST fetch to complete before capturing the URL —
    // ensures the game is fully hydrated and the game ID is valid in the backend.
    cy.get('[data-cy="player-card-count"]', { timeout: 10000 }).should('contain.text', '26');

    cy.url().then((gameUrl) => {
      // Navigate away, then come back directly — simulates a page refresh.
      cy.visit('/');
      cy.visit(gameUrl);

      // GameScreen fetches /api/game/:id on mount; should not redirect to /.
      cy.url({ timeout: 10000 }).should('eq', gameUrl);

      cy.get('[data-cy="player-card-count"]', { timeout: 10000 }).should('contain.text', '26');
      cy.get('[data-cy="opponent-card-count"]', { timeout: 10000 }).should('contain.text', '26');
    });
  });
});
