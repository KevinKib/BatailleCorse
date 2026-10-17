// The board of the Kobo table never moves from one state to the next: the draw pile, the discard pile, the drawn
// zone and every grid keep their box (within 1 px) while texts come and go (the phase line, the prompt, the turn
// cue, the hint, the log, the notice) and the buttons change. The browser is the host (seat 0), Bob is scripted.
// The deal is fixed (test profile): seat 0 holds KH 3H JH 10H, seat 1 holds 9H 8H 7H 6H, then 3S, 4S.
import { scriptedDiscardTurn, startKoboTable } from '../support/koboRoom';
import { snapshotRects, type Rects } from '../support/koboGeometry';

const viewports = [{ width: 375, height: 667 }, { width: 1280, height: 800 }] as const;
const BOARD = ['piles', 'draw', 'discard', 'drawn', 'opponentGrid', 'myGrid', 'myZone', 'myPlate', 'bar', 'opponentPlate'];

describe('Kobo stable layout', () => {
  viewports.forEach(({ width, height }) => {
    it(`keeps the board still across the states of a turn (${width}x${height})`, () => {
      cy.viewport(width, height);
      const seen: { state: string; rects: Rects }[] = [];
      const note = (state: string) => snapshotRects().then((rects) => { seen.push({ state, rects }); });
      startKoboTable(2, ['KH', '3H', 'JH', '10H', '9H', '8H', '7H', '6H', '3S', '4S']).then(({ guests }) => {
        const bob = guests[0] as typeof guests[0] & { seat: number };
        note('memorising');
        cy.get('[data-test="action-ready"]').click();
        cy.get('[data-test="seat-ready"]').should('exist');
        note('ready, waiting for Bob');
        cy.then(() => bob.publish('/app/kobo/ready', {}));
        cy.get('[data-test="action-draw"]').should('be.visible').and('not.be.disabled');
        cy.wait(400);
        note('my turn, draw');
        cy.get('[data-test="action-draw"]').click();
        cy.get('[data-test="action-discard"]').should('be.visible');
        cy.wait(400);
        note('holding the drawn card');
        cy.get('[data-test="action-discard"]').click();
        cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '3 of spade');
        cy.get('[data-test="match-hint"], [data-test="match-coach"]').should('be.visible');
        cy.wait(400);
        note('discarded, hint visible');
        cy.get('[data-test="action-endTurn"]').click();
        cy.wait(400);
        note('Bob to play');
        scriptedDiscardTurn(bob);
        cy.get('[data-test="action-draw"]').should('be.visible');
        cy.wait(400);
        note('my turn again, hint visible');
        cy.then(() => {
          const base = seen[0].rects;
          const problems: string[] = [];
          for (const { state, rects } of seen) {
            for (const name of BOARD) {
              // A plate is as wide as its text; only its place and height matter (they carry the board).
              const keys = name.endsWith('Plate') ? ['top', 'height'] as const : ['left', 'top', 'width', 'height'] as const;
              for (const key of keys) {
                const d = Math.abs((rects[name]?.[key] ?? 0) - (base[name]?.[key] ?? 0));
                if (d > 1) problems.push(`${state}: ${name}.${key} moved by ${d.toFixed(1)} px`);
              }
            }
          }
          expect(problems, problems.join('\n')).to.have.length(0);
        });
      });
    });
  });
});
