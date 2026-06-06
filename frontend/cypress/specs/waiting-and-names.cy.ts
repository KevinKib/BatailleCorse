describe('2-player waiting and names', () => {
  it('keeps the waiting overlay visible after creating a Human game', () => {
    cy.visit('/');
    cy.contains('button', 'New Game').click();
    cy.url().should('include', '/create');

    cy.contains('button', 'Human').click();
    cy.get('#playerName').type('Alice');
    cy.contains('button', 'Create Game').click();

    // The overlay must stay up — the bug was it flashed and disappeared.
    cy.contains('Waiting for opponent', { timeout: 10000 }).should('be.visible');
    cy.contains('button', 'Copy').should('be.visible');
    // Deliberate fixed wait: the bug was the overlay vanishing on its own a moment
    // after creation, so we assert it is still present after a short delay.
    cy.wait(1500);
    cy.contains('Waiting for opponent').should('be.visible');
  });

  it('shows the opponent name once a second player joins', () => {
    cy.visit('/');
    cy.contains('button', 'New Game').click();
    cy.contains('button', 'Human').click();
    cy.get('#playerName').type('Alice');
    cy.contains('button', 'Create Game').click();

    cy.contains('Waiting for opponent', { timeout: 10000 }).should('be.visible');

    cy.url().should('match', /\/room\/.+/).then((url) => {
      const id = url.split('/room/')[1];
      // cy.request sends a JSON body with application/json by default.
      cy.request('POST', `/api/game/${id}/join`, { name: 'Bob' });
    });

    cy.contains('Waiting for opponent').should('not.exist');
    cy.contains('Bob').should('be.visible');
  });
});
