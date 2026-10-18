// A scripted President round on a fixed deal (PUT /api/test/president/{id}/deal, test profile only): the browser is
// seat 0 and leads with sets of three, the two scripted players pass or lead theirs. The round ends 1st seat 0,
// 2nd seat 1, 3rd seat 2; both sides press Ready and round 2 opens on the exchange, where the browser (President)
// gives back two cards with the minimal control. Everything is waited on through DOM states and frames.
import {
  awaitState, hostLobby, joinGuests, noHorizontalScroll, scriptedPass, scriptedPlay, scriptedReady, threePlayerDeal,
} from '../support/presidentRoom';

const SUITS = ['HEART', 'DIAMOND', 'CLUB'];
const hand = (rank: string) => SUITS.map((suit) => `[data-card="${suit}_${rank}"]`);

/** Taps the three cards of `rank`, plays them and waits for the pile to show them. */
function playTriple(rank: string, goesOut = false) {
  cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
  hand(rank).forEach((selector) => cy.get(selector).click({ force: true }));
  cy.get('[data-test="play"]').should('not.be.disabled').click();
  if (goesOut) return;
  // The last set takes me out and closes the trick at once: there is no pile to show.
  cy.get('[data-test="trick-by"]').should('have.text', 'You played');
  cy.get('[data-test="trick-card"]').should('have.length', 3);
  cy.get('[data-test="phase-text"]').should('not.have.text', 'Your turn');
}

[375, 1280].forEach((width) => {
  describe(`President round at ${width} px`, () => {
    it('plays a whole round, then the exchange of round 2', () => {
      cy.viewport(width, width === 375 ? 667 : 800);
      hostLobby('Alice').then((id) => {
        joinGuests(id, ['Bob', 'Carol']).then(([bob, carol]) => {
          cy.request('PUT', `/api/test/president/${id}/deal`, { round: 1, cards: threePlayerDeal() });
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="president-table"]', { timeout: 10000 }).should('be.visible');

          // Seat 0 holds the king of hearts: it leads. 18 cards, no title yet, the rules as chips.
          cy.get('[data-test="phase-text"]').should('have.text', 'Your turn');
          cy.get('[data-test^="hand-card-"]').should('have.length', 18);
          cy.get('[data-test="round"]').should('have.text', 'Round 1 of 5');
          cy.get('[data-test="rule-chip"]').should('contain.text', 'After a pass: play again');
          cy.get('[data-test="play-hint"]').should('have.text', 'Tap the cards you want to play.');
          cy.get('[data-test="play"]').should('be.disabled');
          noHorizontalScroll();

          // A card is a toggle button with a name; one card alone is a legal lead, a mixed pair is not.
          cy.get('[data-card="HEART_KING"]').click({ force: true })
            .should('have.attr', 'aria-pressed', 'true')
            .and('have.attr', 'aria-label', 'King of hearts, selected');
          cy.get('[data-test="play"]').should('not.be.disabled');
          cy.get('[data-card="HEART_QUEEN"]').click({ force: true });
          cy.get('[data-test="play"]').should('be.disabled');
          cy.get('[data-test="play-hint"]').should('have.text', 'Cards of one rank only.');
          cy.get('[data-test="clear"]').click();
          cy.get('[data-card="HEART_KING"]').should('have.attr', 'aria-pressed', 'false');

          // Seat 0 goes out first, one set after the other, the others passing.
          ['KING', 'QUEEN', 'JACK', '10', '9'].forEach((rank) => {
            playTriple(rank);
            scriptedPass(bob);
            scriptedPass(carol);
          });
          playTriple('8', true);
          cy.get('[data-test="my-zone"] [data-test="seat-out"]').should('have.text', 'Out, place 1');

          // Seat 1 leads next and goes out second, seat 2 passing.
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

          // Round over: the placeholder lists the places and Ready is the way on.
          cy.get('[data-test="round-over-title"]', { timeout: 10000 }).should('have.text', 'Round 1 is over');
          cy.get('[data-test="round-over-place"]').then(($rows) => {
            const row = (i: number, part: string) => $rows.eq(i).find(`[data-test="place-${part}"]`).text();
            expect(row(0, 'label'), '1st').to.eq('1. Alice');
            expect(row(0, 'title')).to.eq('President');
            expect(row(1, 'label'), '2nd').to.eq('2. Bob');
            expect(row(1, 'title')).to.eq('Neutral');
            expect(row(2, 'label'), '3rd').to.eq('3. Carol');
            expect(row(2, 'title')).to.eq('Scum');
            expect(row(0, 'points')).to.match(/^\+\d+$/);
          });
          cy.get('[data-test="ready"]').click();
          cy.get('[data-test="round-over-waiting"]').should('have.text', 'Waiting for the others');
          scriptedReady(bob);
          scriptedReady(carol);

          // Round 2 opens on the exchange: Alice (President) chooses two cards to give back (the panel's own
          // behaviour is in president-exchange.cy.ts).
          cy.get('[data-test="exchange-instruction"]', { timeout: 10000 }).should('have.text', 'Choose 2 cards to give to Carol.');
          cy.get('[data-test="round"]').should('have.text', 'Round 2 of 5');
          cy.get('[data-test="seat-title"]').should('exist');
          cy.get('[data-test="exchange-give"]').should('have.text', 'Give 2 cards to Carol').and('be.disabled');
          cy.get('[data-test="pass"]').should('not.exist');
          // The Scum's two best cards are already in my hand: 17 or 18 dealt, plus 2.
          cy.get('[data-test^="hand-card-"]').should('have.length.within', 19, 20);
          cy.get('[data-test="hand-card-0"]').click({ force: true });
          cy.get('[data-test="hand-card-1"]').click({ force: true });
          cy.get('[data-test="exchange-give"]').should('not.be.disabled').click();

          // Exchange done: the Scum leads round 2; the table names who plays.
          cy.get('[data-test="phase-text"]', { timeout: 10000 }).should('have.text', 'Carol is playing');
          awaitState(bob, 'round 2 is being played', (s) => s.round === 2 && s.phase === 'PLAYING');
          noHorizontalScroll();
        });
      });
    });
  });
});
