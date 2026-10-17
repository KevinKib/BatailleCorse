// Visual polish of the Kobo table: a neutral page behind the felt, bigger cards at 2 players (same layout), no
// panel around my zone. The browser is the host (seat 0). Screenshots go to the `screenshotsFolder`, named after
// the `SHOT_PREFIX` env var when given (used to compare before and after).
import { DECK_CODES, startKoboTable } from '../support/koboRoom';
import { everySlotAtLeast44, expectCentred, expectNoOverlap, expectUnframed, tableDoesNotScrollSideways } from '../support/koboGeometry';

const viewports = [
  { width: 375, height: 667, label: 'phone', card: { 2: { opponent: 44, mine: 54 }, 3: { opponent: 34, mine: 46 } } },
  { width: 1280, height: 800, label: 'desktop', card: { 2: { opponent: 52, mine: 68 }, 3: { opponent: 48, mine: 64 } } },
] as const;

const cardWidth = (selector: string) => (doc: Document) =>
  Math.round(doc.querySelector(selector)!.getBoundingClientRect().width);

describe('Kobo visual polish', () => {
  viewports.forEach(({ width, height, label, card }) => {
    ([2, 3] as const).forEach((players) => {
      it(`${players} players on a ${label} (${width}x${height})`, () => {
        cy.viewport(width, height);
        startKoboTable(players, DECK_CODES.slice(0, 4 * players + 1)).then(({ guests }) => {
          cy.get('[data-test="opponent-seat"]').should('have.length', players - 1);

          // The page behind the table is neutral (no green) and my zone has no panel.
          cy.get('.kobo-screen').then(($s) => {
            const bg = getComputedStyle($s[0]).backgroundImage;
            expect(bg, 'page background').to.contain('rgb(29, 32, 31)');
            expect(bg, 'page background').to.not.contain('rgb(30, 92, 48)');
          });
          cy.get('[data-test="my-zone"]').then(($z) => {
            const style = getComputedStyle($z[0]);
            expect(style.backgroundImage, 'my zone fill').to.eq('none');
            expect(style.borderTopWidth, 'my zone border').to.eq('0px');
            expect(style.borderTopLeftRadius, 'my zone corners').to.eq('0px');
          });

          // Card sizes: bigger at 2 players, unchanged at 3.
          cy.document().then((doc) => {
            expect(cardWidth('[data-test="opponent-seat"] .playing_card')(doc), 'opponent card').to.eq(card[players].opponent);
            expect(cardWidth('[data-test="my-zone"] .playing_card')(doc), 'my card').to.eq(card[players].mine);
          });

          // Two players: the bigger cards still fit the frame, which does not need to scroll.
          if (players === 2) {
            cy.get('[data-test="kobo-table"]').then(($t) => {
              expect($t[0].scrollHeight, 'table scrollHeight').to.be.at.most($t[0].clientHeight + 1);
            });
          }
          everySlotAtLeast44();
          expectNoOverlap();
          tableDoesNotScrollSideways();
          cy.get('[data-test="my-zone"] [data-test="seat-label"]').should('be.visible');
          cy.get('[data-test="my-zone"] [data-test="seat-total"]').should('be.visible');
          cy.get('[data-test="my-zone"] [data-test="seat-cards"]').should('be.visible');
          // Memorising: "Memorise your two cards" is centred in my zone whatever the button beside it, and nothing
          // around my zone (the zone, the bar, its row, the hand) has a frame, outline or shadow.
          cy.get('[data-test="phase"]').should('have.text', 'Memorise your two cards').then(($p) => {
            expectCentred($p[0], $p[0].closest('[data-test="my-zone"]')!, 'phase text');
          });
          cy.get('[data-test="my-zone"]').then(($z) => {
            const parts = [$z[0], ...Array.from($z[0].querySelectorAll('.kobo-actions, .actions-row, .actions-text, .actions-buttons, .my-hand, .my-tableau, .kobo-seat'))];
            parts.forEach((el, i) => expectUnframed(el, `my zone part ${i} (${el.className})`));
          });
          cy.get('[data-test="action-ready"]').then(($b) => {
            const z = $b[0].closest('[data-test="my-zone"]')!.getBoundingClientRect();
            const r = $b[0].getBoundingClientRect();
            expect(r.left, 'ready button inside the zone').to.be.at.least(z.left);
            expect(r.right, 'ready button inside the zone').to.be.at.most(z.right);
          });
          cy.screenshot(`${Cypress.env('SHOT_PREFIX') ?? 'shot'}-${players}p-${width}`, { capture: 'viewport', overwrite: true });

          // My turn: the "Playing now" marker and the ring on my plate stay readable without a panel.
          cy.get('[data-test="action-ready"]').click();
          cy.then(() => guests.forEach((g) => g.publish('/app/kobo/ready', {})));
          cy.get('[data-test="action-draw"]').should('be.visible').and('not.be.disabled');
          cy.get('[data-test="my-zone"] [data-test="seat-current"]').should('be.visible');
          cy.get('[data-test="my-zone"] [data-test="seat-label"]').then(($l) => {
            expect(getComputedStyle($l[0]).borderTopColor, 'turn ring').to.not.eq('rgba(255, 255, 255, 0.1)');
          });
          // The dot of the turn ring is clear of the name, mine and the others'.
          cy.get('[data-test="seat-label"]').each(($l) => {
            if (!$l.hasClass('seat-label--active')) return;
            const dot = getComputedStyle($l[0], '::before');
            const gap = parseFloat(getComputedStyle($l[0]).paddingLeft) - parseFloat(dot.left) - parseFloat(dot.width);
            expect(gap, 'gap between the turn dot and the name').to.be.at.least(6);
          });
          expectNoOverlap();
          tableDoesNotScrollSideways();
          cy.screenshot(`${Cypress.env('SHOT_PREFIX') ?? 'shot'}-${players}p-${width}-turn`, { capture: 'viewport', overwrite: true });
        });
      });
    });
  });
});
