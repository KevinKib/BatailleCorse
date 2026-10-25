// Bataille Corse on the shared lobby: a bot at a chosen tier, a guest through the invite link, Leave, Play again.
import { connectScriptedPlayer, joinAsScriptedPlayer, waitForFrame } from '../support/stompPlayer';

const storedToken = (gameId: string) =>
  cy.window().then((win) => Object.values(JSON.parse(win.localStorage.getItem(`tokens:${gameId}`)!))[0] as string);

function noHorizontalScroll() {
  cy.window().then((w) => expect(w.document.documentElement.scrollWidth, 'no horizontal scroll').to.be.at.most(w.innerWidth));
}

describe('Bataille Corse lobby', () => {
  it('plays against a bot at the tier picked in the lobby', () => {
    cy.createGame({ tier: '3' });
    cy.get('.player_tag').first().should('have.text', 'Bot 1 (Gold)');
    // Reloaded, the table still names the bot with its tier (from the session view).
    cy.reload();
    cy.get('[data-cy="player-card-count"]', { timeout: 10000 }).should('be.visible');
    cy.get('.player_tag').first().should('have.text', 'Bot 1 (Gold)');
  });

  it('seats a guest arriving through the invite link, and both names reach the table', () => {
    cy.request('POST', '/api/lobby', { gameType: 'bataille-corse', name: 'Hosty' }).then((res) => {
      const { gameId, token } = res.body as { gameId: string; token: string };
      connectScriptedPlayer(gameId, token, 0).then((host) => {
        cy.visit(`/bataillecorse/join/${gameId}`);
        cy.get('[data-test="name"]').clear().type('Zoe');
        cy.get('[data-test="submit"]').click();
        cy.url({ timeout: 10000 }).should('include', `/bataillecorse/room/${gameId}`);
        cy.get('[data-test="lobby"]').should('be.visible').and('contain.text', 'Hosty');
        cy.get('[data-test="start"]').should('not.exist');
        cy.get('[data-test="waiting-host"]').scrollIntoView().should('be.visible');

        cy.then(() => host.publish('/app/bataillecorse/start', {}));
        cy.get('[data-cy="player-card-count"]', { timeout: 10000 }).should('contain.text', '26');
        cy.get('.player_tag').first().should('have.text', 'Hosty');
        cy.get('.player_tag').last().should('have.text', 'Zoe');
        cy.then(() => host.disconnect());
      });
    });
  });

  it('seats a scripted player through the shared helper typed for Bataille Corse, and waits on its lobby frame', () => {
    cy.openBatailleCorseLobby('Hosty').then((gameId) => {
      joinAsScriptedPlayer(gameId, 'Bob', 'bataille-corse').then((bob) => {
        cy.get('[data-test="player-count"]').should('contain.text', '2 /');
        cy.then(() => bob.publish('/app/lobby/rename', { name: 'Bobby' }));
        cy.get('[data-test="lobby"]').should('contain.text', 'Bobby');
        waitForFrame(bob, (frame) => frame.state?.players?.some((p: { name: string }) => p.name === 'Bobby'))
          .then((frame) => expect(frame.state.mySeat, 'the scripted player sits on seat 1').to.eq(1));
        cy.then(() => bob.disconnect());
      });
    });
  });

  it('Leave goes to the Game selection and forgets the seat', () => {
    cy.openBatailleCorseLobby().then((gameId) => {
      cy.get('[data-test="leave-lobby"]').click();
      cy.location('pathname', { timeout: 10000 }).should('eq', '/');
      cy.window().then((win) => expect(win.localStorage.getItem(`tokens:${gameId}`)).to.eq(null));
    });
  });

  it('Play again goes back to the lobby with the bot and its tier', () => {
    cy.createGame({ tier: '5' });
    cy.location('pathname').then((path) => {
      const gameId = path.split('/room/')[1];
      // End the game quickly: my own seat forfeits from a second socket.
      storedToken(gameId).then((token) => connectScriptedPlayer(gameId, token, 0)).then((me) => {
        cy.then(() => me.publish('/app/forfeit', {}));
        cy.contains('button', 'Play again', { timeout: 10000 }).should('be.visible').click();
        cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
        cy.location('pathname').should('eq', path);
        cy.get('[data-test="badge-bot"]').should('have.length', 1);
        cy.get('[data-test="seat-option-1-tier"]').should('have.value', '5');
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-cy="player-card-count"]', { timeout: 10000 }).should('contain.text', '26');
        cy.get('.player_tag').first().should('have.text', 'Bot 1 (Diamond)');
        cy.then(() => me.disconnect());
      });
    });
  });

  ([[375, 667], [1280, 800]] as Array<[number, number]>).forEach(([width, height]) => {
    it(`fits the lobby and the table at ${width}x${height} without horizontal scroll`, () => {
      cy.viewport(width, height);
      cy.openBatailleCorseLobby();
      cy.get('[data-test="add-bot"]').click();
      cy.get('[data-test="seat-option-1-tier"]').should('be.visible');
      cy.get('[data-test="start"]').should('be.visible');
      cy.get('[data-test="leave-lobby"]').should('be.visible');
      noHorizontalScroll();
      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-cy="player-card-count"]', { timeout: 10000 }).should('be.visible');
      cy.contains('button', 'Send').should('be.visible');
      noHorizontalScroll();
    });
  });
});
