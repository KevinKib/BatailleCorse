// Mangola, endless sowings, end to end. A sowing that never ends is illegal under the owner's rules: the pit is not
// offered, and the player whose every move is endless has no move left and loses. The browser is the host (seat 0,
// side A, who starts), a scripted STOMP client the guest. The position below was found with the domain classes
// (hand-checked through the server: the two layouts are accepted, the host's only legal pit is b2, and after the
// guest's h3 the host has nothing left but a2). Pits are numbered row * 8 + column (a1 = 0 ... h4 = 31).
import {
  arrange, asHostInPlacement, canSow, confirmLayout, pitEl, sow,
} from '../support/mangolaPlayer';

const A2 = 8; const B2 = 9; const H3 = 23;

// Host (side A loop: a2..h2, then h1..a1): 25 seeds on a2 (endless), 2 on b2, one each on f2, h1, f1, d1, b1.
const HOST_TAPS: Array<[number, number]> = [[8, 25], [9, 2], [13, 1], [7, 1], [5, 1], [3, 1], [1, 1]];
// Guest (side B loop: a3..h3, then h4..a4).
const GUEST_LAYOUT = [0, 0, 0, 1, 1, 0, 1, 0, 3, 4, 5, 0, 4, 3, 6, 4];

describe('Mangola endless sowing (end to end)', () => {
  it('does not offer the endless pit, and loses when only endless moves are left', () => {
    asHostInPlacement().then(({ bob }) => {
      arrange(bob, GUEST_LAYOUT);
      confirmLayout(bob);
      cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'true');
      HOST_TAPS.forEach(([pit, times]) => { for (let i = 0; i < times; i += 1) pitEl(pit).click(); });
      cy.get('[data-test="seeds-left"]').should('contain.text', 'All seeds placed');
      // The layout is not only endless: no warning, and the pair is accepted.
      cy.get('[data-test="endless-warning"]').should('not.exist');
      cy.get('[data-test="confirm"]').should('not.be.disabled').click();
      cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');

      // a2 holds 25 seeds but its sowing never ends: it is not offered. b2 is the only pit I may sow.
      canSow(B2);
      pitEl(A2).should('have.attr', 'aria-label').and('contain', '25 seeds');
      pitEl(A2).should('not.have.attr', 'data-selectable');
      cy.get('[data-test^="pit-"][data-selectable="true"]').should('have.length', 1);
      pitEl(B2).click();
      cy.get('[data-test="skip-sowing"]', { timeout: 10000 }).click();
      cy.get('[data-test="sowing-hand"]').should('not.exist');

      // The guest answers; the host is left with a2 alone, endless, so it has no move: defeat.
      sow(bob, H3);
      cy.get('[data-test="end"]', { timeout: 20000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'DEFEAT');
      cy.get('[data-test="end-winner"]').should('have.text', 'Winner: Bob');
      cy.get('.end-sub').should('contain.text', 'no move left');
    });
  });
});
