// President against bots, end to end. The browser is the host (Alice, seat 0), seats 1 and 2 are bots. Under the
// `test` profile the bots answer at once (zero delay, seeded generator), so every wait is on a DOM state, never on a
// delay. A scripted twin of the browser's own seat plays its lowest legal set (the spec only presses Ready).
import { hostLobby, noHorizontalScroll } from '../support/presidentRoom';
import { autoplay, connectMyself } from '../support/presidentAutoplay';

const level = (seat: number) => cy.get(`[data-test="seat-option-${seat}-level"]`);

describe('President against bots', () => {
  it('offers Add bot, puts each bot at the Medium level, and lets the host change it, remove a bot and start with two', () => {
    hostLobby('Alice');
    cy.get('[data-test="add-bot"]').click();
    cy.get('[data-test="player-count"]').should('contain.text', '2 / 8');
    cy.get('[data-test="badge-bot"]').should('have.length', 1);
    cy.get('.players li').eq(1).should('contain.text', 'Bot 1');
    level(1).should('have.value', 'medium').find('option').then(($o) => {
      expect([...$o].map((o) => o.textContent?.trim())).to.deep.eq(['Easy', 'Medium', 'Hard']);
    });
    level(1).should('have.attr', 'aria-label', 'Level for Bot 1');
    // Two players are not enough for a President table.
    cy.get('[data-test="start"]').should('be.disabled');
    cy.get('[data-test="start-hint"]').should('contain.text', 'Waiting for 1 more player');

    cy.get('[data-test="add-bot"]').click();
    cy.get('[data-test="player-count"]').should('contain.text', '3 / 8');
    cy.get('.players li').eq(2).should('contain.text', 'Bot 2');
    level(2).select('Hard');
    level(2).should('have.value', 'hard');
    level(1).should('have.value', 'medium');
    cy.get('[data-test="start"]').should('not.be.disabled');

    cy.get('[data-test="remove-bot-2"]').click();
    cy.get('[data-test="player-count"]').should('contain.text', '2 / 8');
    cy.get('[data-test="start"]').should('be.disabled');
    // The Classic card and the options behind Customise stay where they were.
    cy.get('[data-test="preset-title"]').should('have.text', 'Classic');
    cy.get('[data-test="customise"]').click();
    cy.get('[data-test="option-afterPass"]').scrollIntoView().should('be.visible');
  });

  [[375, 667], [1280, 800]].forEach(([width, height]) => {
    it(`plays a round against two Hard bots, named Bot 1 and Bot 2 on the table and the round summary (${width}x${height})`, () => {
      cy.viewport(width, height);
      hostLobby('Alice').then((id) => {
        cy.get('[data-test="add-bot"]').click();
        cy.get('[data-test="add-bot"]').click();
        cy.get('[data-test="player-count"]').should('contain.text', '3 / 8');
        level(1).select('Hard');
        level(2).select('Hard');
        level(1).should('have.value', 'hard');
        level(2).should('have.value', 'hard');
        noHorizontalScroll();

        // The twin joins the channel in the lobby, so it sees every state from the start.
        connectMyself(id).then((me) => autoplay(id, me, { ready: false }));
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-test="lobby"]').should('not.exist');

        // The bots sit on the table under their shared names.
        cy.get('[data-test="opponent-seat"] [data-test="seat-label"]').then(($labels) => {
          expect([...$labels].map((l) => l.textContent?.trim())).to.deep.eq(['Bot 1', 'Bot 2']);
        });
        noHorizontalScroll();

        // The round is played to its end: the bots lead, beat and pass on their own.
        cy.get('[data-test="round-over-title"]', { timeout: 60000 }).should('have.text', 'Round 1 is over');
        cy.get('[data-test="round-over-place"]').should('have.length', 3);
        cy.get('[data-test="round-over-place"]').then(($rows) => {
          const text = $rows.text();
          expect(text).to.contain('Bot 1');
          expect(text).to.contain('Bot 2');
          expect(text).to.contain('Alice');
        });
        noHorizontalScroll();

        // The bots press Ready on their own: my press alone opens round 2.
        cy.get('[data-test="ready"]').click();
        cy.get('[data-test="round"]', { timeout: 30000 }).should('have.text', 'Round 2 of 5');
        cy.get('[data-test="opponent-seat"] [data-test="seat-label"]').then(($labels) => {
          expect([...$labels].map((l) => l.textContent?.trim())).to.deep.eq(['Bot 1', 'Bot 2']);
        });
      });
    });
  });
});
