// The shared Game home: Join game for both games, and the room links that cannot be restored.
const GAMES = [
  { key: 'bataillecorse', title: 'Bataille Corse' },
  { key: 'bullshit', title: 'Bullshit' },
];

describe('Game home', () => {
  GAMES.forEach(({ key, title }) => {
    it(`${key}: Join game opens the form, a refused join stays on it with its error, Back returns to the home`, () => {
      cy.visit(`/games/${key}`);
      cy.contains('h1', title).should('be.visible');
      cy.get('[data-test="join-game"]').click();
      cy.location('pathname').should('eq', `/games/${key}/join`);
      cy.get('[data-test="submit"]').should('be.disabled');
      cy.get('[data-test="game-id"]').type('does-not-exist');
      cy.get('[data-test="submit"]').click();
      cy.get('[data-test="join-error"]', { timeout: 10000 })
        .should('be.visible')
        .and('contain.text', 'Could not join this game');
      cy.location('pathname').should('eq', `/games/${key}/join`);
      // The previous view is still fading out for a moment: wait until only this one is left.
      cy.get('[data-test="back"]').should('have.length', 1).click();
      cy.location('pathname').should('eq', `/games/${key}`);
    });

    it(`${key}: a room link without a stored token leads to the join form with the id prefilled`, () => {
      cy.visit(`/games/${key}/room/some-game`);
      cy.location('pathname').should('eq', `/games/${key}/join/some-game`);
      cy.get('[data-test="game-id"]').should('have.value', 'some-game');
    });

    it(`${key}: a stored token for a game that no longer exists leads to the Game home with a notice`, () => {
      const storageKey = key === 'bullshit' ? 'bullshit:tokens:gone-game' : 'tokens:gone-game';
      cy.visit(`/games/${key}/room/gone-game`, {
        onBeforeLoad(win) {
          win.localStorage.setItem(storageKey, JSON.stringify({ 0: 'stale-token' }));
        },
      });
      cy.location('pathname', { timeout: 10000 }).should('eq', `/games/${key}`);
      cy.get('[data-test="gone-notice"]').should('be.visible').and('contain.text', 'This game no longer exists');
      cy.get('[data-test="new-game"]').should('be.visible');
      cy.get('[data-test="join-game"]').should('be.visible');
    });
  });
});
