// Mangola rematch, end to end: Play again reopens the room with the same options, the guest comes back with the
// play-again call (as its own Play again button does), and the second game starts on a fresh placement. The browser
// is the host (seat 0, side A, who starts), a scripted STOMP client the guest; every wait is on a DOM state or a frame.
import { connectScriptedPlayer, waitForFrame } from '../support/stompPlayer';
import {
  arrange, canSow, confirmLayout, playFromTwoPerPit, TWO_PER_PIT,
} from '../support/mangolaPlayer';

describe('Mangola rematch (end to end)', () => {
  it('Play again reopens the lobby with the same options; the guest returns and the new game starts on an empty placement', () => {
    playFromTwoPerPit().then(({ id, bob }) => {
      cy.then(() => bob.publish('/app/forfeit', {}));
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'VICTORY');
      cy.then(() => bob.disconnect());
      cy.get('[data-cy="play-again"]').click();

      // The room is a lobby again: the host alone, the option it chose kept, Start waiting for a second player.
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-test="player-count"]').should('contain.text', '1 / 2');
      cy.get('[data-test="option-firstPlayer"] .choice--on').should('contain.text', 'Host');
      cy.get('[data-test="start"]').should('be.disabled');
      cy.get('[data-test="mangola-board"]').should('not.exist');

      cy.request('POST', `/api/mangola/game/${id}/play-again`, { name: 'Bob' }).then((res) => {
        const { playerId, token } = res.body as { playerId: number; token: string };
        connectScriptedPlayer(id, token, playerId).then((bob2) => {
          cy.get('[data-test="player-count"]').should('contain.text', '2 / 2');
          cy.get('[data-test="start"]').should('not.be.disabled').click();

          // A new placement: nothing of the first game's layout is left, on either side.
          cy.get('[data-test="placement-editor"]', { timeout: 10000 }).should('be.visible');
          cy.get('[data-test="seeds-left"]').should('contain.text', '32 seeds left');
          cy.get('[data-test="confirm"]').should('be.disabled');
          cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'false');
          cy.get('[data-test="opponent-status"]').should('contain.text', 'Placing');
          waitForFrame(bob2, (f) => f.state?.phase === 'PLACEMENT').then((frame) => {
            expect(frame.state.players.every((p: any) => p.ready === false), 'nobody is ready').to.eq(true);
          });

          // And it plays: the host still starts.
          cy.get('[data-test="preset-two-per-pit"]').click();
          arrange(bob2, TWO_PER_PIT);
          confirmLayout(bob2);
          cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'true');
          cy.get('[data-test="confirm"]').should('not.be.disabled').click();
          cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
          canSow(8);
          cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
        });
      });
    });
  });

  it('the guest asking for the rematch first takes the host seat: the former host follows as a guest, options kept', () => {
    playFromTwoPerPit().then(({ id, bob }) => {
      cy.then(() => bob.publish('/app/forfeit', {}));
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.then(() => bob.disconnect());
      // Bob is back in the room before the host has pressed anything.
      cy.request('POST', `/api/mangola/game/${id}/play-again`, { name: 'Bob' }).then((res) => {
        const { playerId, token } = res.body as { playerId: number; token: string };
        connectScriptedPlayer(id, token, playerId);
      });
      cy.get('[data-cy="play-again"]').click();
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-test="player-count"]').should('contain.text', '2 / 2');
      cy.get('.players li').should('have.length', 2);
      // The first to come back takes the first free seat, so the host's seat: the browser reads the options, read-only.
      cy.get('[data-test="option-firstPlayer"]').should('contain.text', 'Host');
      cy.get('[data-test="option-firstPlayer"] input').should('not.exist');
      cy.get('[data-test="start"]').should('not.exist');
    });
  });
});
