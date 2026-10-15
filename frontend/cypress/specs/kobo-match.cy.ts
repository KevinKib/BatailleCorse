// The matching discard of Kobo and the event log, end to end. The browser is the host (seat 0) and Bob a scripted
// player in seat 1; the deal is fixed (test profile): seat 0 holds the first four cards below, seat 1 the next
// four, then the draw pile follows. A match never needs luck: the discard top and the cards in hand are known.
// Two rooms only: a spec that opens many leaves connections behind that starve the next ones.
import { awaitState, scriptedDiscardTurn, startKoboTable } from '../support/koboRoom';
import { actionBarIsReachable, expectNoOverlap, measureTable, tableDoesNotScrollSideways } from '../support/koboGeometry';

/** Both players memorise; the first turn is the browser's (seat 0). */
function memoriseBoth(bob: { publish: (d: string, b: Record<string, unknown>) => void }) {
  cy.get('[data-test="action-ready"]').click();
  cy.then(() => bob.publish('/app/kobo/ready', {}));
  cy.get('[data-test="action-draw"]').should('be.visible').and('not.be.disabled');
}

const slot = (seat: number, n: number) => `[data-test="slot-${seat}-${n}"]`;
const empty = (seat: number, n: number) => `[data-test="slot-empty-${seat}-${n}"]`;
const logLine = '[data-test="event-log-line"]';

