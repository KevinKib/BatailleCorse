// Picaria on its 13 points, end to end. The browser is White (the host, through the UI), a scripted STOMP client is
// Black; every wait is on a DOM state or on a frame, never on a delay. Points 0..8 are the grid row by row (centre 4),
// then the crossings: 9 upper-left (between 0 and 4), 10 upper-right (2 and 4), 11 lower-right (8 and 4), 12 lower-left
// (6 and 4). A piece slides one step along a line, so a corner reaches the centre through its crossing.
import { joinAsScriptedPlayer, waitForFrame, type ScriptedPlayer } from '../support/stompPlayer';

type Scripted = ScriptedPlayer & { token: string; seat: number };

const place = (p: Scripted, point: number) => cy.then(() => p.publish('/app/three-men-morris/place', { point }));
const owner = (n: number, who: 'white' | 'black' | 'none') => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-owner', who);
const state = (n: number, s: 'idle' | 'highlighted' | 'selected') => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-state', s);
const myTurn = () => cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
const click = (n: number) => cy.get(`[data-test="point-${n}"]`).click();

/** The browser as White and host, with Picaria picked; the scripted Black has joined and the game is started. */
function asWhiteOnPicaria(): Cypress.Chainable<{ black: Scripted }> {
  cy.visit('/games/three-men-morris');
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/three-men-morris\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  return cy.location('pathname').then((path) => {
    const id = path.split('/room/')[1];
    return joinAsScriptedPlayer(id, 'Bob', 'three-men-morris').then((black) => {
      cy.get('[data-test="variant-card-picaria"]').click();
      waitForFrame(black, (f) => f.state?.options?.variant === 'picaria');
      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible');
      myTurn();
      return cy.wrap({ black });
    });
  });
}

describe('Picaria on 13 points', () => {
  it('shows 13 points, the centre closed with a padlock and its hint, and places on a crossing', () => {
    asWhiteOnPicaria().then(({ black }) => {
      cy.get('[data-test^="point-"]').should('have.length', 13);
      cy.get('[data-test="point-4"]').should('have.attr', 'data-locked', 'true').find('[data-test="lock"]').should('exist');
      cy.get('[data-test="centre-closed"]').should('have.text', 'The centre is closed while you place.');
      cy.get('[data-test="point-4"]').should('have.attr', 'aria-label').and('contain', 'closed while placing');
      state(4, 'idle');
      [0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12].forEach((n) => state(n, 'highlighted'));
      // A click on the closed centre places nothing and the turn stays mine.
      click(4);
      owner(4, 'none');
      myTurn();
      // A crossing is a point like any other.
      click(9); owner(9, 'white');
      cy.get('[data-test="point-9"] [data-test="piece-mark"]').should('have.attr', 'data-mark', 'ring');
      place(black, 12); owner(12, 'black'); myTurn();
      cy.get('[data-test="point-12"] [data-test="piece-mark"]').should('have.attr', 'data-mark', 'diamond');
      cy.get('[data-test="board-live"]').should('contain.text', 'Bob placed a piece on Lower-left crossing.');
      state(4, 'idle');
      cy.get('[data-test="centre-closed"]').should('exist');
    });
  });

  it('moves a piece through a crossing: the paths are lit from the selected piece, the centre opens', () => {
    asWhiteOnPicaria().then(({ black }) => {
      // White 1 6 8, Black 0 2 5: the holes are 3, 4, 7 and the crossings.
      click(1); owner(1, 'white'); place(black, 0); owner(0, 'black'); myTurn();
      click(6); owner(6, 'white'); place(black, 2); owner(2, 'black'); myTurn();
      click(8); owner(8, 'white'); place(black, 5); owner(5, 'black'); myTurn();
      cy.get('[data-test="phase-hint"]').should('have.text', 'Move a piece');
      cy.get('[data-test="centre-closed"]').should('not.exist');
      cy.get('[data-test="point-4"]').should('not.have.attr', 'data-locked');
      cy.get('[data-test="lit-path"]').should('not.exist');
      click(6);
      state(6, 'selected');
      // The corner 6 reaches 3, 7 and its crossing 12, one step each; not the centre.
      [3, 7, 12].forEach((n) => state(n, 'highlighted'));
      state(4, 'idle');
      cy.get('[data-test="lit-path"]').should('have.length', 3);
      ['3', '7', '12'].forEach((to) => cy.get(`[data-test="lit-path"][data-to="${to}"]`).should('exist'));
      click(12);
      owner(12, 'white'); owner(6, 'none');
      cy.get('[data-test="lit-path"]').should('not.exist');
      cy.get('[data-test="turn-hint"]').should('not.exist');
    });
  });

  it('wins on a corner, crossing, centre line: the two edges are drawn, the end screen follows', () => {
    asWhiteOnPicaria().then(({ black }) => {
      // White 0 9 1, Black 2 5 3: no line while placing; then 1 -> 4 makes 0-9-4.
      click(0); owner(0, 'white'); place(black, 2); owner(2, 'black'); myTurn();
      click(9); owner(9, 'white'); place(black, 5); owner(5, 'black'); myTurn();
      click(1); owner(1, 'white'); place(black, 3); owner(3, 'black'); myTurn();
      click(1);
      state(1, 'selected');
      state(4, 'highlighted');
      click(4);
      owner(4, 'white');
      cy.get('line[data-winning="true"]').should('have.length', 2);
      cy.get('line[data-winning="true"][data-line="0-9"]').should('exist');
      cy.get('line[data-winning="true"][data-line="9-4"]').should('exist');
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'VICTORY');
    });
  });
});
