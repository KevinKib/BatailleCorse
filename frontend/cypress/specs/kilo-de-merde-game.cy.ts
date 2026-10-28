// A scripted Kilo de merde game on a fixed deal (PUT /api/test/kilo-de-merde/{id}/deal, test profile only), three seats:
// the browser is seat 0 (Alice), scripted guests are seat 1 (Bob) and seat 2 (Carol, on Alice's right).
//  - Deal: Alice K♠ K♥ K♦ Q♣, Bob J♠ J♥ Q♦ Q♥, Carol K♣ J♦ J♣ Q♠; the kilo pile is the single card A♠ (a one-round game).
//  - Step 1: everybody passes to the seat on their left (seat i to seat i + 1): Alice Q♣, Bob Q♦, Carol K♣. Alice then
//    holds the four Kings.
//  - Race: Alice slaps by tapping the pile, Bob slaps through STOMP; the race closes with all but one seat slapped, so Carol is
//    last and draws the Ace (100 kg).
//  - The pile is empty: the game ends. Alice and Bob share 1st place at 0 kg, Carol is 3rd with 100 kg.
// The round pause is 0 under `test`, so the round banner is covered by Vitest, not here. Everything is waited on through
// DOM states and frames, never a fixed delay.
import { joinAsScriptedPlayer, type ScriptedPlayer } from '../support/stompPlayer';
import { expectGap, expectNoOverlap } from '../support/spacing';

type Guest = ScriptedPlayer & { token: string; seat: number };

const card = (rank: string, suit: string) => ({ rank, suit });

/** The scripted guest passes `cardName` (e.g. `CLUB_QUEEN`) once the first pass step is open for it. */
function scriptedPass(guest: Guest, cardName: string) {
  cy.wrap(guest, { log: false, timeout: 10000 }).should((g) => {
    const s = g.lastState();
    expect(Boolean(s && s.started && s.phase === 'PASSING' && s.round === 1 && s.step === 1),
      `seat ${g.seat}: the first pass step is open`).to.eq(true);
  });
  cy.then(() => {
    const picked = guest.lastState().hand.find((c: { name: string }) => c.name === cardName);
    guest.publish('/app/kilo-de-merde/pass', { card: picked });
  });
}

/** The scripted guest slaps once the race is open and it is not in the slap order yet. */
function scriptedSlap(guest: Guest) {
  cy.wrap(guest, { log: false, timeout: 10000 }).should((g) => {
    const s = g.lastState();
    expect(Boolean(s && s.started && s.phase === 'RACE' && s.availableActions.includes('SLAP')),
      `seat ${g.seat}: the race is open`).to.eq(true);
  });
  cy.then(() => guest.publish('/app/kilo-de-merde/slap', {}));
}

describe('Kilo de merde, three players on a fixed deal', () => {
  it('passes one step to four Kings, races to the slap and ends with the last slapper taking 100 kg', () => {
    cy.visit('/kilo-de-merde');
    cy.get('[data-test="name"]').type('Alice');
    cy.get('[data-test="new-game"]').click();
    cy.url({ timeout: 10000 }).should('match', /\/kilo-de-merde\/room\/.+/);
    cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');

    cy.location('pathname').then((path) => path.split('/room/')[1]).then((id) => {
      joinAsScriptedPlayer(id, 'Bob', 'kilo-de-merde').then((bob) => {
        joinAsScriptedPlayer(id, 'Carol', 'kilo-de-merde').then((carol) => {
          cy.get('[data-test="player-count"]').should('contain.text', '3 / 10');

          cy.request('PUT', `/api/test/kilo-de-merde/${id}/deal`, {
            hands: [
              [card('KING', 'SPADE'), card('KING', 'HEART'), card('KING', 'DIAMOND'), card('QUEEN', 'CLUB')],
              [card('JACK', 'SPADE'), card('JACK', 'HEART'), card('QUEEN', 'DIAMOND'), card('QUEEN', 'HEART')],
              [card('KING', 'CLUB'), card('JACK', 'DIAMOND'), card('JACK', 'CLUB'), card('QUEEN', 'SPADE')],
            ],
            pile: [card('ACE', 'SPADE')],
          });
          cy.get('[data-test="start"]').should('not.be.disabled').click();
          cy.get('[data-test="kilo-table"]', { timeout: 10000 }).should('be.visible');

          // The table: my four cards, the pile with its single card, nobody has any kilos yet.
          cy.get('[data-test="round-heading"]').should('contain.text', 'Round 1');
          cy.get('[data-card]').should('have.length', 4);
          cy.get('[data-test="pile-count"]').should('have.text', '1');
          cy.get('[data-test="seat-plate"]').should('have.length', 3);
          cy.get('[data-cy="rules-toggle"]').should('be.visible');
          expectNoOverlap('[data-test="hand"]', '[data-test="pile"]');
          expectGap('[data-test="pile"]', '[data-test="hand"]', 4);

          // Step 1: Alice taps the Queen of clubs, the guests pass theirs; she receives the King of clubs from Carol.
          cy.get('[data-card="CLUB_QUEEN"]').should('not.be.disabled').click();
          cy.get('[data-test="passing"]').should('be.visible');
          scriptedPass(bob, 'DIAMOND_QUEEN');
          scriptedPass(carol, 'CLUB_KING');
          cy.get('[data-card="CLUB_KING"]', { timeout: 10000 }).should('exist');
          cy.get('[data-card]').should('have.length', 4);

          // Alice holds the four Kings and slaps the pile; Bob slaps next: with three seats the race closes once all but one have
          // slapped, so Carol, who did not, is last and draws the Ace without any slap of hers.
          cy.get('[data-test="pile"]').should('not.be.disabled').click();
          scriptedSlap(bob);

          // The pile is empty after Carol's draw: the game is over.
          cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
          cy.get('[data-test="end-winner"]').should('contain.text', 'You');
          cy.get('[data-test="final-row-0"] [data-test="final-rank"]').should('have.text', '1');
          cy.get('[data-test="final-row-0"] [data-test="final-total"]').should('have.text', '0');
          cy.get('[data-test="final-row-1"] [data-test="final-rank"]').should('have.text', '1');
          cy.get('[data-test="final-row-1"] [data-test="final-total"]').should('have.text', '0');
          cy.get('[data-test="final-row-2"] [data-test="final-rank"]').should('have.text', '3');
          cy.get('[data-test="final-row-2"] [data-test="final-total"]').should('have.text', '100');
        });
      });
    });
  });
});
