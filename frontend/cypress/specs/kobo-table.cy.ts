// The Kobo table for 2, 4 and 6 players (read only): the browser under test is the host (seat 0), scripted STOMP
// clients are the others. The deal of round 1 is fixed (test profile) so what is face up is known.
import { DECK_CODES, startKoboTable, type Guest } from '../support/koboRoom';
import { everySlotAtLeast44, expectNoOverlap, tableDoesNotScrollSideways } from '../support/koboGeometry';
import { waitForFrame } from '../support/stompPlayer';

const viewports = [
  { width: 375, height: 667, label: 'phone' },
  { width: 1280, height: 800, label: 'desktop' },
] as const;

describe('Kobo table', () => {
  viewports.forEach(({ width, height, label }) => {
    [2, 4, 6].forEach((players) => {
      it(`lays out ${players} players on a ${label} (${width}x${height})`, () => {
        cy.viewport(width, height);
        const deal = DECK_CODES.slice(0, 4 * players + 1);
        startKoboTable(players, deal).then(({ guests }) => {
          // Every seat is on the table, in play order after me (seat 0 is first of the others).
          cy.get('[data-test="opponent-seat"]').should('have.length', players - 1);
          cy.get('[data-test="opponent-seat"]').then(($s) => {
            expect($s.toArray().map((el) => Number(el.getAttribute('data-seat')))).to.deep.eq(
              Array.from({ length: players - 1 }, (_, i) => i + 1));
          });
          cy.get('[data-test="my-zone"] button[data-test^="slot-"]').should('have.length', 4);
          cy.get('[data-test="opponent-seat"] button[data-test^="slot-"]').should('have.length', 4 * (players - 1));

          // Piles: the discard top is a visible card and both counts are visible and match the server's state.
          waitForFrame(guests[0], (f) => f.state?.started === true).then((frame) => {
            const state = frame.state;
            // Nothing is discarded while the players memorise: an empty place, then the card once there is one.
            cy.get('[data-test="discard-pile"]').should('be.visible');
            if (state.discardTop) cy.get('[data-test="discard-pile"] img').should('be.visible');
            else cy.get('[data-test="discard-pile"] img').should('not.exist');
            cy.get('[data-test="discard-count"]').should('be.visible').and('have.text', String(state.discardSize));
            cy.get('[data-test="draw-count"]').should('be.visible').and('have.text', String(state.drawPileSize));
          });

          // Names and the number of cards of each opponent are visible.
          cy.get('[data-test="opponent-seat"]').each(($s) => {
            expect($s.find('[data-test="seat-label"]').text().trim(), 'name').to.not.be.empty;
          });
          cy.get('[data-test="opponent-seat"] [data-test="seat-cards"]').should('have.length', players - 1).each(($c) => {
            expect($c.text()).to.eq('4 cards');
          });
          cy.get('[data-test="round"]').should('have.text', 'Round 1');
          cy.get('[data-test="phase"]').should('have.text', 'Memorise your two cards');
          cy.get('[data-cy="game-timer"]').should('be.visible');
          cy.get('[data-cy="rules-toggle"]').should('be.visible');
          cy.get('[data-test="back"]').should('be.visible');

          // Geometry.
          expectNoOverlap();
          everySlotAtLeast44();
          tableDoesNotScrollSideways();
          cy.window().then((win) => {
            // My cards are always on screen, at any player count.
            win.document.querySelectorAll('[data-test="my-zone"] button[data-test^="slot-"]').forEach((el) => {
              const r = el.getBoundingClientRect();
              expect(r.bottom, 'my card bottom').to.be.at.most(win.innerHeight);
              expect(r.right, 'my card right').to.be.at.most(win.innerWidth);
            });
          });
        });
      });
    });
  });

  it('shows only the starting cards the server gave me, face up, and the opponents face down', () => {
    cy.viewport(1280, 800);
    const deal = DECK_CODES.slice(0, 9);
    startKoboTable(2, deal).then(({ guests }) => {
      const bob = guests[0] as Guest;
      // The server tells Bob which of his own cards he may look at; the browser must show mine the same way.
      waitForFrame(bob, (f) => f.state?.started === true && f.state.startingCards?.length === 2).then((frame) => {
        const mine = frame.state.startingCards as Array<{ slot: number }>;
        expect(mine.map((c) => c.slot).sort()).to.deep.eq([2, 3]);
      });
      cy.get('[data-test="my-zone"] button[data-test^="slot-"]').then(($b) => {
        const labels = $b.toArray().map((el) => el.getAttribute('aria-label')!);
        const faceUp = labels.filter((l) => !l.endsWith('face down'));
        expect(faceUp, 'face-up cards').to.have.length(2);
        expect(labels[2]).to.match(/^Your card 3: /);
        expect(labels[3]).to.match(/^Your card 4: /);
        expect(labels[0]).to.eq('Your card 1, face down');
        expect(labels[1]).to.eq('Your card 2, face down');
      });
      cy.get('[data-test="opponent-seat"] button[data-test^="slot-"]').each(($b) => {
        expect($b.attr('aria-label')).to.match(/face down$/);
      });
      // The fixed deal gives seat 0 the first four codes of the deck (KH QH JH 10H), in slot order.
      cy.get('[data-test="slot-0-2"]').should('have.attr', 'aria-label', 'Your card 3: jack of heart');
      cy.get('[data-test="slot-0-3"]').should('have.attr', 'aria-label', 'Your card 4: 10 of heart');
    });
  });

  it('shows who is ready while the others memorise', () => {
    cy.viewport(375, 667);
    startKoboTable(3, DECK_CODES.slice(0, 13)).then(({ guests }) => {
      cy.get('[data-test="opponent-seat"] [data-test="seat-ready"]').should('not.exist');
      guests[0].publish('/app/kobo/ready', {});
      cy.get('[data-test="opponent-seat"][data-seat="1"] [data-test="seat-ready"]').should('be.visible');
      cy.get('[data-test="opponent-seat"][data-seat="2"] [data-test="seat-ready"]').should('not.exist');
      cy.get('[data-test="my-zone"] [data-test="seat-ready"]').should('not.exist');
    });
  });
});
