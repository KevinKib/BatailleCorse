// President: the four completed on a lone card, out of turn, at a table with a bot (fixed deal through PUT
// /api/test/president/{id}/deal, test profile only). The browser is seat 0 (Alice), Bob (seat 1) is scripted, the
// bot sits on seat 2 and answers at once under the test profile.
//  1. Bob leads a lone 5; the bot is next, so Alice, who holds the three other 5s, is out of turn: the bot waits the
//     completion grace, the Complete button shows, and pressing it closes the trick for Alice.
//  2. Alice leads a lone 5; Bob is next and the bot holds the three other 5s: it completes out of turn, and the
//     completion is drawn (a flight with its arrow from the bot's seat, the "Four completed" bubble, the closed trick).
import { hostLobby, joinGuests, noHorizontalScroll, scriptedPlay } from '../support/presidentRoom';

/** The deal goes seat 0, 1, 2, 0, ... (seat 0 holds 18 cards, the others 17). */
function deal(alice: string[], bob: string[], bot: string[]): string[] {
  const cards: string[] = [];
  for (let i = 0; i < 17; i++) cards.push(alice[i], bob[i], bot[i]);
  cards.push(alice[17]);
  return cards;
}

const HUMAN_COMPLETES = deal(
  // Alice can complete the 5s only: no card the bot may play leaves her the three missing cards of its rank.
  ['5H', '5D', '5C', '6H', '6D', '6C', '6S', '7H', '7D', '7C', '7S', '8H', '8D', '8C', '8S', '9H', '9D', 'JS'],
  ['KH', '5S', '3H', '3D', '3C', '3S', '4H', '4D', '4C', '4S', '10H', '10D', '10C', '10S', 'JH', 'JD', 'JC'],
  ['9S', '9C', 'QH', 'QD', 'QC', 'QS', 'KD', 'KC', 'KS', 'AH', 'AD', 'AC', 'AS', '2H', '2D', '2C', '2S'],
);

const BOT_COMPLETES = deal(
  ['KH', '5S', '3H', '3D', '3C', '3S', '4H', '4D', '4C', '4S', '6H', '6D', '6C', '6S', '7H', '7D', '7C', '7S'],
  ['2H', '2D', '2C', '2S', '8H', '8D', '8C', '8S', '9H', '9D', '9C', '9S', '10H', '10D', '10C', '10S', 'JH'],
  ['5H', '5D', '5C', 'JD', 'JC', 'JS', 'QH', 'QD', 'QC', 'QS', 'KD', 'KC', 'KS', 'AH', 'AD', 'AC', 'AS'],
);

const botSeat = '.seat-slot[data-seat="2"]';

/** Alice hosts, Bob joins (seat 1), then a bot takes seat 2; the deal is fixed and the game starts. */
function startWith(cards: string[]) {
  return hostLobby('Alice').then((id) =>
    joinGuests(id, ['Bob']).then(([bob]) => {
      cy.get('[data-test="add-bot"]').click();
      cy.get('[data-test="player-count"]').should('contain.text', '3 / 8');
      cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards });
      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
      return cy.wrap(bob);
    }));
}

[375, 1280].forEach((width) => {
  describe(`President completion on a lone card at ${width} px`, () => {
    beforeEach(() => {
      cy.viewport(width, width === 375 ? 667 : 720);
      // Normal speed, whatever an earlier spec left in the browser.
      cy.window().then((win) => win.localStorage.removeItem('animationSpeed'));
    });

    it('offers Complete to a human out of turn while the bot on turn waits, and the completion closes the trick', () => {
      startWith(HUMAN_COMPLETES).then((bob: any) => {
        cy.get(botSeat).should('exist');
        cy.get('[data-test="complete"]').should('not.exist');

        scriptedPlay(bob, '5', 1);
        // The bot is on turn, not Alice: she is out of turn, with the three missing 5s.
        cy.get('[data-test="trick-card"]').should('have.length', 1);
        cy.get('[data-test="phase-text"]').should('not.have.text', 'Your turn').and('contain.text', 'is playing');
        cy.get('[data-test="complete"]').should('be.visible').and('have.text', 'Complete the four');
        noHorizontalScroll();
        // Still out of turn when Alice presses it: the bot on turn is waiting for her, it has not passed or played.
        cy.get('[data-test="phase-text"]').should('contain.text', 'is playing');
        cy.get('[data-test="complete"]').click();

        // The four closed the trick: Alice leads, the 5s left her hand.
        cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
        cy.get('[data-test="complete"]').should('not.exist');
        cy.get('[data-test="trick-closed-reason"]').should('have.text', 'Four of a kind');
        cy.get('[data-test="my-zone"] [data-test="seat-bubble"]').should('have.attr', 'data-kind', 'completed');
        ['HEART_5', 'DIAMOND_5', 'CLUB_5'].forEach((card) => cy.get(`[data-card="${card}"]`).should('not.exist'));
        noHorizontalScroll();
      });
    });

    it('animates a completion out of turn by the bot from its seat', () => {
      startWith(BOT_COMPLETES).then(() => {
        cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
        cy.get('[data-card="SPADE_5"]').click({ force: true });
        cy.get('[data-test="play"]').should('not.be.disabled').click();

        // The bot completes at once, out of turn: its cards fly from its seat, the bubble and the badge say why.
        cy.get(`${botSeat} [data-test="seat-bubble"]`).should('have.attr', 'data-kind', 'completed')
          .and('have.text', 'Four completed');
        cy.get(botSeat).then(($slot) => {
          const box = $slot[0].getBoundingClientRect();
          cy.get('[data-test="motion-arrow"]').should(($arrows) => {
            const fromBot = [...$arrows].some((a) => {
              const x = Number(a.getAttribute('x1'));
              const y = Number(a.getAttribute('y1'));
              return x >= box.left - 8 && x <= box.right + 8 && y >= box.top - 8 && y <= box.bottom + 8;
            });
            expect(fromBot, 'an arrow leaves the bot\'s seat').to.eq(true);
          });
        });
        cy.get('[data-test="trick-closed-reason"]').should('have.text', 'Four of a kind');
        noHorizontalScroll();
      });
    });
  });
});
