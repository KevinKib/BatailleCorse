// A scripted 99 game on a fixed deal (PUT /api/test/ninety-nine/{id}/deal, test profile only), lives = 2: the browser is
// seat 0 (Alice), a scripted guest is seat 1 (Bob).
//  - Round 1: Alice plays the 9 (total 0), Bob plays a 2, Alice plays the King (total 99); Bob holds only 3, 5, 6, so he
//    is stuck and loses a life (the server shows his hand to the table and deals the next round at once under `test`).
//  - Round 2: Bob, who lost, leads with a 2; Alice plays the King again; Bob is stuck on his last life, so he is
//    eliminated and the end screen names Alice the winner.
// The nominal path only: bots, quick mode and the 12-seat table are covered by the backend and Vitest suites.
// Everything is waited on through DOM states and frames, never a fixed delay.
import { joinAsScriptedPlayer, waitForFrame, type ScriptedPlayer } from '../support/stompPlayer';

type Guest = ScriptedPlayer & { token: string; seat: number };

// Deal order is one card per live seat in turn (Alice, Bob, Alice, Bob...), three each, then the stock: its first card
// is drawn by whoever plays first, the next one by whoever plays second.
//   Alice 9H KH 4H, Bob 2C 3C 5C, stock 8H (Alice's draw), 6C (Bob's draw)
const ROUND_1 = ['9H', '2C', 'KH', '3C', '4H', '5C', '8H', '6C'];
//   Alice KS 7S 8S, Bob 2D 3D 5D, stock 6D (Bob leads, so he draws first)
const ROUND_2 = ['KS', '2D', '7S', '3D', '8S', '5D', '6D'];

/** The scripted guest plays `cardName` (e.g. `CLUB_2`) as soon as it is its turn in `round`. */
function scriptedPlay(guest: Guest, round: number, cardName: string) {
  cy.wrap(guest, { log: false, timeout: 10000 }).should((g) => {
    const s = g.lastState();
    expect(Boolean(s && s.started && s.phase === 'PLAYING' && s.round === round && s.currentSeat === g.seat),
      `seat ${g.seat}: my turn in round ${round}`).to.eq(true);
  });
  cy.then(() => {
    const card = guest.lastState().hand.find((c: { name: string }) => c.name === cardName);
    guest.publish('/app/ninety-nine/play', { card, aceValue: null });
  });
}

describe('99, two players on a fixed deal', () => {
  it('plays a round to a bust, then a second round to the end screen', () => {
    cy.visit('/ninety-nine');
    cy.get('[data-test="name"]').type('Alice');
    cy.get('[data-test="new-game"]').click();
    cy.url({ timeout: 10000 }).should('match', /\/ninety-nine\/room\/.+/);
    cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');

    cy.location('pathname').then((path) => path.split('/room/')[1]).then((id) => {
      joinAsScriptedPlayer(id, 'Bob', 'ninety-nine').then((bob) => {
        cy.get('[data-test="player-count"]').should('contain.text', '2 / 12');

        // Two lives each, so that a single lost life is visible and the second one ends the game.
        cy.get('[data-test="option-lives"]').contains('.choice', '2').click();
        cy.get('[data-test="option-lives"] .choice--on').should('contain.text', '2');

        cy.request('PUT', `/api/test/ninety-nine/${id}/deal`, { round: 1, cards: ROUND_1, firstSeat: 0 });
        cy.request('PUT', `/api/test/ninety-nine/${id}/deal`, { round: 2, cards: ROUND_2 });
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="ninety-nine-table"]', { timeout: 10000 }).should('be.visible');

        // Round 1: Alice leads with three cards, the total is 0, the rules chip is there.
        cy.get('[data-test="round"]').should('have.text', 'Round 1');
        cy.get('[data-test="status"]').should('contain.text', 'Your turn');
        cy.get('[data-test="total"]').should('have.attr', 'aria-label', 'Total 0 of 99');
        cy.get('[data-card]').should('have.length', 3);
        cy.get('[data-cy="rules-toggle"]').should('be.visible');

        // Alice plays the 9 (adds nothing), Bob a 2, then Alice the King: the total becomes 99.
        cy.get('[data-card="HEART_9"]').should('not.be.disabled').click();
        scriptedPlay(bob, 1, 'CLUB_2');
        cy.get('[data-test="total"]').should('have.attr', 'aria-label', 'Total 2 of 99');
        cy.get('[data-test="status"]').should('contain.text', 'Your turn');
        cy.get('[data-card="HEART_KING"]').should('not.be.disabled').click();

        // Bob holds 3, 5 and 6: nothing fits under 99. His stuck hand is public and he loses a life.
        waitForFrame(bob, (f) => f.state?.phase === 'ROUND_OVER' && f.state.lastBust?.seat === bob.seat).then((frame) => {
          expect(frame.state.lastBust.totalBefore, 'total at the bust').to.eq(99);
          expect(frame.state.lastBust.livesLeft, 'lives left').to.eq(1);
          expect(frame.state.lastBust.hand.map((c: { name: string }) => c.name).sort(), 'the stuck hand')
            .to.deep.eq(['CLUB_3', 'CLUB_5', 'CLUB_6']);
        });

        // Round 2: Bob, who lost the round, leads; his plate shows the life he has left.
        cy.get('[data-test="round"]').should('have.text', 'Round 2');
        cy.get('[data-test="seat-plate"][data-seat="1"]').should('have.attr', 'aria-label').and('contain', 'Bob, 1 life left');
        scriptedPlay(bob, 2, 'DIAMOND_2');
        cy.get('[data-test="total"]').should('have.attr', 'aria-label', 'Total 2 of 99');
        cy.get('[data-test="status"]').should('contain.text', 'Your turn');
        cy.get('[data-card="SPADE_KING"]').should('not.be.disabled').click();

        // Bob is stuck on his last life: he is out and Alice wins.
        cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
        cy.get('[data-test="end-winner"]').should('have.text', 'Winner: You');
        cy.get('[data-test="place-row"]').should('have.length', 2);
        cy.get('[data-test="place-name"]').then(($names) => {
          expect($names.eq(0).text(), '1st').to.eq('You');
          expect($names.eq(1).text(), '2nd').to.eq('Bob');
        });
      });
    });
  });
});
