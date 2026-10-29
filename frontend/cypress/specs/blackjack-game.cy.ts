// Blackjack on a scripted shoe (PUT /api/test/blackjack/{id}/deal, test profile only). Two nominal cases:
//  - A tournament of two: the browser is seat 0 (Alice), a scripted guest is seat 1 (Bob), maxBet = 100.
//    Hand 1 (button: Alice): Alice bets 10 with the chips, Bob 40; Alice hits to 20 and stands, Bob stands on 16, the
//    dealer stands on 17: Alice +10, Bob -40. Hand 2 (button: Bob): Bob goes all in (60) and busts, Alice repeats
//    her last bet with one tap and stands on 19 against 17: Bob is eliminated and the end screen puts Alice first.
//  - Solo: the browser plays alone, bets everything with Max and the dealer shows an Ace and a King: the hand is
//    settled at once, the stack is 0 and the end screen shows the net -100.
// The scripted shoe is the deal order (a card to each seat from the button, the dealer's up card, a second card to
// each seat, the dealer's hole card), then the cards drawn in the order they are drawn. Everything is waited on
// through DOM states and frames, never a fixed delay.
import { joinAsScriptedPlayer, type ScriptedPlayer } from '../support/stompPlayer';

type Guest = ScriptedPlayer & { token: string; seat: number };

//   Alice 9S 6H, Bob 10H 6D, dealer K up 7 hole; then Alice's hit
const HAND_1 = ['9S', '10H', 'KD', '6H', '6D', '7S', '5C'];
//   Bob 10C 6C, Alice 10S 9D, dealer 8 up 9 hole; then Bob's hit
const HAND_2 = ['10C', '10S', '8C', '6C', '9D', '9H', 'KD'];
//   Alice 9S 8H, dealer Ace up King hole: the dealer has Blackjack
const SOLO_HAND = ['9S', 'AS', '8H', 'KD'];

/** The scripted guest bets `amount` as soon as hand `hand` opens its betting. */
function scriptedBet(guest: Guest, hand: number, amount: number) {
  cy.wrap(guest, { log: false, timeout: 10000 }).should((g) => {
    const s = g.lastState();
    expect(Boolean(s && s.started && s.phase === 'BETTING' && s.handNumber === hand && s.availableActions.includes('BET')),
      `seat ${g.seat}: betting is open on hand ${hand}`).to.eq(true);
  });
  cy.then(() => guest.publish('/app/blackjack/bet', { amount }));
}

/** The scripted guest plays `action` (`hit`, `stand`...) as soon as it is its turn in hand `hand`. */
function scriptedAct(guest: Guest, hand: number, action: string) {
  cy.wrap(guest, { log: false, timeout: 10000 }).should((g) => {
    const s = g.lastState();
    expect(Boolean(s && s.started && s.phase === 'PLAYING' && s.handNumber === hand && s.currentSeat === g.seat
      && s.availableActions.includes(action.toUpperCase())), `seat ${g.seat}: my turn on hand ${hand}`).to.eq(true);
  });
  cy.then(() => guest.publish(`/app/blackjack/${action}`, {}));
}

