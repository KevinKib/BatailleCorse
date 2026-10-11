// Every way out of a Bullshit game leads to the game picker (/games), never to the Bataille Corse menu.
describe('Bullshit exits lead to /games', () => {
  function createGame() {
    cy.visit('/games');
    cy.get('[data-test="play-bullshit"]').click();
    cy.get('[data-test="name"]').type('Alice');
    // Deliberate fixed wait: a create sent before the WebSocket handshake finishes is reported as an error.
    cy.wait(1500);
    cy.get('[data-test="submit"]').click();
    cy.url({ timeout: 10000 }).should('match', /\/games\/bullshit\/room\/.+/);
  }

  it('goes to /games from the create screen Back', () => {
    cy.visit('/games/bullshit/create');
    cy.get('[data-test="back"]').click();
    cy.location('pathname').should('eq', '/games');
  });

  it('goes to /games from the table Back once the forfeit is confirmed', () => {
    createGame();
    cy.url().then((url) => cy.request('POST', `/api/bullshit/game/${url.split('/room/')[1]}/join`, { name: 'Bob' }));
    cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
    cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
    cy.on('window:confirm', () => true);
    cy.get('[data-test="leave"]').click();
    cy.location('pathname').should('eq', '/games');
    cy.get('[data-test="play-bullshit"]').should('be.visible');
  });
});
