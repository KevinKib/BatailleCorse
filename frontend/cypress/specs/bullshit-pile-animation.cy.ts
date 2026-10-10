// Playing cards flies them from the hand to the pile; a toggle mutes the card sound.
function createRoom(name: string) {
  // Enter through the menu so the WebSocket is connected before we create (see bullshit-table-and-lobby).
  cy.visit('/games');
  cy.get('[data-test="play-bullshit"]').click();
  cy.get('[data-test="name"]').type(name);
  // Deliberate fixed wait: a create sent before the WebSocket handshake finishes is reported as an error.
  cy.wait(1500);
  cy.get('[data-test="submit"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/bullshit\/room\/.+/);
  return cy.url().then((url) => url.split('/room/')[1]);
}

function startGame() {
  cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
  cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
}

describe('Bullshit card-to-pile animation and sound', () => {
  it('flies the played card to the pile without ever scrolling, and keeps the preserved UI', () => {
    createRoom('Alice').then((id) => {
      cy.request('POST', `/api/bullshit/game/${id}/join`, { name: 'Bob' });
      startGame();
      cy.get('[data-test="hand-card-0"]').click();
      cy.get('[data-test="discard"]').click();
      cy.get('[data-test="flying-card"]').should('exist');
      cy.get('[data-test="flying-card"]', { timeout: 3000 }).should('not.exist');
      cy.window().then((w) => {
        expect(w.document.documentElement.scrollHeight).to.be.at.most(w.innerHeight);
      });
      // Information that was already on the table is still there.
      cy.get('[data-test="claim-badge"]').should('be.visible');
      cy.get('[data-test="my-count"]').should('be.visible');
      cy.get('[data-test="leave"]').should('be.visible');
    });
  });

  it('mutes the sound from the top bar and remembers it after a reload', () => {
    createRoom('Alice').then((id) => {
      cy.request('POST', `/api/bullshit/game/${id}/join`, { name: 'Bob' });
      startGame();
      cy.get('[data-test="sound-toggle"]').should('have.attr', 'aria-pressed', 'true').click();
      cy.get('[data-test="sound-toggle"]').should('have.attr', 'aria-pressed', 'false');
      cy.reload();
      cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
      cy.get('[data-test="sound-toggle"]').should('have.attr', 'aria-pressed', 'false');
    });
  });
});
