// Keyboard shortcuts gear on the Bataille Corse table: remap Send and Slap, never scroll.
const noScroll = () => cy.window().then((w) => {
  expect(w.document.documentElement.scrollHeight).to.be.at.most(w.innerHeight);
  expect(w.document.documentElement.scrollWidth).to.be.at.most(w.innerWidth);
});

[{ width: 375, height: 667 }, { width: 1280, height: 800 }].forEach(({ width, height }) => {
  describe(`Bataille Corse hotkey settings at ${width}px`, () => {
    beforeEach(() => {
      cy.viewport(width, height);
      cy.clearLocalStorage();
      cy.createGame();
      cy.contains('button', 'Send').should('not.be.disabled');
    });

    it('keeps the timer, rules, counters and actions, adds the gear without overlap, never scrolls', () => {
      cy.get('[data-test="hotkeys-button"]').should('be.visible');
      cy.get('[data-cy="opponent-card-count"], [data-cy="pile-card-count"], [data-cy="player-card-count"]')
        .should('have.length', 3).and('be.visible');
      cy.contains('button', 'Send').should('be.visible');
      cy.contains('button', 'Slap').should('be.visible');
      cy.contains('button', 'Rules').should('be.visible');
      // The gear overlaps nothing else on the table.
      cy.get('[data-test="hotkeys-button"]').then(($gear) => {
        const g = $gear[0].getBoundingClientRect();
        ['[data-cy="game-timer"]', '.player_tag', 'button:contains("Rules")', 'button:contains("Back")',
          'button:contains("Send")', 'button:contains("Slap")'].forEach((selector) => {
          const el = Cypress.$(selector)[0];
          if (!el) return;
          const r = el.getBoundingClientRect();
          expect(g.right <= r.left || g.left >= r.right || g.bottom <= r.top || g.top >= r.bottom, `gear clear of ${selector}`).to.eq(true);
        });
      });
      noScroll();
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('[data-test="hotkeys-panel"]').should('be.visible');
      cy.get('[data-test="hotkey-send-key"]').should('have.text', 'Q');
      cy.get('[data-test="hotkey-slap-key"]').should('have.text', 'D');
      noScroll();
    });

    it('remaps Send, and the old key no longer works', () => {
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('[data-test="hotkey-send-change"]').click();
      cy.get('body').type('x');
      cy.get('[data-test="hotkey-send-key"]').should('have.text', 'X');
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('body').type('q');
      cy.get('[data-cy="player-card-count"]').should('contain.text', '26');
      cy.get('body').type('x');
      cy.get('[data-cy="player-card-count"]', { timeout: 10000 }).should('contain.text', '25');
    });

    it('refuses the key already used by Slap and keeps the old one', () => {
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('[data-test="hotkey-send-change"]').click();
      cy.get('body').type('d');
      cy.get('[data-test="hotkeys-error"]').should('be.visible');
      cy.get('[data-test="hotkey-send-key"]').should('have.text', 'Q');
    });

    it('remembers the new key after a reload', () => {
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('[data-test="hotkey-slap-change"]').click();
      cy.get('body').type('k');
      cy.reload();
      cy.get('[data-test="hotkeys-button"]', { timeout: 10000 }).click();
      cy.get('[data-test="hotkey-slap-key"]').should('have.text', 'K');
    });
  });
});
