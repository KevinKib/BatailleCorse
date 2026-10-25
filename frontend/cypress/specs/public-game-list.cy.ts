// The public game list of the Join page (nominal cases): a public Kobo lobby shows up live and is joined from its
// row; a lobby created with "Private game" checked never appears. The browser under test is the visitor (or the
// private host); the other hosts are REST-created lobbies kept online by a scripted STOMP seat.
import { connectScriptedPlayer } from '../support/stompPlayer';

/** Creates a public Kobo lobby named `hostName` and keeps its host seat online. */
function openPublicLobby(hostName: string) {
  return cy.request('POST', '/api/lobby', { gameType: 'kobo', name: hostName }).then((created) => {
    const { gameId, token } = created.body as { gameId: string; token: string };
    return connectScriptedPlayer(gameId, token, 0).then(() => gameId);
  });
}

describe('Public game list', () => {
  it('shows a new Kobo lobby live on the Join page and joins it from its row', () => {
    cy.visit('/kobo/join');
    // The list has loaded (other specs may have left games on a shared stack: rows are found by game id).
    cy.get('[data-test="pg-region"]', { timeout: 10000 }).should('have.attr', 'aria-busy', 'false');
    cy.get('[data-test="name"]').type('Bob');

    openPublicLobby('Alice').then((gameId) => {
      cy.get(`[data-test="pg-row"][data-game-id="${gameId}"]`, { timeout: 10000 }).as('row');
      cy.get('@row').should('contain.text', 'Alice').and('contain.text', '1 / 6');
      cy.get('@row').find('[data-test="pg-badge"]').should('have.text', 'Open');
      cy.get('@row').find('[data-test="pg-join"]').click();

      cy.location('pathname', { timeout: 10000 }).should('eq', `/kobo/room/${gameId}`);
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-test="player-count"]').should('contain.text', '2 / 6');
    });
  });

  it('never lists a lobby created with Private game checked on the Game home', () => {
    cy.visit('/kobo');
    cy.get('[data-test="name"]').type('Carol');
    cy.get('[data-test="private-game"]').check();
    cy.get('[data-test="new-game"]').click();
    cy.url({ timeout: 10000 }).should('match', /\/kobo\/room\/.+/);
    cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="customise"]').click();
    cy.get('[data-test="private-switch"]').should('have.attr', 'aria-checked', 'true');
    cy.get('[data-test="invite-hint"]').should('contain.text', 'Only people with this link can join');

    cy.location('pathname').then((path) => {
      const privateId = path.split('/room/')[1];
      // Leaving the page closes the browser's socket: a scripted socket keeps the private host online, so the
      // game is hidden by its privacy and not because nobody is there.
      cy.window().then((win) => {
        const tokens = JSON.parse(win.localStorage.getItem(`kobo:tokens:${privateId}`)!) as Record<string, string>;
        return connectScriptedPlayer(privateId, tokens['0'], 0);
      });
      openPublicLobby('Dave').then((publicId) => {
        cy.visit('/kobo/join');
        cy.get(`[data-test="pg-row"][data-game-id="${publicId}"]`, { timeout: 10000 }).should('be.visible');
        cy.get(`[data-test="pg-row"][data-game-id="${privateId}"]`).should('not.exist');
        cy.request('/api/lobbies/kobo').its('body.lobbies').should((lobbies: Array<{ gameId: string }>) => {
          expect(lobbies.map((l) => l.gameId)).to.include(publicId).and.not.include(privateId);
        });
      });
    });
  });
});
