// The end of a President round (animations chunk 4): the table finishes (last plays, closing, sweep) before the round
// sheet rises from the bottom, the sheet never makes the page or the felt scroll on a phone (3 players on a fixed
// deal, 8 players against bots), and the last round of a match shows its sheet before the final scores
// (president-end.cy.ts plays that one). Everything is waited on through DOM states and frames.
import {
  awaitState, hostLobby, joinGuests, noHorizontalScroll, scriptedPass, scriptedPlay, threePlayerDeal,
} from '../support/presidentRoom';
import { autoplay, connectMyself } from '../support/presidentAutoplay';

const SUITS = ['HEART', 'DIAMOND', 'CLUB'];

function playTriple(rank: string, goesOut = false) {
  cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
  SUITS.forEach((suit) => cy.get(`[data-card="${suit}_${rank}"]`).click({ force: true }));
  cy.get('[data-test="play"]').should('not.be.disabled').click();
  if (goesOut) return;
  cy.get('[data-test="trick-card"]').should('have.length', 3);
  cy.get('[data-test="phase-text"]').should('not.have.text', 'Your turn');
}

/** Nothing scrolls: not the page, not the felt, not the sheet itself. */
function nothingScrolls() {
  noHorizontalScroll();
  cy.window().then((win) => {
    const doc = win.document.documentElement;
    expect(doc.scrollHeight, 'page height').to.be.at.most(win.innerHeight);
  });
  cy.get('[data-test="president-table"]').should(($felt) => {
    expect($felt[0].scrollHeight, 'felt content height').to.be.at.most($felt[0].clientHeight + 1);
  });
  cy.get('[data-test="sheet-slot"]').should(($slot) => {
    expect($slot[0].scrollHeight, 'sheet content height').to.be.at.most($slot[0].clientHeight + 1);
  });
  cy.get('[data-test="ready"]').should('be.visible').then(($button) => {
    const box = $button[0].getBoundingClientRect();
    expect(box.bottom, 'Next round button bottom').to.be.at.most(Cypress.config('viewportHeight'));
  });
}

const PHONES: Array<[number, number]> = [[375, 667], [360, 640], [375, 812]];

describe('President end of round, 3 players on a fixed deal', () => {
  [...PHONES, [1280, 800] as [number, number]].forEach(([width, height]) => {
    it(`shows the round sheet only once the last play is over, from the bottom, without scrolling (${width}x${height})`, () => {
      cy.viewport(width, height);
      hostLobby('Alice').then((id) => {
        joinGuests(id, ['Bob', 'Carol']).then(([bob, carol]) => {
          cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: threePlayerDeal() });
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');

          ['KING', 'QUEEN', 'JACK', '10', '9'].forEach((rank) => {
            playTriple(rank);
            scriptedPass(bob);
            scriptedPass(carol);
          });
          playTriple('8', true);
          scriptedPlay(bob, '7', 3);
          scriptedPass(carol);
          scriptedPlay(bob, '6', 3);
          scriptedPass(carol);
          scriptedPlay(bob, '5', 3);
          scriptedPass(carol);
          scriptedPlay(bob, '4', 3);
          scriptedPass(carol);
          scriptedPlay(bob, '3', 3);
          scriptedPass(carol);
          scriptedPlay(bob, 'ACE', 2);

          // The server scored the round: the table is still finishing the last plays, with nothing to ask of me.
          awaitState(bob, 'the round is scored', (s) => s.phase === 'ROUND_OVER');
          let scoredAt = 0;
          cy.then(() => { scoredAt = Date.now(); });
          cy.get('[data-test="round-over"]').should('not.exist');
          cy.get('[data-test="play"]').should('not.exist');

          // The sheet comes in after the flights and a rest, and nothing is travelling any more by then.
          cy.get('[data-test="sheet-slot"] [data-test="round-over"]', { timeout: 10000 }).should('be.visible');
          cy.then(() => { expect(Date.now() - scoredAt, 'time the table had to finish').to.be.at.least(900); });
          cy.get('[data-test="motion-card"]').should('not.exist');
          cy.get('[data-test="my-zone"]').should('not.exist');
          cy.get('[data-test="round-over-place"]').should('have.length', 3);
          cy.get('[data-test="round-over-place"]').first().should('have.css', 'animation-name', 'place-in');
          cy.get('[data-test="round-over-place"]').last().should('be.visible');
          nothingScrolls();
        });
      });
    });
  });
});

describe('President end of round, 8 players against bots', () => {
  PHONES.forEach(([width, height]) => {
    it(`fits the round sheet of 8 players without scrolling (${width}x${height})`, () => {
      cy.viewport(width, height);
      hostLobby('Alice').then((id) => {
        for (let bots = 1; bots <= 7; bots++) {
          cy.get('[data-test="add-bot"]').click();
          cy.get('[data-test="player-count"]').should('contain.text', `${bots + 1} / 8`);
        }
        connectMyself(id).then((me) => autoplay(id, me, { ready: false }));
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');

        cy.get('[data-test="round-over-title"]', { timeout: 120000 }).should('have.text', 'Round 1 is over');
        cy.get('[data-test="round-over-place"]').should('have.length', 8);
        cy.get('[data-test="motion-card"]').should('not.exist');
        // Wait out the places coming in one by one, then measure.
        cy.get('[data-test="round-over-place"]').last().should('have.css', 'opacity', '1');
        nothingScrolls();
        cy.get('[data-test="ready"]').click();
        cy.get('[data-test="round"]', { timeout: 30000 }).should('have.text', 'Round 2 of 5');
        cy.get('[data-test="sheet-slot"]').should('not.exist');
        cy.get('[data-test="my-zone"]').should('exist');
      });
    });
  });
});
