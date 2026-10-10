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
    cy.get('[data-cy="turn-hint"]').should('be.visible').and('contain.text', 'YOUR TURN');
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

  it('renders buttons flat with a 0.4rem radius', () => {
    cy.contains('button', 'Slap').then(($button) => {
      const style = getComputedStyle($button[0]);
      expect(style.borderRadius).to.eq('6.4px');
      expect(style.boxShadow).to.eq('none');
    });
  });
});
