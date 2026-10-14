// The slot the log names keeps one soft contour: its own 1 px border plus a faint tint, never a ring or a glow
// on top of the border of a slot that can be clicked. What the contour says (which slot the last event touched)
// is still there, and so is the clickable border.
import { DECK_CODES, startKoboTable } from '../support/koboRoom';

[375, 1280].forEach((width) => {
  it(`names the touched slot with one soft contour at ${width}px`, () => {
    cy.viewport(width, width === 375 ? 667 : 800);
    // The first card drawn is a 7: its power looks at one of my own cards.
    startKoboTable(2, [...DECK_CODES.slice(0, 8), '7S']).then(({ guests }) => {
      cy.get('[data-test="action-ready"]').click();
      cy.then(() => guests.forEach((g) => g.publish('/app/kobo/ready', {})));
      cy.get('[data-test="action-draw"]').should('be.visible').click();
      cy.get('[data-test="action-discard"]').click();
      cy.get('[data-test="prompt"]').should('have.text', 'Look at one of your cards');
      // Clickable slots: a 1 px accent border, nothing else.
      cy.get('[data-test="slot-0-1"]').should('not.have.attr', 'data-highlighted');
      cy.get('[data-test="slot-0-1"]').then(($s) => {
        const s = getComputedStyle($s[0]);
        expect(s.borderTopWidth).to.eq('1px');
        expect(s.borderTopColor).not.to.eq('rgba(0, 0, 0, 0)');
        expect(s.boxShadow).to.eq('none');
      });
      cy.get('[data-test="slot-0-0"]').click();
      cy.get('[data-test="slot-0-0"]').should('have.attr', 'data-highlighted', 'true');
      cy.get('[data-test="slot-0-0"]').then(($s) => {
        const s = getComputedStyle($s[0]);
        expect(s.boxShadow, 'no ring or glow on the touched slot').to.eq('none');
        expect(s.borderTopWidth).to.eq('1px');
        expect(s.borderTopColor, 'the touched slot is still marked').not.to.eq('rgba(0, 0, 0, 0)');
        expect(s.backgroundColor, 'with a faint tint').not.to.eq('rgba(0, 0, 0, 0)');
      });
    });
  });
});
