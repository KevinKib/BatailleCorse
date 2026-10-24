// Mangola draws every seed and writes the exact count on each pit. One position with pits of 1, 2, 3, 5, 9, 12 and
// 30 seeds: the host (side A, who starts) lays out a2 2, b2 12, c2 9, d2 5, e2 1, f2 3; the scripted guest a4 30,
// b4 1, c4 1 (the guest's layout is in the order of its loop, which starts at a4). Sowing a2 drops its first seed in
// b2, the pit of 12.
import { arrange, confirmLayout, asHostInPlacement, pitEl, startWithAnimationSpeed, tapDraft, type Scripted } from '../support/mangolaPlayer';

const A2 = 8; const B2 = 9; const C2 = 10; const D2 = 11; const E2 = 12; const F2 = 13; const G2 = 14;
const A4 = 24; const B4 = 25;

function playFromThisPosition(): Cypress.Chainable<{ bob: Scripted }> {
  return asHostInPlacement().then(({ bob }) => {
    tapDraft([[A2, 2], [B2, 12], [C2, 9], [D2, 5], [E2, 1], [F2, 3]]);
    cy.get('[data-test="seeds-left"]').should('contain.text', 'All seeds placed');
    arrange(bob, [30, 1, 1, ...Array<number>(13).fill(0)]);
    confirmLayout(bob);
    cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'true');
    cy.get('[data-test="confirm"]').should('not.be.disabled').click();
    cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="placement-editor"]').should('not.exist');
    return cy.wrap({ bob });
  });
}

const seedsOf = (pit: number) => pitEl(pit).find('[data-test="seed"]');
const badgeOf = (pit: number) => pitEl(pit).find('[data-test="pit-count"]');
const overlaps = (a: DOMRect, b: DOMRect) => a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom;

describe('Mangola visible seeds', () => {
  // The default speed (Normal) is the one the sowing is seen at.
  beforeEach(() => startWithAnimationSpeed('normal'));

  [[375, 667], [1280, 720]].forEach(([width, height]) => {
    it(`draws every seed and the exact count of each pit at ${width}x${height}, with a board of the same size`, () => {
      cy.viewport(width, height);
      playFromThisPosition().then(() => {
        // Set SHOT=before to photograph another build (the screenshots of docs/screenshots/mangola-visible-seeds).
        cy.screenshot(`seeds-${Cypress.env('SHOT') ?? 'after'}-${width}`, { capture: 'viewport' });

        // Every seed is drawn, whatever the pile, and the badge writes the exact count; an empty pit has none.
        [[B2, 12], [C2, 9], [D2, 5], [E2, 1], [F2, 3], [A2, 2], [A4, 30], [B4, 1]].forEach(([pit, count]) => {
          seedsOf(pit).should('have.length', count);
          badgeOf(pit).should('have.text', String(count));
          pitEl(pit).should('have.attr', 'aria-label').and('contain', `${count} seed`);
        });
        seedsOf(G2).should('have.length', 0);
        badgeOf(G2).should('not.exist');

        // The size of the pits and of the board does not depend on what the pits hold.
        cy.get('[data-test="mangola-board"] button.mg-pit').should('have.length', 32).then(($pits) => {
          const first = $pits[0].getBoundingClientRect();
          $pits.each((_, el) => {
            const r = el.getBoundingClientRect();
            expect(r.width, 'pit width').to.be.closeTo(first.width, 0.5);
            expect(r.height, 'pit height').to.be.closeTo(first.height, 0.5);
            expect(r.width, 'tap target').to.be.at.least(44);
          });
          const grid = Cypress.$('.mg-board__grid')[0].getBoundingClientRect();
          expect(grid.height, 'four rows of cells').to.be.closeTo(first.height * 4, 2);
        });
        cy.document().then((doc) => {
          const el = doc.scrollingElement as Element;
          expect(el.scrollWidth, 'no horizontal scroll').to.be.at.most(el.clientWidth + 1);
        });

        // The seeds stay in their bowl; the badge stays in its cell, clear of the direction chevron.
        [B2, A4].forEach((pit) => {
          pitEl(pit).then(($pit) => {
            const cell = $pit[0].getBoundingClientRect();
            const bowl = $pit.find('.mg-pit__bowl')[0].getBoundingClientRect();
            $pit.find('[data-test="seed"]').each((_, seed) => {
              const r = seed.getBoundingClientRect();
              const centreX = r.left + r.width / 2;
              const centreY = r.top + r.height / 2;
              const reach = Math.hypot(centreX - (bowl.left + bowl.width / 2), centreY - (bowl.top + bowl.height / 2)) + r.width / 2;
              expect(reach, 'seed inside the bowl').to.be.at.most(bowl.width / 2 + 0.5);
            });
            const badge = $pit.find('[data-test="pit-count"]')[0].getBoundingClientRect();
            expect(badge.left, 'badge left').to.be.at.least(cell.left - 0.5);
            expect(badge.right, 'badge right').to.be.at.most(cell.right + 0.5);
            expect(badge.top, 'badge top').to.be.at.least(cell.top - 0.5);
            expect(badge.bottom, 'badge bottom').to.be.at.most(cell.bottom + 0.5);
            expect(badge.right - cell.left, 'badge in the right half').to.be.at.least(cell.width / 2);
            expect(badge.bottom - cell.top, 'badge in the top half').to.be.at.most(cell.height / 2);
            expect(overlaps(badge, $pit.find('[data-test="chevron"]')[0].getBoundingClientRect()), 'badge clear of the chevron').to.eq(false);
          });
        });
      });
    });
  });

  it('shows the arriving seed under the hand at the top edge, moves no seed of the pile, and flashes the count', () => {
    playFromThisPosition().then(() => {
      seedsOf(B2).should('have.length', 12);
      const before: string[] = [];
      seedsOf(B2).each(($seed) => { before.push($seed.attr('style') ?? ''); });
      pitEl(A2).click();
      // A seed lands in the pit of 12: the count goes to 13, in gold, and the twelve seeds already there have not moved.
      badgeOf(B2).should('have.text', '13').and('have.attr', 'data-changed', 'true');
      seedsOf(B2).should('have.length', 13).then(($seeds) => {
        const after = [...$seeds].map((seed) => seed.getAttribute('style') ?? '');
        expect(after.slice(0, 12), 'the seeds already in the pit did not move').to.deep.eq(before);
      });
      // The hand sits on the top edge of the pit it is over, not over its bowl.
      cy.get('[data-test="sowing-hand"]').should(($hand) => {
        const hand = $hand.find('.mg-board__hand-seeds')[0].getBoundingClientRect();
        const pit = Cypress.$(`[data-test="pit-${String.fromCharCode(97 + (Number($hand.attr('data-pit')) % 8))}${Math.floor(Number($hand.attr('data-pit')) / 8) + 1}"]`)[0].getBoundingClientRect();
        expect(hand.top + hand.height / 2, 'hand on the top edge').to.be.closeTo(pit.top, 3);
      });
      cy.get('[data-test="skip-sowing"]', { timeout: 10000 }).click();
      cy.get('[data-test="sowing-hand"]').should('not.exist');
    });
  });
});
