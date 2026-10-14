// The score sheet of a won Kobo (the tallest: table, gift buttons) with 4 and 6 players, at 375x667 and 1280x800.
// Alice (the browser) announces on her first turn, every scripted player plays one final turn, then the sheet is
// measured at both sizes. Two rooms only.
import { awaitState, DECK_CODES, scriptedDiscardTurn, startKoboTable } from '../support/koboRoom';
import { measureSheet, sheetFitsTheScreen } from '../support/koboSheet';
import { noHorizontalScroll } from '../support/koboRoom';

const sheet = '[data-test="score-sheet"]';
// Powerless cards drawn in turn: Alice's first, then one per scripted player.
const DRAWS = ['2C', '3C', '4C', '5C', '6C', '2D'];
// Alice holds KH KD AH AD (2 points); the others take high cards in turn.
const ALICE = ['KH', 'KD', 'AH', 'AD'];
const OTHERS = DECK_CODES.filter((code) => !ALICE.includes(code) && !DRAWS.includes(code) && !code.endsWith('C'));

function dealFor(players: number): string[] {
  return [...ALICE, ...OTHERS.slice(0, 4 * (players - 1)), ...DRAWS.slice(0, players)];
}

function playToTheSheet(players: number) {
  startKoboTable(players, dealFor(players)).then(({ guests }) => {
    cy.get('[data-test="action-ready"]').click();
    cy.then(() => guests.forEach((g) => g.publish('/app/kobo/ready', {})));
    cy.get('[data-test="action-draw"]').should('be.visible').and('not.be.disabled').click();
    cy.get('[data-test="action-discard"]').click();
    cy.get('[data-test="action-announce"]').click();
    guests.forEach((g) => scriptedDiscardTurn(g as typeof g & { seat: number }));
    awaitState(guests[0], 'the gift step', (s) => s.phase === 'GIFT');
  });
}

function checkAt(width: number, height: number, players: number) {
  cy.viewport(width, height);
  cy.get(sheet).should('be.visible');
  cy.get('[data-test^="give-"]').should('have.length', players - 1);
  cy.get('[data-test^="score-row-"]').should('have.length', players);
  sheetFitsTheScreen();
  noHorizontalScroll();
  measureSheet(`gift, ${players} players`);
}

describe('Kobo score sheet fit', () => {
  for (const players of [4, 6]) {
    it(`fits ${players} players at 375x667 and 1280x800`, () => {
      cy.viewport(375, 667);
      playToTheSheet(players);
      checkAt(375, 667, players);
      checkAt(1280, 800, players);
    });
  }
});
