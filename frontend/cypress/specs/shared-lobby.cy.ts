// The shared lobby with two clients: the browser under test is one player, a scripted STOMP client is the other.
import { connectScriptedPlayer, joinAsScriptedPlayer } from '../support/stompPlayer';

const roomId = () => cy.location('pathname').then((path) => path.split('/room/')[1]);

function hostLobby(name: string) {
  cy.visit('/games/bullshit');
  cy.get('[data-test="name"]').type(name);
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/bullshit\/room\/.+/);
  cy.get('[data-test="lobby"]').should('be.visible');
}

// A lobby hosted by the scripted player, entered by the browser through its invite link.
function guestLobby(hostName: string, guestName: string) {
  return cy.request('POST', '/api/lobby', { gameType: 'bullshit', name: hostName }).then((res) => {
    const { gameId, token } = res.body as { gameId: string; token: string };
    return connectScriptedPlayer(gameId, token, 0).then((host) => {
      cy.visit(`/games/bullshit/join/${gameId}`);
      cy.get('[data-test="name"]').type(guestName);
      cy.get('[data-test="submit"]').click();
      cy.url({ timeout: 10000 }).should('include', `/games/bullshit/room/${gameId}`);
      cy.get('[data-test="lobby"]').should('be.visible');
      return cy.wrap({ host, gameId });
    });
  });
}

describe('Shared lobby', () => {
  it('shows the guest the option the host picked, live', () => {
    hostLobby('Alice');
    roomId().then((id) => {
      joinAsScriptedPlayer(id, 'Bob').then((guest) => {
        cy.contains('[data-test="lobby"] label', '52 cards').click();
        cy.get('[data-test="option-deckSize"] .choice--on').should('contain.text', '52 cards');
        cy.wrap(guest.frames).should((frames) => {
          expect(guest.lastState().options.deckSize, 'the guest sees the new deck').to.eq('52');
          expect(frames.length).to.be.greaterThan(0);
        });
      });
    });
  });

  it('does not let a guest edit the game options, and says who is awaited', () => {
    guestLobby('Hosty', 'Zoe').then(() => {
      cy.get('[data-test="option-deckSize"]').should('contain.text', '32 cards');
      cy.get('[data-test="lobby"] input[type="radio"]').should('not.exist');
      cy.get('[data-test="start"]').should('not.exist');
      cy.get('[data-test="waiting-host"]').scrollIntoView().should('be.visible');
      cy.get('[data-test="add-bot"]').should('not.exist');
      cy.get('[data-test="waiting-for-player"]').should('be.visible');
    });
  });

  it('frees the seat of a guest who leaves, and sends them to /games', () => {
    hostLobby('Alice');
    roomId().then((id) => {
      cy.get('[data-test="add-bot"]').click();
      cy.get('[data-test="player-count"]').should('contain.text', '2 / 6');
      cy.request('POST', `/api/bullshit/game/${id}/join`, { name: 'Bob' }).then((res) => {
        cy.get('[data-test="player-count"]').should('contain.text', '3 / 6');
        const token = res.body.token as string;
        // The host stays in the browser; the guest leaves through its own socket.
        connectScriptedPlayer(id, token, res.body.playerId).then((guest) => {
          guest.publish('/app/lobby/leave', {});
          cy.get('[data-test="player-count"]').should('contain.text', '2 / 6');
          cy.get('.players li').should('have.length', 2);
        });
      });
    });
  });

  it('sends the leaving browser to /games and forgets its token', () => {
    guestLobby('Hosty', 'Zoe').then(({ host, gameId }) => {
      // The host has seen the guest arrive: the guest's socket is up and its seat is bound.
      cy.wrap(host.frames).should(() => expect(host.lastState().players.filter((p: { joined: boolean }) => p.joined)).to.have.length(2));
      cy.get('[data-test="leave-lobby"]').click();
      cy.location('pathname').should('eq', '/games');
      cy.window().then((win) => expect(win.localStorage.getItem(`bullshit:tokens:${gameId}`)).to.eq(null));
      cy.wrap(host.frames).should(() => {
        expect(host.lastState().players.filter((p: { joined: boolean }) => p.joined), 'the seat reopens').to.have.length(1);
      });
    });
  });

  it('makes the next human the host without a reload when the host leaves', () => {
    guestLobby('Hosty', 'Zoe').then(({ host }) => {
      cy.get('[data-test="start"]').should('not.exist');
      cy.get('[data-test="host-notice"]').should('have.text', '');
      host.publish('/app/lobby/leave', {});
      cy.get('[data-test="host-notice"]').should('contain.text', 'You are now the host');
      cy.get('[data-test="start"]').scrollIntoView().should('be.visible');
      cy.get('[data-test="option-deckSize-52"]').should('exist');
      cy.get('.players li').should('have.length', 1);
      cy.get('input[data-test="rename"]').should('have.value', 'Zoe');
      // The seat moved to 0: the stored entry follows, so a reload still restores the right seat.
      cy.location('pathname').then((path) => {
        const id = path.split('/room/')[1];
        cy.window().then((win) => expect(JSON.parse(win.localStorage.getItem(`bullshit:tokens:${id}`)!)).to.have.keys('0'));
      });
    });
  });

  it('renames my own seat for everybody', () => {
    hostLobby('Alice');
    roomId().then((id) => {
      joinAsScriptedPlayer(id, 'Bob').then((guest) => {
        cy.get('input[data-test="rename"]').clear().type('Alicia{enter}');
        cy.wrap(guest.frames).should(() => {
          expect(guest.lastState().players[0].name).to.eq('Alicia');
        });
      });
    });
  });

  it('tells an invite link to a deleted lobby that the game is gone', () => {
    cy.request('POST', '/api/lobby', { gameType: 'bullshit', name: 'Hosty' }).then((res) => {
      const { gameId, token } = res.body as { gameId: string; token: string };
      connectScriptedPlayer(gameId, token, 0).then((host) => {
        host.publish('/app/lobby/leave', {});
        // Nothing sits at the table any more: the lobby is removed.
        cy.request({ url: `/api/bullshit/game/${gameId}?token=${token}`, failOnStatusCode: false }).its('status').should('eq', 404);
        cy.visit(`/games/bullshit/join/${gameId}`);
        cy.get('[data-test="name"]').type('Zoe');
        cy.get('[data-test="submit"]').click();
        cy.get('[data-test="join-error"]').should('be.visible').and('contain.text', 'No game found');
      });
    });
  });
});
