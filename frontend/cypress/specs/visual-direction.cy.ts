// Non-regression for the "C bis" visual direction: fonts, flat turn cue, and
// every piece of information the game screen showed before the restyle.
describe('Visual direction (C bis)', () => {
  beforeEach(() => {
    cy.createGame();
  });

  it('keeps every piece of game-screen information visible', () => {
    cy.get('.game-timer').should('be.visible').invoke('text').should('match', /\d\d:\d\d/);
    cy.get('[data-cy="rules-toggle"]').should('be.visible');
    cy.get('[data-cy="opponent-card-count"]').should('be.visible');
    cy.get('[data-cy="player-card-count"]').should('be.visible');
    cy.get('[data-cy="pile-card-count"]').should('be.visible');
    cy.get('.player_tag').should('have.length', 2).each(($tag) => {
      cy.wrap($tag).should('be.visible').invoke('text').should('not.be.empty');
    });
    cy.contains('button', 'Send').should('be.visible');
    cy.contains('button', 'Slap').should('be.visible');
    cy.contains('button', 'Back').should('be.visible');
    cy.get('[data-test="turn-hint"]').should('be.visible').and('contain.text', 'Your turn');
  });

  it('uses Atkinson Hyperlegible for titles and the interface, in 400 and 700 only', () => {
    cy.get('body').should('have.css', 'font-family').and('include', 'Atkinson Hyperlegible');
    cy.get('body').should('have.css', 'font-weight', '400');
    cy.get('.player_tag').first().should('have.css', 'font-family').and('include', 'Atkinson Hyperlegible');
    cy.get('.player_tag').first().should('have.css', 'font-weight').and('match', /^(400|700)$/);
    cy.contains('button', 'Send').should('have.css', 'font-family').and('include', 'Atkinson Hyperlegible');
    cy.get('[data-cy="player-card-count"] .count').should('have.css', 'font-variant-numeric', 'tabular-nums');
    cy.get('.game-timer').should('have.css', 'font-variant-numeric', 'tabular-nums');
  });

  it('marks the active player with a crisp 1px ring and a fixed dot, no glow or pulse', () => {
    cy.get('.player_tag--active').should('have.length', 1).then(($tag) => {
      const style = getComputedStyle($tag[0]);
      expect(style.animationName, 'no pulse animation').to.eq('none');
      // inset 0 0 0 1px <accent>: no blur, no spread, inset.
      expect(style.boxShadow).to.match(/inset/);
      expect(style.boxShadow).to.match(/ 0px 0px 0px 1px|0px 0px 0px 1px/);
      const dot = getComputedStyle($tag[0], '::before');
      expect(dot.width).to.eq('6px');
      expect(dot.height).to.eq('6px');
    });
    cy.get('.turn-hint__dot').should('have.css', 'animation-name', 'none');
    cy.contains('button', 'Send').should('have.css', 'animation-name', 'none');
  });

  it('keeps the green Send button clean on my turn: no gold ring, a visible outline only for keyboard focus', () => {
    cy.contains('button', 'Send').should('not.be.disabled').then(($button) => {
      const resting = getComputedStyle($button[0]);
      // The gold ring that used to sit inside the button on my turn is gone.
      expect(resting.boxShadow, 'no gold ring or glow at rest').to.eq('none');
      expect(resting.outlineStyle, 'no outline at rest').to.eq('none');
      expect(resting.backgroundColor).to.eq('rgb(74, 222, 128)');
      expect(resting.borderColor).to.eq('rgb(74, 222, 128)');
    });
    // Keyboard focus (:focus-visible) keeps a visible outline, drawn outside the button (WCAG 2.4.7).
    cy.contains('button', 'Send').focus().then(($button) => {
      expect($button[0].matches(':focus-visible')).to.eq(true);
      const focused = getComputedStyle($button[0]);
      expect(focused.outlineStyle).to.eq('solid');
      expect(parseFloat(focused.outlineWidth)).to.be.at.least(1);
      expect(parseFloat(focused.outlineOffset)).to.be.at.least(1);
      expect(focused.boxShadow, 'focus does not add a gold ring').to.eq('none');
    });
    // A mouse click never shows the focus outline.
    cy.contains('button', 'Slap').then(($slap) => {
      $slap[0].blur();
    });
    cy.contains('button', 'Send').click();
    cy.contains('button', 'Send').then(($button) => {
      expect($button[0].matches(':focus-visible')).to.eq(false);
      expect(getComputedStyle($button[0]).outlineStyle).to.eq('none');
    });
  });

  it('renders buttons flat with a 0.4rem radius', () => {
    cy.contains('button', 'Slap').then(($button) => {
      const style = getComputedStyle($button[0]);
      expect(style.borderRadius).to.eq('6.4px');
      expect(style.boxShadow).to.eq('none');
    });
  });
});
