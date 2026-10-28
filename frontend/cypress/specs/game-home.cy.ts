// The shared Game home: Join game for every game, and the room links that cannot be restored.
const GAMES = [
  // Both games word a missing game (404) apart from a refusal (full, already started).
  { key: 'bataillecorse', title: 'Bataille Corse', joinError: 'No game found' },
  { key: 'bullshit', title: 'Bullshit', joinError: 'No game found' },
  { key: 'kobo', title: 'Kobo', joinError: 'No game found' },
  { key: 'three-men-morris', title: 'Morris', joinError: 'No game found' },
  { key: 'president', title: 'President', joinError: 'No game found' },
  { key: 'mangola', title: 'Mangola', joinError: 'No game found' },
  { key: 'ninety-nine', title: '99', joinError: 'No game found' },
];

describe('Game home', () => {
  GAMES.forEach(({ key, title, joinError }) => {
    it(`${key}: Join game opens the form, a refused join stays on it with its error, Back returns to the home`, () => {
      cy.visit(`/${key}`);
      cy.contains('h1', title).should('be.visible');
      cy.get('[data-test="join-game"]').click();
      cy.location('pathname').should('eq', `/${key}/join`);
      cy.get('[data-test="submit"]').should('be.disabled');
      cy.get('[data-test="game-id"]').type('does-not-exist');
      cy.get('[data-test="submit"]').click();
      cy.get('[data-test="join-error"]', { timeout: 10000 })
        .should('be.visible')
        .and('contain.text', joinError);
      cy.location('pathname').should('eq', `/${key}/join`);
      // The previous view is still fading out for a moment: wait until only this one is left.
      cy.get('[data-test="back"]').should('have.length', 1).click();
      cy.location('pathname').should('eq', `/${key}`);
    });

    it(`${key}: a room link without a stored token leads to the join form with the id prefilled`, () => {
      cy.visit(`/${key}/room/some-game`);
      cy.location('pathname').should('eq', `/${key}/join/some-game`);
      cy.get('[data-test="game-id"]').should('have.value', 'some-game');
    });

    it(`${key}: a stored token for a game that no longer exists leads to the Game home with a notice`, () => {
      // A well-formed id the server does not know: only a 404 means "gone" (a 500 must not pass for it).
      const goneId = '11111111-1111-4111-8111-111111111111';
      const storageKey = key === 'bataillecorse' ? `tokens:${goneId}` : `${key}:tokens:${goneId}`;
      cy.visit(`/${key}/room/${goneId}`, {
        onBeforeLoad(win) {
          win.localStorage.setItem(storageKey, JSON.stringify({ 0: '00000000-0000-4000-8000-000000000000' }));
        },
      });
      cy.location('pathname', { timeout: 10000 }).should('eq', `/${key}`);
      cy.get('[data-test="gone-notice"]').should('be.visible').and('contain.text', 'This game no longer exists');
      cy.get('[data-test="new-game"]').should('be.visible');
      cy.get('[data-test="join-game"]').should('be.visible');
    });
  });
});

describe('Game home creates a Bullshit lobby directly', () => {
  it('New game lands in the lobby with the typed name, without any create page', () => {
    cy.visit('/bullshit');
    cy.get('[data-test="name"]').type('Alice');
    cy.get('[data-test="new-game"]').click();
    cy.location('pathname', { timeout: 10000 }).should('match', /^\/bullshit\/room\/.+/);
    cy.get('[data-test="lobby"]').should('be.visible');
    cy.get('input[data-test="rename"]').should('have.value', 'Alice');
  });

  it('a stored token the server refuses leads to the Game home with the lost-seat notice', () => {
    cy.visit('/bullshit');
    cy.get('[data-test="name"]').type('Alice');
    cy.get('[data-test="new-game"]').click();
    cy.location('pathname', { timeout: 10000 }).should('match', /^\/bullshit\/room\/.+/);
    cy.get('[data-test="lobby"]').should('be.visible');
    cy.location('pathname').then((path) => {
      const id = path.split('/room/')[1];
      // The game is alive but the token no longer resolves to a seat.
      cy.window().then((win) => win.localStorage.setItem(`bullshit:tokens:${id}`, JSON.stringify({ 0: '00000000-0000-4000-8000-000000000000' })));
      cy.visit(path);
      cy.location('pathname', { timeout: 10000 }).should('eq', '/bullshit');
      cy.get('[data-test="lost-notice"]').should('be.visible').and('contain.text', 'Your seat in that game was lost');
      cy.get('[data-test="gone-notice"]').should('not.exist');
    });
  });
});
