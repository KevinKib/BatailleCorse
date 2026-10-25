// President on the shared lobby (3 to 8 players; the bots have their own spec, president-bot.cy.ts): the browser under test is the host, scripted STOMP
// clients are the other players. The options go through the shared lobby (Classic card + Customise).
import { hostLobby, joinGuests, noHorizontalScroll } from '../support/presidentRoom';

describe('President lobby', () => {
  [375, 1280].forEach((width) => {
    it(`creates a lobby with the Classic rules and Add bot, starts with three players at ${width} px`, () => {
      cy.viewport(width, width === 375 ? 667 : 800);
      hostLobby('Alice').then((id) => {
        cy.get('[data-test="lobby"] .game-title').should('have.text', 'President');
        cy.get('[data-test="player-count"]').should('contain.text', '1 / 8');
        cy.get('[data-test="add-bot"]').should('be.visible').and('not.be.disabled');
        cy.get('[data-test="start"]').should('be.disabled');
        cy.get('[data-test="start-hint"]').should('contain.text', 'Waiting for 2 more players');
        cy.get('[data-test="preset-title"]').should('have.text', 'Classic');
        cy.get('[data-test="invite-link"]').invoke('val').should('match', new RegExp(`/president/join/${id}$`));
        noHorizontalScroll();

        joinGuests(id, ['Bob', 'Carol']).then(() => {
          cy.get('[data-test="start"]').should('not.be.disabled');
          cy.get('[data-test="start-hint"]').should('not.exist');
          noHorizontalScroll();
          cy.get('[data-test="start"]').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');
          cy.get('[data-test="lobby"]').should('not.exist');
          cy.get('[data-test="opponent-seat"]').should('have.length', 2);
          noHorizontalScroll();
        });
      });
    });
  });

  it('keeps every option behind Customise, shows Custom after a change and goes back to Classic', () => {
    hostLobby('Alice').then(() => {
      cy.get('[data-test="game-options"]').should('not.exist');
      cy.get('[data-test="customise"]').should('have.attr', 'aria-expanded', 'false').click();
      cy.get('[data-test="customise"]').should('have.attr', 'aria-expanded', 'true');
      ['afterPass', 'exchange', 'giveBack', 'nextLead', 'scoring', 'length'].forEach((key) => {
        cy.get(`[data-test="option-${key}"]`).scrollIntoView().should('be.visible');
      });
      cy.get('[data-test="option-afterPass-out"]').check({ force: true });
      cy.get('[data-test="preset-title"]').should('have.text', 'Custom');
      cy.get('[data-test="option-afterPass-out"]').should('be.checked');
      cy.get('[data-test="option-length-endless"]').check({ force: true });
      cy.get('[data-test="preset-reset"]').scrollIntoView().should('be.visible').click();
      cy.get('[data-test="preset-title"]').should('have.text', 'Classic');
      cy.get('[data-test="option-afterPass-reenter"]').should('be.checked');
      cy.get('[data-test="option-length-rounds-5"]').should('be.checked');
    });
  });

  it('fills the lobby to eight players and refuses a ninth', () => {
    hostLobby('Alice').then((id) => {
      joinGuests(id, ['Bob', 'Carol', 'Dan', 'Erin', 'Fay', 'Gus', 'Hal']).then(() => {
        cy.get('[data-test="player-count"]').should('contain.text', '8 / 8');
        cy.clearLocalStorage();
        cy.visit(`/president/join/${id}`);
        cy.get('[data-test="name"]').type('Ivy');
        cy.get('[data-test="submit"]').click();
        cy.get('[data-test="join-error"]', { timeout: 10000 }).should('contain.text', 'This lobby is full.');
      });
    });
  });
});
