// Mangola lobby: the shared two-player lobby on the Mangola store, with the two host options (the set-up and who
// starts). The second player is a scripted STOMP client (there is no bot yet).
import { joinAsScriptedPlayer } from '../support/stompPlayer';

const SIZES: Array<[number, number]> = [[375, 667], [1280, 800]];

function hostLobby(name = 'Alice') {
  cy.visit('/games');
  cy.get('[data-test="play-mangola"]').click();
  cy.location('pathname').should('eq', '/games/mangola');
  cy.get('[data-test="name"]').type(name);
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/mangola\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  return cy.location('pathname').then((path) => path.split('/room/')[1]);
}

describe('Mangola lobby', () => {
  it('opens a two-seat lobby without Add bot, Start disabled until the second player joins', () => {
    hostLobby().then((id) => {
      cy.get('[data-test="lobby"]').should('contain.text', 'Mangola');
      cy.get('[data-test="add-bot"]').should('not.exist');
      cy.get('[data-test="start"]').should('be.disabled');
      cy.get('[data-test="player-count"]').should('contain.text', '1 / 2');
      joinAsScriptedPlayer(id, 'Bob', 'mangola').then(() => {
        cy.get('[data-test="player-count"]').should('contain.text', '2 / 2');
        cy.get('.players li').should('have.length', 2);
        cy.get('[data-test="start"]').should('not.be.disabled');
      });
    });
  });

  it('lets the host choose the set-up and who starts; the guest reads them, read-only', () => {
    hostLobby().then((id) => {
      // The server's defaults: free placement, the first player drawn at random.
      cy.get('[data-test="option-setup"] .choice--on').should('contain.text', 'Free placement');
      cy.get('[data-test="option-firstPlayer"] .choice--on').should('contain.text', 'Random');
      cy.get('[data-test="option-setup-checkerboard"]').check({ force: true });
      cy.get('[data-test="option-setup"] .choice--on').should('contain.text', 'Checkerboard');
      cy.get('[data-test="option-firstPlayer-host"]').check({ force: true });
      cy.get('[data-test="option-firstPlayer"] .choice--on').should('contain.text', 'Host');
      joinAsScriptedPlayer(id, 'Bob', 'mangola').then((bob) => {
        // The guest cannot change the host's choices: the server refuses and the lobby keeps them.
        cy.then(() => bob.publish('/app/lobby/setOptions', { values: { setup: 'random' } }));
        cy.wrap(bob.frames).should((frames) => {
          expect(frames.some((f) => f.eventType === 'ERROR' || f.success === false), 'the refusal reached the guest').to.eq(true);
        });
        cy.get('[data-test="option-setup"] .choice--on').should('contain.text', 'Checkerboard');
        cy.get('[data-test="option-firstPlayer"] .choice--on').should('contain.text', 'Host');
      });
    });
  });

  it('shows the guest the options as read-only text', () => {
    hostLobby('Alice').then((id) => {
      cy.get('[data-test="option-setup-random"]').check({ force: true });
      cy.get('[data-test="option-setup"] .choice--on').should('contain.text', 'Random');
      cy.window().then((win) => win.localStorage.removeItem(`mangola:tokens:${id}`));
      cy.visit(`/games/mangola/join/${id}`);
      cy.get('[data-test="name"]').type('Bob');
      cy.get('[data-test="submit"]').click();
      cy.url({ timeout: 10000 }).should('match', new RegExp(`/games/mangola/room/${id}`));
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
      cy.get('.players li').should('have.length', 2);
      cy.get('[data-test="option-setup"]').should('contain.text', 'Random');
      cy.get('[data-test="option-setup"] input').should('not.exist');
      cy.get('[data-test="option-firstPlayer"] input').should('not.exist');
      cy.get('[data-test="start"]').should('not.exist');
    });
  });

  it('lets the host remove the guest, who is then told', () => {
    hostLobby().then((id) => {
      joinAsScriptedPlayer(id, 'Bob', 'mangola').then((bob) => {
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

  it('starts the game: the lobby gives way to the placement screen', () => {
    hostLobby().then((id) => {
      joinAsScriptedPlayer(id, 'Bob', 'mangola').then(() => {
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="placement-editor"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-test="lobby"]').should('not.exist');
      });
    });
  });

  it('starts on the board when the set-up is not free', () => {
    hostLobby().then((id) => {
      cy.get('[data-test="option-setup-checkerboard"]').check({ force: true });
      cy.get('[data-test="option-setup"] .choice--on').should('contain.text', 'Checkerboard');
      joinAsScriptedPlayer(id, 'Bob', 'mangola').then(() => {
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-test="placement-editor"]').should('not.exist');
      });
    });
  });

  it('Leave lobby goes back to the Game selection', () => {
    hostLobby().then(() => {
      cy.get('[data-test="leave-lobby"]').click();
      cy.location('pathname', { timeout: 10000 }).should('eq', '/games');
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
