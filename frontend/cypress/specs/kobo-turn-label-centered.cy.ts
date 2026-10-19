// The turn cue ("Your turn") and the line under it (phase or prompt) are centred on the axis of my cards, whatever
// buttons the bar shows beside or under them: the label never moves with the width or the presence of a button.
// The browser is the host (seat 0); Bob is a scripted player who only draws, discards and ends his turns.
import { scriptedDiscardTurn, startKoboTable } from '../support/koboRoom';

const HANDS = ['KH', 'QH', 'JH', '10H', '9H', '8H', '7H', '6H'];
const viewports = [
  { width: 375, height: 667 },
  { width: 360, height: 640 },
  { width: 375, height: 812 },
  { width: 768, height: 1024 },
  { width: 1280, height: 800 },
] as const;

/** Centre of the text itself (a block-level paragraph is as wide as its column, whatever its text). */
function textCentre(doc: Document, el: Element): number {
  const range = doc.createRange();
  range.selectNodeContents(el);
  const r = range.getBoundingClientRect();
  return (r.left + r.right) / 2;
}

/** `--env shots=1` also saves the viewport of each state (the before/after pictures of the pull request). */
function shot(state: string) {
  if (!Cypress.env('shots')) return;
  cy.window().then((win) => cy.screenshot(`${state.replace(/[^a-z0-9]+/gi, '-')}-${win.innerWidth}`, { capture: 'viewport', overwrite: true }));
}

/** Every label the bar shows right now is on the centre line of my cards (within 1 px). */
function labelsAreOnTheCardsAxis(state: string) {
  shot(state);
  if (Cypress.env('shots')) return; // the pictures run only takes pictures
  cy.document().then((doc) => {
    const cards = doc.querySelector('[data-test="my-tableau-ring"]')!.getBoundingClientRect();
    const axis = (cards.left + cards.right) / 2;
    const labels = ['turn-hint', 'phase', 'prompt'].flatMap((id) => Array.from(doc.querySelectorAll(`[data-test="${id}"]`)));
    expect(labels.length, `${state}: a label is shown`).to.be.greaterThan(0);
    labels.forEach((el) => {
      const offset = textCentre(doc, el) - axis;
      cy.log(`${state} ${el.getAttribute('data-test')} offset ${offset.toFixed(1)} px`);
      expect(Math.abs(offset), `${state}: ${el.getAttribute('data-test')} off the cards axis (px)`).to.be.at.most(1);
    });
  });
}

describe('Kobo turn label centred on the cards', () => {
  viewports.forEach(({ width, height }) => {
    it(`stays on the axis of my cards in every state of the bar at ${width}x${height}`, () => {
      cy.viewport(width, height);
      // Draw order: 3S (I swap it in), 4S (Bob), 7S (I peek one of mine).
      startKoboTable(2, [...HANDS, '3S', '4S', '7S', '5S']).then(({ guests }) => {
        const bob = guests[0] as typeof guests[0] & { seat: number };
        // Memorising: the phase line and the lone "I have memorised" button.
        cy.get('[data-test="action-ready"]').should('be.visible');
        labelsAreOnTheCardsAxis('memorise');
        cy.get('[data-test="action-ready"]').click();
        cy.then(() => bob.publish('/app/kobo/ready', {}));
        // My turn, draw step: "Your turn", "Your turn: draw a card", the Draw button.
        cy.get('[data-test="action-draw"]').should('be.visible').and('not.be.disabled');
        cy.get('[data-test="turn-hint"]').should('be.visible');
        labelsAreOnTheCardsAxis('draw');
        // The drawn card: the prompt and Discard.
        cy.get('[data-test="action-draw"]').click();
        cy.get('[data-test="prompt"]').should('be.visible');
        cy.get('[data-test="action-discard"]').should('be.visible');
        labelsAreOnTheCardsAxis('decision');
        // The end of the turn: End turn and Kobo!.
        cy.get('[data-test="slot-0-1"]').click();
        cy.get('[data-test="action-endTurn"]').should('be.visible');
        cy.get('[data-test="action-announce"]').should('be.visible');
        labelsAreOnTheCardsAxis('turn end');
        // Bob's turn: no button at all.
        cy.get('[data-test="action-endTurn"]').click();
        cy.get('[data-test="phase"]').invoke('text').should('match', / is (drawing|choosing)/);
        cy.get('[data-test="action-endTurn"]').should('not.exist');
        labelsAreOnTheCardsAxis('Bob drawing');
        scriptedDiscardTurn(bob);
        // A 7 has a power: the prompt with Skip, then End turn + Kobo! + Hide once the card is looked at.
        cy.get('[data-test="action-draw"]', { timeout: 10000 }).should('be.visible').click();
        cy.get('[data-test="action-discard"]').click();
        cy.get('[data-test="prompt"]').should('have.text', 'Look at one of your cards');
        cy.get('[data-test="action-skip"]').should('be.visible');
        labelsAreOnTheCardsAxis('peek prompt + Skip');
        cy.get('[data-test="slot-0-0"]').click();
        cy.get('[data-test="action-hide"]').should('be.visible');
        cy.get('[data-test="action-endTurn"]').should('be.visible');
        cy.get('[data-test="action-announce"]').should('be.visible');
        labelsAreOnTheCardsAxis('End turn + Kobo! + Hide');
      });
    });
  });
});
