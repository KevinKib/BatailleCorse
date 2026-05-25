describe('Create game', () => {
  it('navigates to game screen and shows initial game state', () => {
    cy.visit('/');

    cy.contains('button', 'New Game').click();
    cy.url().should('include', '/create');

    cy.contains('button', 'Deal Cards').click();

    // The store watches for a WebSocket CREATE response and navigates on gameId.
    cy.url().should('match', /\/room\/.+/);

    // Initial state: 52 cards split evenly, pile empty.
    cy.get('[data-cy="player-card-count"]').should('contain.text', '26');
    cy.get('[data-cy="opponent-card-count"]').should('contain.text', '26');
    cy.get('[data-cy="pile-card-count"]').should('contain.text', '0');
  });
});