describe('Blackjack, nominal games on a scripted shoe', () => {
  it('plays a tournament of two through an elimination to the end screen', () => {
    cy.visit('/blackjack');
    cy.get('[data-test="name"]').type('Alice');
    cy.get('[data-test="new-game"]').click();
    cy.url({ timeout: 10000 }).should('match', /\/blackjack\/room\/.+/);
    cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');

    cy.location('pathname').then((path) => path.split('/room/')[1]).then((id) => {
      joinAsScriptedPlayer(id, 'Bob', 'blackjack').then((bob) => {
        cy.get('[data-test="player-count"]').should('contain.text', '2 / 6');

        cy.get('[data-test="option-maxBet"]').contains('.choice', '100').click();
        cy.get('[data-test="option-maxBet"] .choice--on').should('contain.text', '100');

        cy.request('PUT', `/api/test/blackjack/${id}/deal`, { hands: [HAND_1, HAND_2] });
        cy.get('[data-test="start"]').should('not.be.disabled').click();
        cy.get('[data-test="blackjack-table"]', { timeout: 10000 }).should('be.visible');

        // Hand 1: Alice composes a bet of 10 with the chips; the others see "bet placed", never the amount.
        cy.get('[data-test="hand-number"]').should('contain.text', 'Hand 1 / 10');
        cy.get('[data-test="chip-clear"]').click();
        cy.get('[data-test="chip-5"]').click().click();
        cy.get('[data-test="bet-place"]').should('contain.text', 'Bet 10').click();
        cy.get('[data-test="waiting-bets"]').should('be.visible');
        scriptedBet(bob, 1, 40);

        // Alice acts first (the button is seat 0): 9 and 6 make 15, a 5 makes 20, she stands.
        cy.get('[data-test="my-seat"] [data-test="hand-total"]', { timeout: 20000 }).should('contain.text', '15');
        cy.get('[data-test="action-hit"]').should('be.visible').click();
        cy.get('[data-test="my-seat"] [data-test="hand-total"]').should('contain.text', '20');
        cy.get('[data-test="action-stand"]').should('be.visible').click();
        scriptedAct(bob, 1, 'stand');

        // The dealer stands on 17: Alice wins 10, Bob loses 40. The summary of hand 1 is shown while hand 2 opens.
        // The table tells the dealer's turn and the results (a few seconds) before the next betting opens.
        cy.get('[data-test="hand-number"]', { timeout: 30000 }).should('contain.text', 'Hand 2 / 10');
        cy.get('[data-test="hand-summary"]').should('be.visible');
        cy.get('[data-test="summary-dealer"]').should('contain.text', '17');
        cy.get('[data-test="summary-you"]').should('contain.text', '+10');
        cy.get('[data-test="my-stack"]').should('contain.text', '110');

        // Hand 2 (the button moved to Bob): "Bet 10" is the last bet, one tap. Bob goes all in with his 60.
        cy.get('[data-test="bet-place"]').should('contain.text', 'Bet 10').click();
        scriptedBet(bob, 2, 60);

        // Bob plays first, draws a King on 16 and busts; then it is Alice's turn: 19 against the dealer's 17.
        scriptedAct(bob, 2, 'hit');
        cy.get('[data-test="my-seat"] [data-test="hand-total"]', { timeout: 20000 }).should('contain.text', '19');
        cy.get('[data-test="action-stand"]', { timeout: 20000 }).should('be.visible').click();

        // Bob has no points left: only Alice is standing, the game ends and she is first.
        cy.get('[data-test="end"]', { timeout: 30000 }).should('be.visible');
        cy.get('[data-test="place-row"]').should('have.length', 2);
        cy.get('[data-test="place-name"]').then(($names) => {
          expect($names.eq(0).text(), '1st').to.eq('You');
          expect($names.eq(1).text(), '2nd').to.eq('Bob');
        });
        cy.get('[data-test="place-stack"]').then(($stacks) => {
          expect($stacks.eq(0).text(), 'Alice').to.contain('120');
          expect($stacks.eq(1).text(), 'Bob').to.contain('0');
        });
      });
    });
  });

  it('plays a solo game lost on a dealer blackjack', () => {
    cy.visit('/blackjack');
    cy.get('[data-test="name"]').type('Alice');
    cy.get('[data-test="new-game"]').click();
    cy.url({ timeout: 10000 }).should('match', /\/blackjack\/room\/.+/);
    cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="solo-hint"]').should('be.visible');

    cy.location('pathname').then((path) => path.split('/room/')[1]).then((id) => {
      cy.get('[data-test="option-maxBet"]').contains('.choice', '100').click();
      cy.get('[data-test="option-maxBet"] .choice--on').should('contain.text', '100');
      cy.request('PUT', `/api/test/blackjack/${id}/deal`, { hands: [SOLO_HAND] });
      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-test="blackjack-table"]', { timeout: 10000 }).should('be.visible');

      // All in with Max: the dealer's Ace is checked against its hole card, which is a King, and the hand ends.
      cy.get('[data-test="chip-max"]').click();
      cy.get('[data-test="bet-place"]').should('contain.text', 'Bet 100').click();

      cy.get('[data-test="end"]', { timeout: 30000 }).should('be.visible');
      cy.get('[data-test="solo-final"]').should('contain.text', '0 points').and('contain.text', '-100');
    });
  });
});
