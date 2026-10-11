// Last turn of a Bullshit game: the table, the verdict and the pile flight play out before the end
// screen, which names the winner. The winning verdict is injected into the store (the real call
// path is covered by the unit tests; a real game cannot be finished quickly).
function startTwoPlayerGame() {
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
  describe(`Bullshit end screen at ${width}px`, () => {
    beforeEach(() => {
      cy.viewport(width, height);
      startTwoPlayerGame();
    });

    it('lets the winning verdict play out, then names the winner, without scrolling', () => {
      // Deliberate wait: let the server's own state pushes after the start settle, or they would overwrite the injected state.
      cy.wait(2000);
      cy.window().then((w) => {
        const pinia = (w.document.querySelector('#app') as any).__vue_app__.config.globalProperties.$pinia;
        const store = pinia._s.get('bullshit-store');
        // Bob (seat 1) wins: the state is already finished when the verdict arrives.
        store.applyEvent({ type: 'state-update', state: { ...store.game, availableActions: [],
          outcome: { status: 'FINISHED', winnerId: '1' } } });
        store.applyEvent({ type: 'event', eventType: 'CALL_BULLSHIT', message: '', eventData: {
          callerSeat: 0, claimantSeat: 1, truthful: true, pickerSeat: 0,
          revealedCards: store.game.myHand.slice(0, 1),
        } });
      });
      cy.get('[data-test="reveal"]').should('be.visible');
      cy.get('[data-test="verdict"]').should('be.visible');
      cy.get('[data-test="end"]').should('not.exist');
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-test="end-winner"]').should('contain.text', 'Winner:').and('contain.text', 'Bob');
      cy.get('[data-cy="play-again"]').should('be.visible');
      cy.contains('Back to home').should('be.visible');
      cy.window().then((w) => {
        expect(w.document.documentElement.scrollHeight).to.be.at.most(w.innerHeight);
      });
    });
  });
});
