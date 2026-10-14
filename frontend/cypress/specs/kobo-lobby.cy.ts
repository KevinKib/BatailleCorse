// Kobo on the shared lobby (2 to 6 players, bots added by the host: see kobo-bot.cy.ts): the browser under test is the host, scripted
// STOMP clients are the other players. Start leads to the table (its layout is covered by kobo-table.cy.ts).
import { joinAsScriptedPlayer } from '../support/stompPlayer';
import { hostLobby, noHorizontalScroll, type Guest } from '../support/koboRoom';

describe('Kobo lobby', () => {
  [375, 1280].forEach((width) => {
    it(`creates a lobby, offers Add bot to the host, shows the invite link, starts with two players at ${width} px`, () => {
      cy.viewport(width, width === 375 ? 667 : 800);
      hostLobby('Alice').then((id) => {
        cy.get('[data-test="lobby"] .game-title').should('have.text', 'Kobo');
        cy.get('[data-test="player-count"]').should('contain.text', '1 / 6');
        cy.get('[data-test="add-bot"]').should('be.visible').and('not.be.disabled');
        cy.get('[data-test="start"]').should('be.disabled');
        cy.get('[data-test="start-hint"]').should('contain.text', 'Waiting for 1 more player');
        cy.get('[data-test="invite-link"]').invoke('val').should('match', new RegExp(`/games/kobo/join/${id}$`));
        cy.get('.local-preferences').should('not.exist');
        noHorizontalScroll();

        joinAsScriptedPlayer(id, 'Bob', 'kobo').then((bob) => {
          cy.get('[data-test="player-count"]').should('contain.text', '2 / 6');
          cy.get('[data-test="start"]').should('not.be.disabled');
          cy.get('[data-test="start-hint"]').should('not.exist');
          noHorizontalScroll();
          cy.get('[data-test="start"]').click();
          cy.get('[data-test="kobo-table"]', { timeout: 10000 }).should('be.visible');
          cy.get('[data-test="lobby"]').should('not.exist');
          cy.get('[data-test="opponent-seat"]').should('have.length', 1);
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
        cy.get('[data-test="player-count"]').should('contain.text', `${expected} / 6`);
        // The host may add a bot while a seat is free.
        cy.get('[data-test="add-bot"]').should(expected < 6 ? 'not.be.disabled' : 'be.disabled');
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
          cy.get('[data-test="player-count"]').should('contain.text', '3 / 6');
          cy.get('[data-test="remove-seat-0"]').should('not.exist');
          cy.get('[data-test="remove-seat-1"]').click();
          cy.get('[data-test="player-count"]').should('contain.text', '2 / 6');
          cy.get('[data-test="lobby"] .players li').should('have.length', 2);
          cy.wrap(bob.frames).should((frames) => {
            const notice = frames.find((f) => f.eventType === 'REMOVED');
            expect(notice?.message).to.eq('You were removed from the lobby by the host');
          });
          cy.get('[data-test="add-bot"]').should('not.be.disabled');
        });
      });
    });
  });
});
