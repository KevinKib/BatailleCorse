describe('Rules panel', () => {
  it('opens from the toggle and closes with the close button', () => {
    cy.createGame();

    // Collapsed by default (v-show keeps it in the DOM but hidden).
    cy.get('[data-cy="rules-panel"]').should('not.be.visible');

    // Open and show content.
    cy.get('[data-cy="rules-toggle"]').click();
    cy.get('[data-cy="rules-panel"]')
      .should('be.visible')
      .and('contain.text', 'How to play')
      .and('contain.text', 'Doubles');

    // Close via the close button.
    cy.get('[data-cy="rules-close"]').click();
    cy.get('[data-cy="rules-panel"]').should('not.be.visible');
  });

  it('closes with the Escape key', () => {
    cy.createGame();

    cy.get('[data-cy="rules-toggle"]').click();
    cy.get('[data-cy="rules-panel"]').should('be.visible');

    cy.get('body').type('{esc}');
    cy.get('[data-cy="rules-panel"]').should('not.be.visible');
  });
});