describe('Kobo matching discard', () => {
  it('matches my own card, then misses one and takes a penalty card', () => {
    cy.viewport(375, 667);
    // I hold KH 3H JH 10H, Bob 9H 8H 7H 6H. I draw 3S and discard it (a 3 has no power); the next card of the
    // pile, 4S, is the penalty card.
    startKoboTable(2, ['KH', '3H', 'JH', '10H', '9H', '8H', '7H', '6H', '3S', '4S']).then(({ guests }) => {
      const bob = guests[0] as typeof guests[0] & { seat: number };
      memoriseBoth(bob);
      cy.get('[data-test="action-match"]').should('not.exist');
      cy.get(logLine).should('have.text', '').and('have.attr', 'aria-live', 'polite');

      cy.get('[data-test="action-draw"]').click();
      // The discard pile is still empty: nothing to match yet, so no hint and no button while I hold the card.
      cy.get('[data-test="match-hint"], [data-test="match-coach"], [data-test="action-match"]').should('not.exist');
      cy.get('[data-test="action-discard"]').click();
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '3 of spade');
      // Nothing else wants a tap now: no Match button, a tap on a card is the match (cypress/specs/kobo-click-actions.cy.ts).
      cy.get('[data-test="action-match"]').should('not.exist');
      cy.get('button[data-test^="slot-"]:not([disabled])').should('have.length', 8);
      cy.get('[data-test="prompt"]').should('not.exist');
      // The first time, the coach mark rings my hand and says both gestures; "Got it" remembers it in this browser.
      cy.get('[data-test="match-coach"]').should('be.visible').and('contain.text', 'Tap your card to throw it');
      cy.get('[data-test="my-tableau-ring"]').should('have.class', 'coach-ring');
      cy.get('[data-test="match-coach-dismiss"]').should('be.visible').invoke('outerHeight').should('be.gte', 44);
      actionBarIsReachable();
      cy.get('[data-test="match-coach-dismiss"]').click();
      cy.get('[data-test="match-coach"]').should('not.exist');
      cy.get('[data-test="my-tableau-ring"]').should('not.have.class', 'coach-ring');
      cy.window().then((w) => expect(w.localStorage.getItem('kobo.matchCoach')).to.eq('seen'));
      cy.get('[data-test="match-hint"]').should('be.visible')
        .and('have.text', "Same rank as the discard? Tap your card. A rival's card: tap it, then yours.");
      measureTable('one-tap match hint, 2 players');

      // My second card is a 3 like the top of the pile: ONE tap, the card goes to the discard pile.
      cy.get(slot(0, 1)).click();
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '3 of heart');
      cy.get(empty(0, 1)).should('exist').and('have.attr', 'data-highlighted', 'true');
      cy.get(logLine).should('have.text', 'You matched card 2 (3 of heart)');
      cy.get('[data-test="event-log"]').should('have.attr', 'data-outcome', 'good');
      cy.get('[data-test="action-match"]').should('not.exist');
      awaitState(bob, 'Bob sees my card gone', (s) => s.players[0].slots[1].occupied === false && s.discardTop.rank === '3');
      cy.then(() => {
        const match = bob.frames.find((f) => f.eventType === 'MATCH');
        expect(match, 'a MATCH event reached Bob').to.not.eq(undefined);
      });
      actionBarIsReachable();

      // My first card is a king, the top is a 3: a penalty card is added to my cards (a fifth slot).
      cy.get(slot(0, 0)).click();
      cy.get(logLine).should('have.text', 'You missed a match and took a penalty card');
      cy.get('[data-test="event-log"]').should('have.attr', 'data-outcome', 'bad');
      cy.get(slot(0, 4)).should('exist');
      cy.get(slot(0, 0)).should('exist');
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '3 of heart');
      awaitState(bob, 'Bob sees my penalty card', (s) => s.players[0].handSize === 4);
      tableDoesNotScrollSideways();
      expectNoOverlap();
      actionBarIsReachable();
    });
  });

  it('matches a card of Bob with a gift, then loses the race for another one and reads Bob\'s match', () => {
    cy.viewport(1280, 800);
    // I hold KH QH JH 10H, Bob 9H 5H 4H 6H. I draw 4S and discard it. Bob draws 5S on his turn and discards it.
    startKoboTable(2, ['KH', 'QH', 'JH', '10H', '9H', '5H', '4H', '6H', '4S', '5S']).then(({ guests }) => {
      const bob = guests[0] as typeof guests[0] & { seat: number };
      memoriseBoth(bob);
      // The lobby nudges rename the scripted guest, so his name is read from the table.
      cy.get('[data-test="opponent-seat"] [data-test="seat-label"]').invoke('text').as('bobName');
      cy.get('[data-test="action-draw"]').click();
      cy.get('[data-test="action-discard"]').click();
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '4 of spade');

      // Bob's third card is a 4: a first tap only PICKS it (nothing is sent), Cancel and Esc drop the pick...
      cy.get('[data-test="action-match"]').should('not.exist');
      cy.get(slot(1, 2)).should('have.attr', 'aria-label').and('contain', 'match this card, then pick one of yours to give');
      cy.get(slot(1, 2)).click();
      cy.get('[data-test="prompt"]').should('have.text', 'Pick one of your cards to give');
      cy.get(slot(1, 2)).should('have.attr', 'aria-pressed', 'true').and('have.attr', 'aria-label').and('contain', 'picked to match');
      cy.get('[data-test="match-hint"]').should('not.exist');
      cy.get('button[data-test^="slot-"]:not([disabled])').should('have.length', 4)
        .each(($b) => expect($b.attr('data-test')).to.match(/^slot-0-/));
      cy.get('[data-test="action-cancel"]').should('be.visible');
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '4 of spade');
      actionBarIsReachable();
      cy.get('[data-test="action-cancel"]').click();
      cy.get('[data-test="prompt"]').should('not.exist');
      cy.get(slot(1, 2)).should('not.have.attr', 'aria-pressed');
      cy.get('[data-test="match-hint"]').should('be.visible');
      cy.get(slot(1, 2)).click();
      cy.get('[data-test="prompt"]').should('have.text', 'Pick one of your cards to give');
      cy.get('body').type('{esc}');
      cy.get('[data-test="prompt"]').should('not.exist');
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '4 of spade');
      // ...and the second tap, on one of MY cards, is the card I give: only then is the match sent.
      cy.get(slot(1, 2)).click();
      cy.get(slot(0, 0)).click();
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '4 of heart');
      cy.get(empty(0, 0)).should('exist').and('have.attr', 'data-highlighted', 'true');
      cy.get(slot(1, 2)).should('have.attr', 'data-highlighted', 'true');
      cy.get<string>('@bobName').then((name) => {
        cy.get(logLine).should('have.text', `You matched ${name}'s card 3 (4 of heart), gave card 1`);
      });
      awaitState(bob, 'my king sits in Bob\'s third slot', (s) => s.players[1].slots[2].revision > 0 && s.players[0].slots[0].occupied === false);
      cy.get('[data-test="action-endTurn"]').click();
      scriptedDiscardTurn(bob);

      // Bob's turn is over, the 5 he discarded is on top. I pick his second card (a 5)...
      cy.get('[data-test="discard-pile"] img', { timeout: 10000 }).should('have.attr', 'alt', '5 of spade');
      cy.get(slot(1, 1)).click();
      cy.get('[data-test="prompt"]').should('have.text', 'Pick one of your cards to give');
      // ...but Bob matches that very card first: mine changed under my hand, nothing is sent, and I am told.
      cy.then(() => {
        const s = bob.lastState();
        bob.publish('/app/kobo/match', {
          seat: 1, slot: 1, expectedTopRank: s.discardTop.rank, expectedRevision: s.players[1].slots[1].revision,
        });
      });
      cy.get('[data-test="notice"]').should('have.text', 'Too late, nothing happened');
      cy.get('[data-test="action-match"]').should('not.exist');
      cy.get('[data-test="action-cancel"]').should('not.exist');
      // What Bob did is readable: the line, and his emptied slot.
      cy.get<string>('@bobName').then((name) => {
        cy.get(logLine).should('have.text', `${name} matched card 2 (5 of heart)`);
      });
      cy.get('[data-test="event-log"]').should('have.attr', 'data-outcome', 'good');
      cy.get(empty(1, 1)).should('have.attr', 'data-highlighted', 'true');
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '5 of heart');
      // The next thing that happens replaces the highlight (state, not a timer).
      cy.get('[data-test="action-draw"]').click();
      cy.get<string>('@bobName').then((name) => {
        cy.get(logLine).should('have.text', `${name} matched card 2 (5 of heart)`);
      });
      cy.get(empty(1, 1)).should('have.attr', 'data-highlighted', 'true');
      cy.get('[data-test="drawn-zone"] img').should('be.visible');
      cy.get('[data-test="action-discard"]').click();
      cy.get(logLine).should('contain.text', 'You discarded');
      cy.get('[data-highlighted]').should('not.exist');
      measureTable('log line after a discard, 2 players');
      tableDoesNotScrollSideways();
      expectNoOverlap();
      actionBarIsReachable();
    });
  });
});
