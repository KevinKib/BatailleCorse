// Draughts end to end, nominal paths only. The browser is the host (Alice, seat 0, White); the other seat is either a
// scripted guest or a bot.
//  - Two players: the test seam (PUT /api/test/draughts/{id}/position, test profile only) replaces the opening with a short
//    ending. White must capture two pieces (the hint says so), plays the rafle 32x12 by two clicks, Black replies 3-8, and
//    White takes the last piece 12x3: the end screen names Alice.
//  - Against a bot: from the opening, White plays 32-28 and the bot (zero delay under `test`) answers, which the move list shows.
// Everything is waited on through DOM states and frames, never a fixed delay. Squares are numbered 1-50 the FMJD way.
import { joinAsScriptedPlayer, type ScriptedPlayer } from '../support/stompPlayer';

type Guest = ScriptedPlayer & { token: string; seat: number };

/** 50 layout characters, square 1 first: `w W b B .` (man / king, white / black, empty). */
function layout(pieces: Record<number, string>): string {
  return Array.from({ length: 50 }, (_, i) => pieces[i + 1] ?? '.').join('');
}

// White man on 32 against Black men on 27, 17 and 3: 32 jumps 27 to land on 21, then jumps 17 to land on 12.
const ENDING = layout({ 32: 'w', 27: 'b', 17: 'b', 3: 'b' });

function hostLobby(name: string): Cypress.Chainable<string> {
  cy.visit('/draughts');
  cy.get('[data-test="name"]').type(name);
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/draughts\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  return cy.location('pathname').then((path) => path.split('/room/')[1]);
}

/** The host plays White: it moves first, whatever the random colour option would have drawn. */
function hostPlaysWhite() {
  cy.get('[data-test="option-hostColour"]').contains('.choice', 'White').click();
  cy.get('[data-test="option-hostColour"] .choice--on').should('contain.text', 'White');
}

describe('Draughts', () => {
  it('plays a compulsory two-piece rafle, a reply and the last capture to the end screen', () => {
    hostLobby('Alice').then((id) => {
      joinAsScriptedPlayer(id, 'Bob', 'draughts').then((bob: Guest) => {
        cy.get('[data-test="player-count"]').should('contain.text', '2 / 2');
        hostPlaysWhite();
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="draughts-board"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-cy="rules-toggle"]').should('be.visible');

        // A short ending instead of fifty moves: White to move with a compulsory capture of two pieces.
        cy.request('PUT', `/api/test/draughts/${id}/position`, { layout: ENDING, toMove: 'white' });
        cy.get('[data-test="capture-hint"]').should('contain.text', 'You must capture: 2 pieces');
        cy.get('[data-test="square-32"]').should('have.attr', 'data-piece').and('contain', 'white');

        // The rafle has one route: select the piece, then its landing square.
        cy.get('[data-test="square-32"]').click();
        cy.get('[data-test="square-12"]').should('have.attr', 'data-target', 'true').click();

        // Black (the scripted guest) answers 3-8, offering the last piece: 12 can take it by landing on the freed square 3.
        cy.wrap(bob, { log: false, timeout: 10000 }).should((g: Guest) => {
          const s = g.lastState();
          expect(Boolean(s && s.started && s.currentSeat === g.seat && s.legalMoves?.length > 0), 'Bob is to move').to.eq(true);
        });
        cy.then(() => bob.publish('/app/draughts/move', { from: 3, to: 8, captures: [] }));

        cy.get('[data-test="capture-hint"]').should('contain.text', 'You must capture: 1 piece');
        cy.get('[data-test="square-12"]').click();
        cy.get('[data-test="square-3"]').should('have.attr', 'data-target', 'true').click();

        cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-test="end-winner"]').should('contain.text', 'Alice');
      });
    });
  });

  it('answers the first move of a human against a bot', () => {
    hostLobby('Alice').then(() => {
      cy.get('[data-test="add-bot"]').click();
      cy.get('[data-test="player-count"]').should('contain.text', '2 / 2');
      cy.get('[data-test="badge-bot"]').should('be.visible');
      cy.get('[data-test="seat-option-1-level"]').select('Easy');
      cy.get('[data-test="seat-option-1-level"]').should('have.value', 'random');
      hostPlaysWhite();
      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-test="draughts-board"]', { timeout: 10000 }).should('be.visible');

      // The opening has no capture; White plays 32-28 and the bot replies at once.
      cy.get('[data-test="capture-hint"]').should('not.exist');
      cy.get('[data-test="square-32"]').click();
      cy.get('[data-test="square-28"]').should('have.attr', 'data-target', 'true').click();
      cy.get('[data-test="moves-toggle"]').click();
      cy.get('[data-test="moves-row"]').should('have.length', 1);
      cy.get('[data-test="moves-cell-white"]').first().should('contain.text', '32-28');
      cy.get('[data-test="moves-cell-black"]', { timeout: 10000 }).first().invoke('text').should('match', /\d+[-x]\d+/);
    });
  });
});
