// Kobo on the shared lobby (2 to 6 players, no bots, no options): the browser under test is the host, scripted
// STOMP clients are the other players. The table itself is not built yet: Start leads to a provisional screen.
import { joinAsScriptedPlayer, type ScriptedPlayer } from '../support/stompPlayer';

type Guest = ScriptedPlayer & { token: string; seat: number };

function hostLobby(name: string) {
  cy.visit('/games/kobo');
  cy.get('[data-test="name"]').type(name);
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/kobo\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  return cy.location('pathname').then((path) => path.split('/room/')[1]);
}

/**
 * A view sent between the browser's first load and its seat subscription is lost, and the lobby carries no
 * other signal that the channel is live. So `publish` (a repeatable change that shows up in the lobby, such as a
 * rename) is replayed with a fresh label until the browser displays it: from then on the channel is live.
 */
function untilBrowserSees(publish: (label: string) => void) {
  return cy.window({ log: false }).then((win) => new Promise<void>((resolve, reject) => {
    let attempt = 0;
    const next = () => {
      if (attempt >= 12) { reject(new Error('the browser never received the lobby update')); return; }
      const label = `Nudge${++attempt}`;
      publish(label);
      const deadline = Date.now() + 1500;
      const look = () => {
        if (win.document.querySelector('.players')?.textContent?.includes(label)) { resolve(); return; }
        if (Date.now() > deadline) { next(); return; }
        setTimeout(look, 50);
      };
      look();
    };
    next();
  }));
}

function noHorizontalScroll() {
  cy.document().then((doc) => {
    expect(doc.documentElement.scrollWidth, 'page width').to.be.at.most(doc.documentElement.clientWidth);
  });
}

describe('Kobo lobby', () => {
  [375, 1280].forEach((width) => {
    it(`creates a lobby without bots, shows the invite link, starts with two players at ${width} px`, () => {
      cy.viewport(width, width === 375 ? 667 : 800);
      hostLobby('Alice').then((id) => {
        cy.get('[data-test="lobby"] .game-title').should('have.text', 'Kobo');
        cy.get('[data-test="player-count"]').should('contain.text', '1 / 6');
        cy.get('[data-test="add-bot"]').should('not.exist');
        cy.get('[data-test="waiting-for-player"]').should('be.visible');
        cy.get('[data-test="start"]').should('be.disabled');
        cy.get('[data-test="start-hint"]').should('contain.text', 'Waiting for 1 more player');
        cy.get('[data-test="invite-link"]').invoke('val').should('match', new RegExp(`/games/kobo/join/${id}$`));
        cy.get('.local-preferences').should('not.exist');
        noHorizontalScroll();

        joinAsScriptedPlayer(id, 'Bob', 'kobo').then((bob) => {
          untilBrowserSees((label) => bob.publish('/app/lobby/rename', { name: label }));
          cy.get('[data-test="player-count"]').should('contain.text', '2 / 6');
          cy.get('[data-test="start"]').should('not.be.disabled');
          cy.get('[data-test="start-hint"]').should('not.exist');
          noHorizontalScroll();
          cy.get('[data-test="start"]').click();
          cy.get('[data-test="kobo-provisional"]', { timeout: 10000 }).should('be.visible');
          cy.get('[data-test="lobby"]').should('not.exist');
          cy.contains('Game in progress').should('be.visible');
          noHorizontalScroll();
        });
      });
    });
  });

  it('fills the lobby from the third to the sixth player, the host removes a seat, a seventh is refused', () => {
    hostLobby('Alice').then((id) => {
      const guests: Guest[] = [];
      const joinNext = (name: string, expected: number) => joinAsScriptedPlayer(id, name, 'kobo').then((guest) => {
        guests.push(guest);
        untilBrowserSees((label) => guest.publish('/app/lobby/rename', { name: label }));
        cy.get('[data-test="player-count"]').should('contain.text', `${expected} / 6`);
        cy.get('[data-test="add-bot"]').should('not.exist');
      });
      joinNext('Bob', 2);
      joinNext('Carol', 3);
      joinNext('Dan', 4);
      joinNext('Erin', 5);
      joinNext('Fay', 6).then(() => {
        cy.get('[data-test="waiting-for-player"]').should('not.exist');
        cy.get('[data-test="start"]').should('not.be.disabled');
        cy.get('[data-test="lobby"] .players li').should('have.length', 6);

        // The seventh player is turned away with the dedicated message (a fresh browser: no seat of mine here).
        cy.clearLocalStorage();
        cy.visit(`/games/kobo/join/${id}`);
        cy.get('[data-test="name"]').type('Gus');
        cy.get('[data-test="submit"]').click();
        cy.get('[data-test="join-error"]', { timeout: 10000 }).should('contain.text', 'This lobby is full.');
      });
    });
  });

  it('lets the host remove a seat; the removed player is told and the seat frees up', () => {
    hostLobby('Alice').then((id) => {
      joinAsScriptedPlayer(id, 'Bob', 'kobo').then((bob) => {
        joinAsScriptedPlayer(id, 'Carol', 'kobo').then((carol) => {
          untilBrowserSees((label) => carol.publish('/app/lobby/rename', { name: label }));
          cy.get('[data-test="player-count"]').should('contain.text', '3 / 6');
          cy.get('[data-test="remove-seat-0"]').should('not.exist');
          cy.get('[data-test="remove-seat-1"]').click();
          cy.get('[data-test="player-count"]').should('contain.text', '2 / 6');
          cy.get('[data-test="lobby"] .players li').should('have.length', 2);
          cy.wrap(bob.frames).should((frames) => {
            const notice = frames.find((f) => f.eventType === 'REMOVED');
            expect(notice?.message).to.eq('You were removed from the lobby by the host');
          });
          cy.get('[data-test="add-bot"]').should('not.exist');
        });
      });
    });
  });
});
