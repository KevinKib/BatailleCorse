// The turn of the browser player in Kobo, branch by branch. The browser is the host (seat 0), Bob is a scripted
// player in seat 1 who only draws, discards and ends his turns (his cards have no power). The deal is fixed
// (test profile): seat 0 holds KH QH JH 10H, seat 1 holds 9H 8H 7H 6H, then the draw pile follows the order given
// below. Two rooms only: a spec that opens many leaves connections behind that starve the next ones.
import { awaitState, scriptedDiscardTurn, startKoboTable } from '../support/koboRoom';
import { actionBarIsReachable, expectNoOverlap, measureTable, tableDoesNotScrollSideways } from '../support/koboGeometry';

const HANDS = ['KH', 'QH', 'JH', '10H', '9H', '8H', '7H', '6H'];

/** Both players memorise; the first turn is the browser's (seat 0). */
function memoriseBoth(bob: { publish: (d: string, b: Record<string, unknown>) => void }) {
  cy.get('[data-test="action-ready"]').click();
  cy.then(() => bob.publish('/app/kobo/ready', {}));
  cy.get('[data-test="action-draw"]').should('be.visible').and('not.be.disabled');
}

const slot = (seat: number, n: number) => `[data-test="slot-${seat}-${n}"]`;

describe('Kobo turn', () => {
  it('swaps the drawn card, discards a powerless one, peeks my own card and one of Bob\'s, then announces Kobo', () => {
    cy.viewport(375, 667);
    // Draw order: 3S (I swap it in), 4S (Bob), 2C (I discard it), 3C (Bob), 7S (I peek mine), 5S (Bob),
    // 9S (I peek Bob's), 6S (Bob's final turn).
    startKoboTable(2, [...HANDS, '3S', '4S', '2C', '3C', '7S', '5S', '9S', '6S']).then(({ guests }) => {
      const bob = guests[0] as typeof guests[0] & { seat: number };
      memoriseBoth(bob);

      // Turn 1: draw, then swap the drawn card with my second card.
      cy.get('[data-test="action-draw"]').click();
      cy.get('[data-test="drawn-zone"]').should('have.attr', 'aria-label', 'Your drawn card: 3 of spade');
      cy.get('[data-test="prompt"]').should('have.text', 'Pick one of your cards to swap with the drawn card');
      cy.get('[data-test="action-discard"]').should('be.visible');
      cy.get('[data-test="action-draw"]').should('not.exist');
      // Only my cards can be picked, and only mine.
      cy.get('button[data-test^="slot-"]:not([disabled])').should('have.length', 4)
        .each(($b) => expect($b.attr('data-test')).to.match(/^slot-0-/));
      actionBarIsReachable();
      measureTable('drawn card: prompt + Discard, 2 players');
      cy.get(slot(0, 1)).click();
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', 'queen of heart');
      cy.get('[data-test="drawn-zone"] img').should('not.exist');
      cy.get('[data-test="action-endTurn"]').should('be.visible');
      cy.get('[data-test="action-announce"]').should('have.text', 'Kobo!');
      cy.get('button[data-test^="slot-"]:not([disabled])').should('not.exist');
      // The swapped card goes in face down, I do not get to see it.
      cy.get(slot(0, 1)).should('have.attr', 'aria-label', 'Your card 2, face down');
      actionBarIsReachable();
      measureTable('turn end: End turn + Kobo!, 2 players');
      cy.get('[data-test="action-endTurn"]').click();
      cy.get('[data-test="opponent-seat"][data-seat="1"] [data-test="seat-current"]').should('be.visible');
      scriptedDiscardTurn(bob);

      // Turn 3: draw a 2 and discard it: no power, the turn is over at once.
      cy.get('[data-test="action-draw"]', { timeout: 10000 }).should('be.visible').click();
      cy.get('[data-test="drawn-zone"]').should('have.attr', 'aria-label', 'Your drawn card: 2 of club');
      cy.get('[data-test="action-discard"]').click();
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '2 of club');
      cy.get('[data-test="action-endTurn"]').should('be.visible');
      cy.get('[data-test="action-skip"]').should('not.exist');
      cy.get('[data-test="action-endTurn"]').click();
      scriptedDiscardTurn(bob);

      // Turn 5: a 7 has a power, look at one of my cards; it stays face up until I hide it.
      cy.get('[data-test="action-draw"]', { timeout: 10000 }).should('be.visible').click();
      cy.get('[data-test="action-discard"]').click();
      cy.get('[data-test="prompt"]').should('have.text', 'Look at one of your cards');
      cy.get('[data-test="action-skip"]').should('be.visible');
      cy.get('button[data-test^="slot-"]:not([disabled])').should('have.length', 4)
        .each(($b) => expect($b.attr('data-test')).to.match(/^slot-0-/));
      cy.get(slot(0, 0)).click();
      cy.get(slot(0, 0)).should('have.attr', 'aria-label', 'Your card 1: king of heart');
      cy.get('[data-test="action-hide"]').should('be.visible');
      cy.get('[data-test="action-endTurn"]').should('be.visible');
      actionBarIsReachable();
      measureTable('turn end: End turn + Kobo! + Hide, 2 players');
      cy.get('[data-test="action-hide"]').click();
      cy.get(slot(0, 0)).should('have.attr', 'aria-label', 'Your card 1, face down');
      cy.get('[data-test="action-hide"]').should('not.exist');
      cy.get('[data-test="action-endTurn"]').click();
      scriptedDiscardTurn(bob);

      // Turn 7: a 9 looks at another player's card; Bob's cards only are clickable. Then I announce Kobo.
      cy.get('[data-test="action-draw"]', { timeout: 10000 }).should('be.visible').click();
      cy.get('[data-test="action-discard"]').click();
      cy.get('[data-test="prompt"]').should('have.text', "Look at another player's card");
      cy.get('button[data-test^="slot-"]:not([disabled])').should('have.length', 4)
        .each(($b) => expect($b.attr('data-test')).to.match(/^slot-1-/));
      cy.get(slot(1, 3)).click();
      cy.get(slot(1, 3)).should('have.attr', 'aria-label').and('contain', '6 of heart');
      cy.get('[data-test="action-announce"]').click();

      // The announcement: the others see it in the DTO, with Bob's last turn still to play.
      awaitState(bob, 'Kobo announced', (s) => s.announcer === 0 && s.finalTurnsRemaining.includes(1));
      cy.then(() => {
        expect(bob.frames.some((f) => f.eventType === 'KOBO'), 'a KOBO event reached Bob').to.eq(true);
      });
      cy.get('[data-test="opponent-seat"][data-seat="1"] [data-test="seat-final-turn"]').should('be.visible');
      cy.get('[data-test="my-zone"] [data-test="seat-announcer"]').should('be.visible');
      cy.get('[data-test="action-announce"]').should('not.exist');
      scriptedDiscardTurn(bob);
      // The last turn ends the round: every card is face up for everybody.
      awaitState(bob, 'round resolved', (s) => s.revealed === true);
      // The score sheet rises under the table; Bob's cards, face up now, stay on the table above it.
      cy.get('[data-test="score-sheet"]').should('be.visible');
      cy.get('[data-test="slot-1-0"]').should('have.attr', 'aria-label').and('contain', '9 of heart');
      tableDoesNotScrollSideways();
      expectNoOverlap();
    });
  });

  it('plays a blind swap with a cancel, a king, and skips a power', () => {
    cy.viewport(1280, 800);
    // Draw order: JS (blind swap), 3S (Bob), KS (king), 3D (Bob), 8S (I skip its power).
    startKoboTable(2, [...HANDS, 'JS', '3S', 'KS', '3D', '8S']).then(({ guests }) => {
      const bob = guests[0] as typeof guests[0] & { seat: number };
      memoriseBoth(bob);

      // Turn 1: a jack swaps one of my cards with one of Bob's, blind, in two clicks.
      cy.get('[data-test="action-draw"]').click();
      cy.get('[data-test="action-discard"]').click();
      cy.get('[data-test="prompt"]').should('have.text', 'Blind swap: pick one of your cards');
      cy.get('[data-test="action-cancel"]').should('not.exist');
      cy.get(slot(0, 0)).click();
      cy.get('[data-test="prompt"]').should('have.text', "Now pick another player's card to swap with");
      cy.get(slot(0, 0)).should('have.attr', 'aria-pressed', 'true');
      cy.get('button[data-test^="slot-"]:not([disabled])').should('have.length', 4)
        .each(($b) => expect($b.attr('data-test')).to.match(/^slot-1-/));
      // Escape drops the half-made choice.
      cy.get('body').type('{esc}');
      cy.get('[data-test="prompt"]').should('have.text', 'Blind swap: pick one of your cards');
      cy.get(slot(0, 0)).should('not.have.attr', 'aria-pressed');
      // And so does Cancel.
      cy.get(slot(0, 1)).click();
      cy.get('[data-test="action-cancel"]').click();
      cy.get('[data-test="prompt"]').should('have.text', 'Blind swap: pick one of your cards');
      // Now for real: my card 3 with Bob's card 2.
      cy.get(slot(0, 2)).click();
      cy.get(slot(1, 1)).click();
      cy.get('[data-test="action-endTurn"]').should('be.visible');
      awaitState(bob, 'the swap reached Bob', (s) => s.phase === 'TURN_END' && s.players[1].slots[1].revision > 0);
      cy.get('[data-test="action-endTurn"]').click();
      scriptedDiscardTurn(bob);

      // Turn 3: a king looks at another player's card, then swaps one of mine with it.
      cy.get('[data-test="action-draw"]', { timeout: 10000 }).should('be.visible').click();
      cy.get('[data-test="action-discard"]').click();
      cy.get('[data-test="prompt"]').should('have.text', "King: look at another player's card");
      cy.get(slot(1, 2)).click();
      cy.get(slot(1, 2)).should('have.attr', 'aria-label').and('contain', '7 of heart');
      cy.get('[data-test="prompt"]').should('have.text', 'Pick one of your cards to swap with the card you looked at');
      cy.get('[data-test="action-skip"]').should('be.visible');
      cy.get('[data-test="action-hide"]').should('be.visible');
      cy.get('button[data-test^="slot-"]:not([disabled])').should('have.length', 4)
        .each(($b) => expect($b.attr('data-test')).to.match(/^slot-0-/));
      cy.get(slot(0, 3)).click();
      cy.get('[data-test="action-endTurn"]').should('be.visible');
      awaitState(bob, 'the king swap reached Bob', (s) => s.phase === 'TURN_END' && s.players[1].slots[2].revision > 0);
      cy.get('[data-test="action-endTurn"]').click();
      scriptedDiscardTurn(bob);

      // Turn 5: an 8 has a power, and I choose not to use it.
      cy.get('[data-test="action-draw"]', { timeout: 10000 }).should('be.visible').click();
      cy.get('[data-test="action-discard"]').click();
      cy.get('[data-test="prompt"]').should('have.text', 'Look at one of your cards');
      cy.get('[data-test="action-skip"]').click();
      cy.get('[data-test="action-skip"]').should('not.exist');
      cy.get('[data-test="action-endTurn"]').should('be.visible');
      cy.get('button[data-test^="slot-"]:not([disabled])').should('not.exist');
      actionBarIsReachable();
      cy.get('[data-test="action-endTurn"]').click();
      cy.get('[data-test="opponent-seat"][data-seat="1"] [data-test="seat-current"]').should('be.visible');
      expectNoOverlap();
    });
  });
});
