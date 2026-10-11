// Configurable keyboard shortcuts of the Bullshit table (WCAG 2.1.4): remap, turn off, keep the rest.
function startTwoPlayerGame() {
  cy.visit('/games');
  cy.get('[data-test="play-bullshit"]').click();
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url().should('include', '/games/bullshit/new');
  // Deliberate fixed wait: a create sent before the WebSocket handshake finishes is reported as an error.
  cy.wait(1500);
  cy.get('[data-test="submit"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/bullshit\/room\/.+/);
  cy.url().then((url) => cy.request('POST', `/api/bullshit/game/${url.split('/room/')[1]}/join`, { name: 'Bob' }));
  cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
  cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
}

const noScroll = () => cy.window().then((w) => {
  expect(w.document.documentElement.scrollHeight).to.be.at.most(w.innerHeight);
  expect(w.document.documentElement.scrollWidth).to.be.at.most(w.innerWidth);
});

[{ width: 375, height: 667 }, { width: 1280, height: 800 }].forEach(({ width, height }) => {
  describe(`Bullshit hotkey settings at ${width}px`, () => {
    beforeEach(() => {
      cy.viewport(width, height);
      cy.clearLocalStorage();
      startTwoPlayerGame();
    });

    it('keeps everything already on the table and the sound toggle, adds the settings button, never scrolls', () => {
      ['leave', 'sound-toggle', 'hotkeys-button', 'discard', 'call', 'claim-badge', 'my-count'].forEach((id) =>
        cy.get(`[data-test="${id}"]`).should('be.visible'));
      cy.get('[data-test="keys-hint"]').should('exist');
      noScroll();
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('[data-test="hotkeys-panel"]').should('be.visible');
      noScroll();
    });

    it('remaps Discard, and the old key no longer works', () => {
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('[data-test="hotkey-discard-change"]').click();
      cy.get('body').type('x');
      cy.get('[data-test="hotkey-discard-key"]').should('have.text', 'X');
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('[data-test="discard"]').should('have.attr', 'aria-keyshortcuts', 'x');
      cy.get('[data-test="keys-hint"]').should('contain.text', 'X discard');
      cy.get('body').type('1d');
      cy.get('[data-test="last-play"]').should('not.exist');
      cy.get('body').type('x');
      cy.get('[data-test="last-play"]').should('exist');
    });

    it('refuses a duplicate key', () => {
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('[data-test="hotkey-discard-change"]').click();
      cy.get('body').type('b');
      cy.get('[data-test="hotkeys-error"]').should('be.visible');
      cy.get('[data-test="hotkey-discard-key"]').should('have.text', 'D');
    });

    it('turns every shortcut off, digits included, and remembers it after a reload', () => {
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('[data-test="hotkeys-enabled"]').click();
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('[data-test="keys-hint"]').should('not.exist');
      cy.get('[data-test="discard"]').should('not.have.attr', 'aria-keyshortcuts');
      cy.get('body').type('1');
      cy.get('[data-test="hand-card-0"]').should('have.attr', 'aria-pressed', 'false');
      cy.reload();
      cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
      cy.get('[data-test="keys-hint"]').should('not.exist');
    });
  });
});
