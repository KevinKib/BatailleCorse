// President trick variants on a fixed deal (PUT /api/test/president/{id}/deal, test profile only), with the Classic
// rules: a lone 2 on a lone card and a four of a kind close the trick (fixed rules). The browser is seat 0 (Alice); Bob and Carol
// are scripted.
//  1. Alice leads the king of hearts, Bob cuts it with a 2: the trick is closed at once and Bob leads;
//  2. Bob plays a four of a kind: it closes the trick too, and the line under the pile says so;
//  3. Bob goes on, then leads his last three cards, 2s: he goes out on a forbidden finish, which is announced, marked
//     on his seat, and does not close the trick (Alice is asked to answer).
import {
  hostLobby, joinGuests, noHorizontalScroll, scriptedPass, scriptedPlay,
} from '../support/presidentRoom';

const ALICE = ['KH', 'KD', 'KC', 'KS', '6D', '6C', '2C', '7H', '7D', '7C', '7S', '8H', '8D', '8C', '8S', '9H', '9D', '9C'];
const BOB = ['2S', '3H', '3D', '3C', '3S', '4H', '4D', '4C', '4S', '5H', '5D', '5C', '5S', '6H', '6S', '2H', '2D'];
const CAROL = ['9S', '10H', '10D', '10C', '10S', 'JH', 'JD', 'JC', 'JS', 'QH', 'QD', 'QC', 'QS', 'AH', 'AD', 'AC', 'AS'];

/** The deal goes seat 0, 1, 2, 0, ... */
function variantsDeal(): string[] {
  const deal: string[] = [];
  for (let i = 0; i < 17; i++) deal.push(ALICE[i], BOB[i], CAROL[i]);
  deal.push(ALICE[17]);
  return deal;
}

const texts = (selector: string) => cy.get(selector).then(($e) => [...$e].map((e) => e.textContent?.trim()));

[375, 1280].forEach((width) => {
  describe(`President trick variants at ${width} px`, () => {
    it('cuts with a 2, closes on a four, and announces a forbidden finish without closing the trick', () => {
      cy.viewport(width, width === 375 ? 667 : 800);
      hostLobby('Alice').then((id) => {
        joinGuests(id, ['Bob', 'Carol']).then(([bob, carol]) => {
          cy.get('[data-test="preset-title"]').should('have.text', 'Classic');
          cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: variantsDeal() });
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');

          // The rules in force are on the table.
          texts('[data-test="rule-chip"]').then((chips) => {
            expect(chips).to.include('After a pass: play again');
            expect(chips).not.to.include('Out on a 2: last place');
          });

          // 1. The cut.
          cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
          cy.get('[data-card="HEART_KING"]').click({ force: true });
          cy.get('[data-test="play"]').should('not.be.disabled').click();
          scriptedPlay(bob, '2', 1);
          cy.get('[data-test="trick-note"]').should('have.text', 'Bob closed the trick with a 2.');
          cy.get('[data-test="phase-text"]').should('have.text', 'Bob is playing');
          noHorizontalScroll();

          // 2. A four of a kind closes the trick: Bob leads again without anyone answering.
          scriptedPlay(bob, '3', 4);
          cy.get('[data-test="trick-note"]').should('have.text', 'Bob closed the trick with four of a kind.');
          scriptedPlay(bob, '4', 4);
          scriptedPlay(bob, '5', 4);

          // 3. A pair of 6s is passed on by both, then Bob leads his last two cards, 2s, and goes out on them.
          scriptedPlay(bob, '6', 2);
          scriptedPass(carol);
          cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
          cy.get('[data-test="pass"]').should('not.be.disabled').click();
          scriptedPlay(bob, '2', 2);
          cy.get('[data-test="trick-note"]').should('have.text', 'Bob went out on a 2 and will finish last.');
          cy.get('[data-seat="1"] [data-test="seat-out"]').should('have.text', 'Out on a 2');
          cy.get('[data-seat="1"] [data-test="seat-cards"]').should('have.text', '0 cards');
          // Going out on a 2 did not close the trick: Carol, then Alice, are asked to answer the pair of 2s.
          scriptedPass(carol);
          cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
          cy.get('[data-test="trick-card"]').should('have.length', 2);
          cy.screenshot(`president-variants-${width}`, { capture: 'viewport' });
          // 4. A lone 2 no longer answers a pair (a 2 cuts only with the same number of cards): Alice can only pass,
          // which ends the trick, and the lead goes to Carol, the next seat that still holds cards.
          cy.get('[data-card="CLUB_2"]').click({ force: true });
          cy.get('[data-test="play"]').should('be.disabled');
          cy.get('[data-test="pass"]').should('not.be.disabled').click();
          cy.get('[data-test="trick-card"]').should('not.exist');
          cy.get('[data-test="phase-text"]').should('have.text', 'Carol is playing');
          noHorizontalScroll();
        });
      });
    });
  });
});
