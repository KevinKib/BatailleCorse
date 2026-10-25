// Every way out of a Bullshit game leads to the game picker (/), never to the Bataille Corse menu.
// (New game opens the create screen: the Game home asks for the name first.)
describe('Bullshit exits lead to the game picker (/)', () => {
  function createGame() {
    cy.visit('/');
    cy.get('[data-test="play-bullshit"]').click();
    cy.get('[data-test="name"]').type('Alice');
    cy.get('[data-test="new-game"]').click();
    cy.url({ timeout: 10000 }).should('match', /\/bullshit\/room\/.+/);
  }

  it('goes to / from the create screen Back', () => {
    cy.visit('/bullshit/create');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/');
  });

  it('goes to / from the table Back once the forfeit is confirmed', () => {
    createGame();
    cy.url().then((url) => cy.request('POST', `/api/bullshit/game/${url.split('/room/')[1]}/join`, { name: 'Bob' }));
    cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
    cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
    cy.on('window:confirm', () => true);
    cy.get('[data-test="leave"]').click();
    cy.location('pathname').should('eq', '/');
    cy.get('[data-test="play-bullshit"]').should('be.visible');
  });
});
