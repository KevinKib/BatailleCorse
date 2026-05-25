describe('Rehydrate game from URL', () => {
  it('loads game state when navigating directly to /room/:id', () => {
    cy.createGame();

    cy.url().then((gameUrl) => {
      // Navigate away, then come back directly — simulates a page refresh.
      cy.visit('/');
      cy.visit(gameUrl);

      // GameScreen fetches /api/game/:id on mount; should not redirect to /.
      cy.url({ timeout: 10000 }).should('eq', gameUrl);

      cy.get('[data-cy="player-card-count"]').should('contain.text', '26');
      cy.get('[data-cy="opponent-card-count"]').should('contain.text', '26');
    });
  });
});
