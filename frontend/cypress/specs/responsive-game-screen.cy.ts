describe('Responsive game screen (phone portrait)', () => {
  const VIEWPORT_W = 390;
  const VIEWPORT_H = 844;

  beforeEach(() => {
    cy.viewport(VIEWPORT_W, VIEWPORT_H);
    cy.createGame();
    // Wait for the active board (the one-time YOUR TURN hint only renders there).
    cy.get('[data-cy="turn-hint"]', { timeout: 10000 }).should('exist');
  });

  it('fits the whole board on screen with no scrolling', () => {
    cy.document().then((doc) => {
      const el = doc.scrollingElement as Element;
      // +1 tolerance for sub-pixel rounding.
      expect(el.scrollWidth, 'no horizontal overflow').to.be.at.most(el.clientWidth + 1);
      expect(el.scrollHeight, 'no vertical overflow').to.be.at.most(el.clientHeight + 1);
    });
  });

  it('keeps both decks and the pile visible', () => {
    cy.get('[data-cy="opponent-card-count"]').should('be.visible');
    cy.get('[data-cy="pile-card-count"]').should('be.visible');
    cy.get('[data-cy="player-card-count"]').should('be.visible');
  });

  it('keeps Send and Slap fully on-screen and clickable', () => {
    cy.contains('button', 'Send').should('be.visible');
    cy.contains('button', 'Slap').should('be.visible');
    cy.contains('button', 'Slap').then(($b) => {
      const r = $b[0].getBoundingClientRect();
      expect(r.right, 'Slap right edge within viewport').to.be.at.most(VIEWPORT_W + 1);
      expect(r.bottom, 'Slap bottom edge within viewport').to.be.at.most(VIEWPORT_H + 1);
      expect(r.left, 'Slap left edge within viewport').to.be.at.least(-1);
    });
  });

  it('leaves a margin below the action buttons (not flush to the bottom edge)', () => {
    cy.contains('button', 'Slap').then(($b) => {
      const r = $b[0].getBoundingClientRect();
      expect(VIEWPORT_H - r.bottom, 'gap below Slap button').to.be.greaterThan(4);
    });
  });

  it('never collapses the empty pile slot', () => {
    // At game start the pile is empty, so its card <img> is v-show-hidden. The
    // slot must still reserve the full (portrait) card box — taller than wide —
    // rather than shrink to padding height with no card in it.
    cy.get('.pile_slot').then(($s) => {
      const r = $s[0].getBoundingClientRect();
      expect(r.height, 'empty pile slot stays card-tall').to.be.greaterThan(r.width);
    });
  });
});
