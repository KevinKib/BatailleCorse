describe('Send card', () => {
  it('decrements player hand count and adds a card to the pile', () => {
    cy.createGame();

    // Wait for the game to be hydrated before clicking — onMounted fetches game state
    // asynchronously; the button stays disabled until hydrate() sets state.value.
    cy.contains('button', 'Send').should('not.be.disabled').click();

    // Player 0 sent one card: hand drops from 26 to 25.
    cy.get('[data-cy="player-card-count"]', { timeout: 10000 }).should('contain.text', '25');

    // Pile has at least one card (may have more if AI acted within 2100ms, which is unlikely).
    cy.get('[data-cy="pile-card-count"]', { timeout: 10000 })
      .invoke('text')
      .then((text) => parseInt(text.trim(), 10))
      .should('be.gte', 1);
  });
});
