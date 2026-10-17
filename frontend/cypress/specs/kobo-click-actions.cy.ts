// Kobo actions on direct clicks, end to end: the piles draw and discard, and a click on a card is a match when no
// action of the phase wants it (my own card at once, another player's card then the card I give). The deal is
// fixed (test profile): seat 0 holds the first four cards, seat 1 the next four, then the draw pile follows.
// Two rooms only: a spec that opens many leaves connections behind that starve the next ones.
import { awaitState, scriptedDiscardTurn, startKoboTable } from '../support/koboRoom';
import {
  actionBarIsReachable, expectNoOverlap, expectSameRects, measureTable, snapshotRects, tableDoesNotScrollSideways,
  type Rects,
} from '../support/koboGeometry';

const slot = (seat: number, n: number) => `[data-test="slot-${seat}-${n}"]`;
const empty = (seat: number, n: number) => `[data-test="slot-empty-${seat}-${n}"]`;
const logLine = '[data-test="event-log-line"]';
const discardTop = (alt: string) => cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', alt);

function memoriseBoth(bob: { publish: (d: string, b: Record<string, unknown>) => void }) {
  cy.get('[data-test="action-ready"]').click();
  cy.then(() => bob.publish('/app/kobo/ready', {}));
  cy.get('[data-test="draw-pile"]').should('match', 'button').and('not.be.disabled');
}

