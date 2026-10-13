// The memorising phase of Kobo: the browser is the host (seat 0), a scripted player is seat 1. The deal is fixed
// (test profile): seat 0 holds KH QH JH 10H, seat 1 holds 9H 8H 7H 6H, and the starting cards are the 3rd and 4th.
import { awaitState, startKoboTable } from '../support/koboRoom';
import { actionBarIsReachable, everySlotAtLeast44, tableDoesNotScrollSideways } from '../support/koboGeometry';

const DEAL = ['KH', 'QH', 'JH', '10H', '9H', '8H', '7H', '6H', '3S'];

describe('Kobo memorisation', () => {
  [
    { width: 375, height: 667, label: 'phone' },
    { width: 1280, height: 800, label: 'desktop' },
  ].forEach(({ width, height, label }) => {
    it(`shows my two cards until I have memorised them, and nobody else sees them (${label})`, () => {
      cy.viewport(width, height);
      startKoboTable(2, DEAL).then(({ guests }) => {
        const bob = guests[0];
        // My two starting cards (slots 3 and 4) are face up, the other two and everything of Bob's are face down.
        cy.get('[data-test="slot-0-2"]').should('have.attr', 'aria-label', 'Your card 3: jack of heart');
        cy.get('[data-test="slot-0-3"]').should('have.attr', 'aria-label', 'Your card 4: 10 of heart');
        cy.get('[data-test="slot-0-0"]').should('have.attr', 'aria-label', 'Your card 1, face down');
        cy.get('[data-test="slot-0-1"]').should('have.attr', 'aria-label', 'Your card 2, face down');
        cy.get('[data-test="opponent-seat"] button[data-test^="slot-"]').each(($b) => {
          expect($b.attr('aria-label')).to.contain('face down');
        });
        // No card of the table can be clicked while memorising.
        cy.get('button[data-test^="slot-"]:not([disabled])').should('not.exist');
        cy.get('[data-test="phase"]').should('have.text', 'Memorise your two cards');
        cy.get('[data-test="action-ready"]').should('have.text', 'I have memorised');
        actionBarIsReachable();
        everySlotAtLeast44();
        tableDoesNotScrollSideways();

        // Bob's own view: his starting cards (7H, 6H) and no value of mine anywhere.
        awaitState(bob, 'Bob sees his two cards', (s) => s.startingCards.length === 2);
        cy.then(() => {
          const s = bob.lastState();
          expect(s.startingCards.map((c: any) => `${c.card.rank}${c.card.suit}`)).to.deep.eq(['7HEART', '6HEART']);
          expect(s.players[0].slots.every((slot: any) => slot.card === null), 'my cards in his state').to.eq(true);
          expect(JSON.stringify(bob.frames), 'my values in his frames').to.not.match(/"(JACK|QUEEN|KING)"/);
        });

        // Bob is ready first: I see his check mark, mine is still to come.
        cy.then(() => bob.publish('/app/kobo/ready', {}));
        cy.get('[data-test="opponent-seat"][data-seat="1"] [data-test="seat-ready"]').should('be.visible');
        cy.get('[data-test="my-zone"] [data-test="seat-ready"]').should('not.exist');

        // I press the button: my cards turn face down, the button goes, my check mark shows, the turn is mine.
        cy.get('[data-test="action-ready"]').click();
        cy.get('[data-test="slot-0-2"]').should('have.attr', 'aria-label', 'Your card 3, face down');
        cy.get('[data-test="slot-0-3"]').should('have.attr', 'aria-label', 'Your card 4, face down');
        cy.get('[data-test="action-ready"]').should('not.exist');
        cy.get('[data-test="phase"]').should('have.text', 'Your turn: draw a card');
        cy.get('[data-test="action-draw"]').should('be.visible').and('not.be.disabled');
        awaitState(bob, 'the round started', (s) => s.phase === 'DRAW' && s.currentSeat === 0);
        actionBarIsReachable();
      });
    });
  });
});
