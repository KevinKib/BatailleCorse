// The end of a Kobo round and of the game. The browser is the host (seat 0, Alice); Bob (seat 1) is scripted. The
// deal is fixed (test profile): Alice announces Kobo on her first turn, Bob plays his final turn, the round is
// scored. Test 1 goes the whole way (gift of 10, next round, a forfeit, the end screen, Play again); test 2 is a
// failed Kobo (no gift) at desktop size. Two rooms only: a spec that opens many leaves connections behind.
import { awaitState, scriptedDiscardTurn, startKoboTable } from '../support/koboRoom';
import { measureSheet, sheetFitsTheScreen } from '../support/koboSheet';
import { noHorizontalScroll } from '../support/koboRoom';

const sheet = '[data-test="score-sheet"]';
const row = (seat: number) => `[data-test="score-row-${seat}"]`;

function memoriseBoth(bob: { publish: (d: string, b: Record<string, unknown>) => void }) {
  cy.get('[data-test="action-ready"]').click();
  cy.then(() => bob.publish('/app/kobo/ready', {}));
  cy.get('[data-test="action-draw"]').should('be.visible').and('not.be.disabled');
}

/** Alice draws the powerless card on top, discards it and announces Kobo; Bob plays his final turn. */
function aliceAnnouncesAndBobPlaysLast(bob: Parameters<typeof scriptedDiscardTurn>[0]) {
  memoriseBoth(bob);
  cy.get('[data-test="action-draw"]').click();
  cy.get('[data-test="action-discard"]').click();
  cy.get('[data-test="action-announce"]').click();
  scriptedDiscardTurn(bob);
}

