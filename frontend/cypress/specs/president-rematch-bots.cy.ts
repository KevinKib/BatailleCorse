// President Play again with two humans and two bots, end to end. The browser hosts (Alice, seat 0), a scripted guest
// (Bob) and two bots fill the table; a scripted twin of the browser's seat and Bob play the three-round match by
// themselves. The browser comes back first through Play again, so it hosts the reopened lobby; Bob comes back
// second. He sits right after the host, before the re-seated bots, so the host can still remove every bot without
// having to remove Bob first. Every wait is on a DOM state.
import { hostLobby, joinGuests } from '../support/presidentRoom';
import { autoplay, connectMyself } from '../support/presidentAutoplay';
import { connectScriptedPlayer } from '../support/stompPlayer';

describe('President Play again with two humans and bots', () => {
  it('the host of the reopened lobby may remove every bot, the other human sitting before them', () => {
    hostLobby('Alice').then((id) => {
      cy.get('[data-test="customise"]').click();
      cy.get('[data-test="option-length-rounds-3"]').check({ force: true });
      cy.get('[data-test="option-length-rounds-3"]').should('be.checked');
      connectMyself(id).then((me) => autoplay(id, me));
      joinGuests(id, ['Bob']).then(([bob]) => {
        autoplay(id, bob);
        cy.get('[data-test="add-bot"]').click();
        cy.get('[data-test="add-bot"]').click();
        cy.get('[data-test="player-count"]').should('contain.text', '4 / 8');
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');

        cy.get('[data-test="final-scores"]', { timeout: 180000 }).should('be.visible').click();
        cy.get('[data-cy="end-overlay"]', { timeout: 10000 }).should('be.visible');
        cy.then(() => bob.disconnect());
        cy.get('[data-cy="play-again"]').click();

        // The browser is back first: it hosts the lobby, the two bots re-seated right after it.
        cy.get('[data-test="lobby"]', { timeout: 15000 }).should('be.visible');
        cy.get('[data-test="player-count"]').should('contain.text', '3 / 8');
        cy.get('[data-test="start"]').should('exist');

        cy.request('POST', `/api/president/game/${id}/play-again`, { name: 'Bob' }).then((res) => {
          const { playerId, token } = res.body as { playerId: number; token: string };
          expect(playerId, 'Bob sits right after the host').to.eq(1);
          connectScriptedPlayer(id, token, playerId);
        });
        cy.get('[data-test="player-count"]').should('contain.text', '4 / 8');
        cy.get('.players li').eq(1).should('contain.text', 'Bob');
        cy.get('[data-test="remove-seat-1"]').should('be.visible');
        cy.get('[data-test="remove-bot-2"]').should('not.be.disabled');
        cy.get('[data-test="remove-bot-3"]').should('not.be.disabled');

        // Removing a bot keeps Bob in the room.
        cy.get('[data-test="remove-bot-2"]').click();
        cy.get('[data-test="player-count"]').should('contain.text', '3 / 8');
        cy.get('.players li').eq(1).should('contain.text', 'Bob');
        cy.get('[data-test="badge-bot"]').should('have.length', 1);
        cy.get('[data-test="remove-bot-2"]').should('not.be.disabled');
      });
    });
  });
});