describe('Kobo actions on direct clicks', () => {
  it('draws and discards by clicking the piles, then matches my own cards with one click', () => {
    cy.viewport(375, 667);
    // I hold KH 3H JH 10H, Bob 9H 8H 7H 6H. I draw 3S and discard it; the next card, 4S, is the penalty card.
    startKoboTable(2, ['KH', '3H', 'JH', '10H', '9H', '8H', '7H', '6H', '3S', '4S']).then(({ guests }) => {
      const bob = guests[0] as typeof guests[0] & { seat: number };
      cy.get('[data-test="draw-pile"]').should('not.match', 'button');
      memoriseBoth(bob);
      cy.get('[data-test="discard-pile"]').should('not.match', 'button');

      // The draw pile draws; the bar's Draw button is still there as the fallback.
      cy.get('[data-test="draw-pile"]').should('have.attr', 'aria-label').and('match', /^Draw a card, [0-9]+ cards left$/);
      // The pile as a button shows what the figure showed: its picture, its count and its caption.
      cy.get('[data-test="draw-pile"] img').should('be.visible');
      cy.get('[data-test="draw-count"]').should('be.visible');
      cy.get('[data-test="draw-pile"]').should('contain.text', 'Draw');
      cy.get('[data-test="draw-pile"]').click();
      cy.get('[data-test="drawn-zone"] img').should('have.attr', 'alt', '3 of spade');
      cy.get('[data-test="action-draw"]').should('not.exist');
      cy.get('[data-test="action-discard"]').should('be.visible');
      // Holding a card, a click on a slot swaps it: no slot is a match, the discard pile is the click target.
      cy.get(slot(0, 1)).should('not.have.attr', 'data-match');
      cy.get('[data-test="draw-pile"]').should('not.match', 'button');
      cy.get('[data-test="discard-pile"]').should('match', 'button')
        .and('have.attr', 'aria-label').and('match', /^Discard the drawn card/);
      cy.get('[data-test="discard-count"]').should('be.visible');
      cy.get('[data-test="discard-pile"]').should('contain.text', 'Discard');
      let holding: Rects;
      snapshotRects().then((r) => { holding = r; });
      cy.get('[data-test="discard-pile"]').click();
      discardTop('3 of spade');
      cy.get('[data-test="discard-count"]').should('have.text', '1');
      cy.get('[data-test="discard-pile"]').should('not.match', 'button');

      // Nothing else wants a click on a card now: every card says that a click is a match, with one light border.
      cy.get('button[data-test^="slot-"]:not([disabled])').should('have.length', 8);
      cy.get(slot(0, 1)).should('have.attr', 'aria-label', 'Your card 2, face down: match this card')
        .and('have.attr', 'title', 'Match this card').and('have.attr', 'data-match', 'true')
        .and('have.css', 'cursor', 'pointer');
      cy.get(slot(0, 1)).then(($s) => {
        const s = getComputedStyle($s[0]);
        expect(s.borderTopWidth, 'a 1 px border').to.eq('1px');
        expect(s.boxShadow, 'no ring or glow').to.eq('none');
      });
      // A tap is the match, so there is no Match button: the hint line (or the first-time note) says how.
      cy.get('[data-test="action-match"]').should('not.exist');
      cy.get('[data-test="match-hint"], [data-test="match-coach"]').should('be.visible');
      // The hint line appears without moving anything: the piles, the grids and the drawn zone keep their boxes.
      snapshotRects().then((r) => expectSameRects(holding, r, ['piles', 'draw', 'discard', 'drawn', 'opponentGrid', 'opponentSeat', 'myGrid']));
      actionBarIsReachable();
      measureTable('one-click match, 2 players');

      // My second card is a 3 like the top: one click matches it, no Match button needed.
      let beforeMatch: Rects;
      snapshotRects().then((r) => { beforeMatch = r; });
      cy.get(slot(0, 1)).click();
      discardTop('3 of heart');
      cy.get(empty(0, 1)).should('exist').and('have.attr', 'data-highlighted', 'true');
      // An emptied slot keeps the size of a card slot (its row and its column do not shrink); nothing moves.
      cy.get(slot(0, 0)).then(($full) => {
        cy.get(empty(0, 1)).then(($empty) => {
          const f = $full[0].getBoundingClientRect();
          const e = $empty[0].getBoundingClientRect();
          expect(e.width, 'empty slot width').to.eq(f.width);
          expect(e.height, 'empty slot height').to.eq(f.height);
        });
      });
      snapshotRects().then((r) => expectSameRects(beforeMatch, r, ['piles', 'draw', 'discard', 'drawn', 'opponentGrid', 'opponentSeat', 'myGrid']));
      cy.get(logLine).should('have.text', 'You matched card 2 (3 of heart)');
      cy.get('[data-test="event-log"]').should('have.attr', 'data-outcome', 'good');
      awaitState(bob, 'Bob sees my card gone', (s) => s.players[0].slots[1].occupied === false && s.discardTop.rank === '3');

      // My first card is a king: one click, one penalty card (a fifth slot).
      cy.get(slot(0, 0)).click();
      cy.get(logLine).should('have.text', 'You missed a match and took a penalty card');
      cy.get('[data-test="event-log"]').should('have.attr', 'data-outcome', 'bad');
      cy.get(slot(0, 4)).should('exist');
      awaitState(bob, 'Bob sees my penalty card', (s) => s.players[0].handSize === 4);
      tableDoesNotScrollSideways();
      expectNoOverlap();
      actionBarIsReachable();
    });
  });

  it('leaves the click to a pending power, then matches a card of Bob with a gift in two clicks', () => {
    cy.viewport(1280, 800);
    // I hold KH QH JH 10H, Bob 9H 5H 4H 6H. The first card drawn is a 7 (look at one of my cards), then Bob
    // draws 4S and discards it.
    startKoboTable(2, ['KH', 'QH', 'JH', '10H', '9H', '5H', '4H', '6H', '7S', '4S']).then(({ guests }) => {
      const bob = guests[0] as typeof guests[0] & { seat: number };
      memoriseBoth(bob);
      cy.get('[data-test="draw-pile"]').click();
      cy.get('[data-test="discard-pile"]').click();
      discardTop('7 of spade');

      // The 7 is waiting for a card to look at: those clicks are its own, nothing is a match.
      cy.get('[data-test="prompt"]').should('have.text', 'Look at one of your cards');
      cy.get('button[data-test^="slot-"]:not([disabled])').should('have.length', 4)
        .each(($b) => expect($b.attr('data-test')).to.match(/^slot-0-/));
      cy.get('[data-match]').should('not.exist');
      // Stray taps: no hint (a tap here is the power's), a rival card cannot be tapped, and a tap on my card
      // looks at it; nothing is thrown on the pile. The Match button is the explicit way to match meanwhile.
      cy.get('[data-test="match-hint"], [data-test="match-coach"]').should('not.exist');
      cy.get('[data-test="action-match"]').should('be.visible').and('have.attr', 'aria-pressed', 'false');
      cy.get(slot(1, 2)).should('be.disabled');
      cy.get(slot(1, 2)).click({ force: true });
      discardTop('7 of spade');
      cy.get(logLine).should('not.contain.text', 'match');
      // The explicit Match button arms the mode (every card can be picked); Esc and Cancel give the power its taps back.
      cy.get('[data-test="action-match"]').click();
      cy.get('[data-test="action-match"]').should('have.attr', 'aria-pressed', 'true');
      cy.get('[data-test="prompt"]').should('have.text', 'Pick the card to match');
      cy.get('button[data-test^="slot-"]:not([disabled])').should('have.length', 8);
      cy.get('[data-test="action-cancel"]').should('be.visible');
      actionBarIsReachable();
      cy.get('body').type('{esc}');
      cy.get('[data-test="action-match"]').should('have.attr', 'aria-pressed', 'false');
      cy.get('[data-test="prompt"]').should('have.text', 'Look at one of your cards');
      cy.get('[data-test="action-match"]').click();
      cy.get('[data-test="action-cancel"]').click();
      cy.get('[data-test="prompt"]').should('have.text', 'Look at one of your cards');
      discardTop('7 of spade');
      cy.get(slot(0, 0)).click();
      cy.get(slot(0, 0)).should('have.attr', 'aria-label').and('contain', 'king of heart');
      discardTop('7 of spade');
      cy.get(logLine).should('not.contain.text', 'match');

      // The turn ends; Bob discards a 4, which is on top of the pile at the start of my turn.
      cy.get('[data-test="action-endTurn"]').click();
      let waiting: Rects;
      snapshotRects().then((r) => { waiting = r; });
      scriptedDiscardTurn(bob);
      discardTop('4 of spade');
      cy.get('[data-test="slot-1-2"]').should('have.attr', 'data-match', 'true');
      cy.get('[data-test="match-hint"], [data-test="match-coach"]').should('be.visible');
      // The hint line comes without moving the piles or the grids (wide table).
      snapshotRects().then((r) => expectSameRects(waiting, r, ['piles', 'draw', 'discard', 'drawn', 'opponentGrid', 'opponentSeat', 'myGrid']));

      // Bob's third card is a 4: one click on it, then one click on the card I give.
      cy.get(slot(1, 2)).click();
      cy.get('[data-test="prompt"]').should('have.text', 'Pick one of your cards to give');
      cy.get('button[data-test^="slot-"]:not([disabled])').should('have.length', 4)
        .each(($b) => expect($b.attr('data-test')).to.match(/^slot-0-/));
      cy.get('[data-test="action-cancel"]').should('be.visible');
      cy.get(slot(0, 1)).click();
      discardTop('4 of heart');
      cy.get('[data-test="opponent-seat"] [data-test="seat-label"]').invoke('text').then((name) => {
        cy.get(logLine).should('have.text', `You matched ${name}'s card 3 (4 of heart), gave card 2`);
      });
      cy.get(empty(0, 1)).should('exist');
      cy.get('[data-test="action-cancel"]').should('not.exist');
      measureTable('after a one-click match with a gift, 2 players');
      tableDoesNotScrollSideways();
      expectNoOverlap();
      actionBarIsReachable();
    });
  });
});
