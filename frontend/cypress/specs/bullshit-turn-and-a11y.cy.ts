// Turn cue, accessible hand and verdict region, and the end-of-game line on the Bullshit table.
function startTwoPlayerGame() {
  // Enter through the menu so the WebSocket is connected before we create (see bullshit-table-and-lobby).
  cy.visit('/games');
  cy.get('[data-test="play-bullshit"]').click();
  cy.get('[data-test="name"]').type('Alice');
  // Deliberate fixed wait: a create sent before the WebSocket handshake finishes is reported as an error.
  cy.wait(1500);
  cy.get('[data-test="submit"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/bullshit\/room\/.+/);
  cy.url().then((url) => cy.request('POST', `/api/bullshit/game/${url.split('/room/')[1]}/join`, { name: 'Bob' }));
  cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
  cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
}

[{ width: 375, height: 667 }, { width: 1280, height: 800 }].forEach(({ width, height }) => {
  describe(`Bullshit turn cue and accessibility at ${width}px`, () => {
    beforeEach(() => {
      cy.viewport(width, height);
      startTwoPlayerGame();
    });

    it('says "Your turn", pulses Discard once a card is picked, and never scrolls', () => {
      cy.get('[data-test="turn-hint"]').should('be.visible').and('have.text', 'Your turn');
      cy.get('[data-test="discard"]').should('not.have.class', 'discard--pulse');
      cy.get('[data-test="hand-card-0"]').click({ force: true });
      cy.get('[data-test="discard"]').should('have.class', 'discard--pulse').and('not.be.disabled');
      cy.window().then((w) => {
        expect(w.document.documentElement.scrollHeight).to.be.at.most(w.innerHeight);
      });
      // Information that was already on the table is still there.
      cy.get('[data-test="claim-badge"]').should('be.visible');
      cy.get('[data-test="my-count"]').should('be.visible');
      cy.get('[data-test="leave"]').should('be.visible');
      cy.get('[data-test="discard"]').should('be.visible');
      cy.get('[data-test="call"]').should('be.visible');
    });

    it('exposes the hand cards as named toggle buttons and keeps a polite verdict region', () => {
      cy.get('[data-test="hand-card-0"]')
        .should('have.attr', 'aria-label').and('match', /^\w+ of \w+$/);
      cy.get('[data-test="hand-card-0"]').should('have.attr', 'aria-pressed', 'false').click({ force: true });
      cy.get('[data-test="hand-card-0"]').should('have.attr', 'aria-pressed', 'true');
      cy.get('[data-test="verdict-live"]').should('have.attr', 'aria-live', 'polite').and('have.text', '');
    });

    it('says how the opponent left on the winner end screen', () => {
      cy.window().then((w) => {
        const pinia = (w.document.querySelector('#app') as any).__vue_app__.config.globalProperties.$pinia;
        const store = pinia._s.get('bullshit-store');
        store.applyEvent({ type: 'event', eventType: 'FORFEIT', eventData: { loserSeat: 1, reason: 'RESIGNED' }, message: '' });
        store.applyEvent({ type: 'state-update', state: { ...store.state, players: [store.state.players[0]], outcome: { status: 'FINISHED', winnerId: '0' } } });
      });
      cy.get('[data-test="end"]').should('be.visible').and('contain.text', 'Bob resigned.');
      cy.get('[data-cy="play-again"]').should('be.visible').and('not.be.disabled');
    });
  });
});
