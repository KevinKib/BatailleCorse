describe('Create game', () => {
  it('navigates to game screen and shows initial game state', () => {
    cy.visit('/games/bataillecorse');

    cy.contains('button', 'New Game').click();
    cy.url().should('include', '/create');

    // Human is the default opponent; "Deal Cards" is the solo flow.
    cy.contains('button', 'Computer').click();

    cy.contains('button', 'Deal Cards').click();

    // The store watches for a WebSocket CREATE response and navigates on gameId.
    cy.url({ timeout: 10000 }).should('match', /\/room\/.+/);

    // Initial state: 52 cards split evenly, pile empty.
    cy.get('[data-cy="player-card-count"]').should('contain.text', '26');
    cy.get('[data-cy="opponent-card-count"]').should('contain.text', '26');
    cy.get('[data-cy="pile-card-count"]').should('contain.text', '0');
  });
});
