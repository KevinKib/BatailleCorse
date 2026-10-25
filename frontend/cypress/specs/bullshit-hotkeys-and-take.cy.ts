// Keyboard shortcuts of the Bullshit table, the card selection feedback and the pile flight to its taker.
function startTwoPlayerGame() {
  // Enter through the menu so the WebSocket is connected before we create (see bullshit-table-and-lobby).
  cy.visit('/');
  cy.get('[data-test="play-bullshit"]').click();
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/bullshit\/room\/.+/);
  // The Game home fades out while the room fades in: continue once the room is alone.
  cy.get('[data-test="start-panel"]').should('not.exist');
  cy.url().then((url) => cy.request('POST', `/api/bullshit/game/${url.split('/room/')[1]}/join`, { name: 'Bob' }));
  cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
  cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
}

[{ width: 375, height: 667 }, { width: 1280, height: 800 }].forEach(({ width, height }) => {
  describe(`Bullshit shortcuts and pile flight at ${width}px`, () => {
    beforeEach(() => {
      cy.viewport(width, height);
      startTwoPlayerGame();
    });

    it('has no digit shortcut, discards the clicked cards with D, and never scrolls', () => {
      cy.get('[data-test="hand-card-0"]').should('have.attr', 'aria-pressed', 'false');
      cy.get('body').type('120');
      cy.get('[data-test="hand-card-0"]').should('have.attr', 'aria-pressed', 'false');
      cy.get('[data-test="hand-card-1"]').should('have.attr', 'aria-pressed', 'false');
      cy.get('[data-test="hand-card-0"]').click({ force: true });
      cy.get('[data-test="hand-card-1"]').click({ force: true });
      cy.get('[data-test="hand-card-0"]').should('have.attr', 'aria-pressed', 'true');
      cy.get('[data-test="hand-card-1"]').should('have.attr', 'aria-pressed', 'true');
      cy.get('[data-test="hand-card-1"]').click({ force: true });
      cy.get('[data-test="hand-card-1"]').should('have.attr', 'aria-pressed', 'false');
      cy.get('[data-test="discard"]').should('have.attr', 'aria-keyshortcuts', 'd').and('not.be.disabled');
      cy.get('body').type('d');
      cy.get('[data-test="last-play"]').should('exist');
      cy.get('[data-test="hand-card-0"]').should('have.attr', 'aria-pressed', 'false');
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

    it('documents the shortcuts discreetly where a keyboard is likely', () => {
      cy.get('[data-test="call"]').should('have.attr', 'aria-keyshortcuts', 'b');
      cy.get('[data-test="keys-hint"]').should('contain.text', 'D discard').and('contain.text', 'B call Bullshit')
        .and('not.contain.text', '1');
    });

    it('flies the pile to the player who takes it once the verdict is gone', () => {
      cy.document().then((doc) => {
        (doc as any).__pileFlights = 0;
        new MutationObserver((records) => {
          records.forEach((r) => r.addedNodes.forEach((n) => {
            if ((n as HTMLElement).dataset?.test === 'motion-card') (doc as any).__pileFlights++;
          }));
        }).observe(doc.body, { childList: true, subtree: true });
      });
      cy.window().then((w) => {
        const pinia = (w.document.querySelector('#app') as any).__vue_app__.config.globalProperties.$pinia;
        const store = pinia._s.get('bullshit-store');
        store.applyEvent({
          type: 'event', eventType: 'CALL_BULLSHIT',
          eventData: {
            callerSeat: 0, claimantSeat: 1, truthful: false, pickerSeat: 1,
            revealedCards: store.game.myHand.slice(0, 2),
          },
        });
      });
      cy.get('[data-test="reveal"]').should('exist');
      cy.get('[data-test="reveal"]', { timeout: 6000 }).should('not.exist');
      cy.document().should((doc) => expect((doc as any).__pileFlights).to.be.greaterThan(0));
      cy.get('[data-test="motion-card"]', { timeout: 3000 }).should('not.exist');
    });
  });
});