describe('Kobo round end and game end', () => {
  it('scores a won Kobo, gives 10, starts the next round, then shows the end screen after a forfeit', () => {
    cy.viewport(375, 667);
    // Alice holds KH KD AH AD (2 points), Bob 9H 8H 7H 6H (30). Draw order: 2C (Alice), 3C (Bob).
    startKoboTable(2, ['KH', 'KD', 'AH', 'AD', '9H', '8H', '7H', '6H', '2C', '3C']).then(({ guests }) => {
      const bob = guests[0] as typeof guests[0] & { seat: number };
      // The guest's name is whatever the lobby handshake left it: read it from the table.
      let bobName = '';
      cy.get('[data-test="opponent-seat"][data-seat="1"] [data-test="seat-label"]').invoke('text').then((name) => { bobName = name; });
      aliceAnnouncesAndBobPlaysLast(bob);

      // GIFT: the sheet rises under the table, which stays on screen above it with every card face up.
      cy.get(sheet).should('be.visible');
      cy.get('[data-test="sheet-badge"]').should('have.text', 'Kobo!');
      cy.get('[data-test="sheet-title"]').should('have.text', 'You had the fewest points');
      cy.get('[data-test="sheet-subtitle"]').should('have.text', 'Choose who takes 10 points');
      cy.get(row(0)).within(() => {
        cy.get('[data-test="score-name"]').should('contain.text', 'You');
        cy.get('[data-test="pill-kobo"]').should('have.text', 'Kobo!');
        cy.get('[data-test="score-hand"]').should('have.text', '2');
        cy.get('[data-test="score-round"]').should('have.text', '0');
        cy.get('[data-test="score-total"]').should('have.text', '0 → 0');
      });
      cy.get(row(1)).within(() => {
        cy.get('[data-test="score-name"]').should(($n) => expect($n.text()).to.eq(bobName));
        cy.get('[data-test="score-name"]').should(($n) => expect($n[0].getBoundingClientRect().width).to.be.greaterThan(24));
        cy.get('[data-test="score-hand"]').should('have.text', '30');
        cy.get('[data-test="score-round"]').should('have.text', '30');
        cy.get('[data-test="score-total"]').should('have.text', '0 → 30');
        cy.get('[data-test="pill-gift"]').should('not.exist');
      });
      cy.get('[data-test="slot-1-0"]').should('have.attr', 'aria-label').and('contain', '9 of heart');
      cy.get('[data-test="action-next-round"]').should('not.exist');
      cy.get('[data-test^="give-"]').should('have.length', 1);
      sheetFitsTheScreen();
      noHorizontalScroll();
      measureSheet('gift, 2 players');
      cy.get('[data-test="give-1"]').should(($b) => expect($b.text()).to.eq(`Give 10 to ${bobName}`)).click();

      // ROUND_OVER: the gift shows on Bob's row, the next round waits for everybody.
      cy.get('[data-test="sheet-subtitle"]').should(($t) => expect($t.text()).to.eq(`You gave 10 points to ${bobName}`));
      cy.get('[data-test^="give-"]').should('not.exist');
      cy.get(row(1)).within(() => {
        cy.get('[data-test="pill-gift"]').should('have.text', '+10');
        cy.get('[data-test="score-total"]').should('have.text', '0 → 40');
      });
      awaitState(bob, 'round over', (s) => s.phase === 'ROUND_OVER' && s.lastRound.giftTo === 1);
      cy.get('[data-test="ready-0"]').should('not.exist');
      cy.get('[data-test="action-next-round"]').should('have.text', 'Next round').and('not.be.disabled').click();
      cy.get('[data-test="ready-0"]').should('be.visible');
      cy.get('[data-test="ready-1"]').should('not.exist');
      cy.get('[data-test="action-next-round"]').should('not.exist');
      cy.get('[data-test="sheet-wait"]').should('have.text', 'Waiting for the other players');
      cy.then(() => bob.publish('/app/kobo/ready', {}));

      // The next round starts in memorisation: no sheet, no leftover.
      cy.get('[data-test="round"]').should('have.text', 'Round 2');
      cy.get('[data-test="action-ready"]').should('be.visible');
      cy.get(sheet).should('not.exist');

      // Bob leaves: the game is over and the end screen names the winner, by forfeit.
      cy.then(() => bob.publish('/app/forfeit', {}));
      cy.get('[data-cy="end-overlay"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-test="end-winner"]').should('have.text', 'Winner: Alice');
      cy.get('.end-sub').should(($t) => expect($t.text()).to.eq(`${bobName} resigned.`));
      cy.get('[data-test="final-row-0"]').within(() => {
        cy.get('[data-test="final-rank"]').should('have.text', '1');
        cy.get('[data-test="final-total"]').should('contain.text', '0');
        // The name is actually seen (not just in the DOM): it has room in the row.
        cy.get('[data-test="final-name"]').should('be.visible').and('have.text', 'You')
          .and(($n) => expect($n[0].getBoundingClientRect().width).to.be.greaterThan(24));
      });
      cy.get('[data-test="pill-over"]').should('not.exist');
      noHorizontalScroll();
      cy.get('[data-cy="play-again"]').click();
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
    });
  });

  it('scores a failed Kobo without a gift at desktop size', () => {
    cy.viewport(1280, 800);
    // Alice holds KH QH JH 10H (33 points), Bob 6S 5S 4S 3S (18). Draw order: 2C (Alice), 3C (Bob).
    startKoboTable(2, ['KH', 'QH', 'JH', '10H', '6S', '5S', '4S', '3S', '2C', '3C']).then(({ guests }) => {
      const bob = guests[0] as typeof guests[0] & { seat: number };
      aliceAnnouncesAndBobPlaysLast(bob);

      cy.get(sheet).should('be.visible');
      cy.get('[data-test="sheet-title"]').should('have.text', 'Your Kobo failed: someone had fewer points');
      cy.get('[data-test="give-1"]').should('not.exist');
      cy.get(row(0)).within(() => {
        cy.get('[data-test="pill-kobo"]').should('be.visible');
        cy.get('[data-test="score-hand"]').should('have.text', '33');
        cy.get('[data-test="score-round"]').should('have.text', '53');
        cy.get('[data-test="score-total"]').should('have.text', '0 → 53');
      });
      cy.get(row(1)).within(() => {
        cy.get('[data-test="score-hand"]').should('have.text', '18');
        cy.get('[data-test="score-total"]').should('have.text', '0 → 18');
      });
      cy.get('[data-test="action-next-round"]').should('be.visible');
      // The table stays visible above the sheet, and the sheet fits the screen.
      cy.get('[data-test="opponent-seat"][data-seat="1"]').should('be.visible');
      sheetFitsTheScreen();
      noHorizontalScroll();
      measureSheet('failed Kobo, 2 players');
    });
  });
});
