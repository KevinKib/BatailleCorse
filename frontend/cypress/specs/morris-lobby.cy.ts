// Three Men's Morris lobby: the shared two-player lobby on the Morris store. The second player is a scripted
// STOMP client; there is no "Add bot" because the game has no bot.
import { joinAsScriptedPlayer } from '../support/stompPlayer';

const SIZES: Array<[number, number]> = [[375, 667], [1280, 800]];

function hostLobby(name = 'Alice') {
  cy.visit('/games');
  cy.get('[data-test="play-three-men-morris"]').click();
  cy.location('pathname').should('eq', '/games/three-men-morris');
  cy.get('[data-test="name"]').type(name);
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/three-men-morris\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  return cy.location('pathname').then((path) => path.split('/room/')[1]);
}

describe("Three Men's Morris lobby", () => {
  it('opens a two-seat lobby with no Add bot, Start disabled until the second player joins', () => {
    hostLobby().then((id) => {
      cy.get('[data-test="lobby"]').should('contain.text', "Three Men's Morris");
      cy.get('[data-test="add-bot"]').should('not.exist');
      cy.get('[data-test="start"]').should('be.disabled');
      cy.get('[data-test="player-count"]').should('contain.text', '1 / 2');
      joinAsScriptedPlayer(id, 'Bob', 'three-men-morris').then(() => {
        cy.get('[data-test="player-count"]').should('contain.text', '2 / 2');
        cy.get('.players li').should('have.length', 2);
        cy.get('[data-test="start"]').should('not.be.disabled');
        cy.get('[data-test="add-bot"]').should('not.exist');
      });
    });
  });

  it('is full for a third player (the server refuses the join)', () => {
    hostLobby().then((id) => {
      joinAsScriptedPlayer(id, 'Bob', 'three-men-morris').then(() => {
        cy.get('[data-test="player-count"]').should('contain.text', '2 / 2');
        cy.request({ method: 'POST', url: `/api/three-men-morris/game/${id}/join`, body: { name: 'Eve' }, failOnStatusCode: false })
          .its('status').should('eq', 409);
      });
    });
  });

  it('lets the host remove the guest, who is then told', () => {
    hostLobby().then((id) => {
      joinAsScriptedPlayer(id, 'Bob', 'three-men-morris').then((bob) => {
        cy.get('[data-test="player-count"]').should('contain.text', '2 / 2');
        cy.get('[data-test="remove-seat-1"]').click();
        cy.get('[data-test="player-count"]').should('contain.text', '1 / 2');
        cy.wrap(null).should(() => {
          expect(bob.frames.some((f) => f.eventType === 'REMOVED'), 'the guest received REMOVED').to.eq(true);
        });
        cy.get('[data-test="start"]').should('be.disabled');
      });
    });
  });

  it('starts the game: the lobby gives way to the board', () => {
    hostLobby().then((id) => {
      joinAsScriptedPlayer(id, 'Bob', 'three-men-morris').then(() => {
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-test="lobby"]').should('not.exist');
      });
    });
  });

  it('joins by invite link: the guest sees the lobby and the host is named', () => {
    hostLobby('Alice').then((id) => {
      cy.get('[data-test="player-count"]').should('contain.text', '1 / 2');
      cy.window().then((win) => win.localStorage.removeItem(`three-men-morris:tokens:${id}`));
      cy.visit(`/games/three-men-morris/join/${id}`);
      cy.get('[data-test="name"]').type('Bob');
      cy.get('[data-test="submit"]').click();
      cy.url({ timeout: 10000 }).should('match', new RegExp(`/games/three-men-morris/room/${id}`));
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
      cy.get('.players li').should('have.length', 2);
    });
  });

  SIZES.forEach(([width, height]) => {
    it(`fits the lobby at ${width}x${height} without horizontal scroll`, () => {
      cy.viewport(width, height);
      hostLobby();
      cy.get('[data-test="start"]').scrollIntoView().should('be.visible');
      cy.get('[data-test="leave-lobby"]').should('be.visible');
      cy.document().then((doc) => {
        expect(doc.documentElement.scrollWidth, 'page width').to.be.at.most(doc.documentElement.clientWidth);
      });
    });
  });
});
