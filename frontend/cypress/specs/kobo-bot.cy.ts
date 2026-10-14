// Kobo against a bot, end to end. The browser is the host (Alice, seat 0); the bot is seat 1. Under the `test`
// profile the bot answers at once (zero delay, seeded memory) and the deal is fixed through
// PUT /api/test/kobo/{id}/deal. Every wait is on a DOM state, never on a delay.
import { hostLobby, noHorizontalScroll } from '../support/koboRoom';

const level = () => cy.get('[data-test="seat-option-1-level"]');
const botLabel = '[data-test="opponent-seat"][data-seat="1"] [data-test="seat-label"]';

// Alice holds KH KD AH AD (2 points), the bot 9H 8H 7H 6H (30). Draw order: 2C (Alice), 3C (bot).
const DEAL = ['KH', 'KD', 'AH', 'AD', '9H', '8H', '7H', '6H', '2C', '3C'];

describe('Kobo against a bot', () => {
  it('offers Add bot, puts the bot at the Medium level and lets the host change it or remove the bot', () => {
    hostLobby('Alice');
    cy.get('[data-test="add-bot"]').click();
    cy.get('[data-test="player-count"]').should('contain.text', '2 / 6');
    cy.get('[data-test="badge-bot"]').should('be.visible');
    cy.get('.players li').eq(1).should('contain.text', 'Bot 1');
    level().should('have.value', 'medium').find('option').then(($o) => {
      expect([...$o].map((o) => o.textContent?.trim())).to.deep.eq(['Easy', 'Medium', 'Hard']);
    });
    level().should('have.attr', 'aria-label', 'Level for Bot 1');
    level().select('Hard');
    level().should('have.value', 'hard');
    cy.get('[data-test="start"]').should('not.be.disabled');
    cy.get('[data-test="remove-bot-1"]').click();
    cy.get('[data-test="player-count"]').should('contain.text', '1 / 6');
    cy.get('[data-test="add-bot"]').should('not.be.disabled');
  });

  [[375, 667], [1280, 800]].forEach(([width, height]) => {
    it(`plays a round against a Hard bot: it readies, plays, and is named Bot 1 on the table and the sheet (${width}x${height})`, () => {
      cy.viewport(width, height);
      hostLobby('Alice').then((id) => {
        cy.get('[data-test="add-bot"]').click();
        level().select('Hard');
        level().should('have.value', 'hard');
        cy.request('PUT', `/api/test/kobo/${id}/deal`, { round: 1, cards: DEAL });
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="kobo-table"]', { timeout: 10000 }).should('be.visible');

        // The bot sits on the table under its shared name; nothing of the lobby is left.
        cy.get(botLabel).should('have.text', 'Bot 1');
        cy.get('[data-test="lobby"]').should('not.exist');

        // I memorise; the bot readies on its own, the round starts on my turn.
        cy.get('[data-test="action-ready"]').click();
        cy.get('[data-test="action-draw"]').should('be.visible').and('not.be.disabled');

        // I draw the powerless 2C, discard it and announce Kobo: the bot plays its last turn (its draw, its
        // decision) on its own and the round is scored.
        cy.get('[data-test="action-draw"]').click();
        cy.get('[data-test="action-discard"]').click();
        cy.get('[data-test="action-announce"]').click();
        cy.get('[data-test="score-sheet"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-test="score-row-1"] [data-test="score-name"]').should('have.text', 'Bot 1');
        cy.get('[data-test="give-1"]').should('have.text', 'Give 10 to Bot 1');
        noHorizontalScroll();
        cy.get('[data-test="give-1"]').click();

        // The bot readies for the next round as well.
        cy.get('[data-test="action-next-round"]').click();
        cy.get('[data-test="round"]').should('have.text', 'Round 2');
        cy.get(botLabel).should('have.text', 'Bot 1');
      });
    });
  });
});
