// Mangola placement, end to end: the hidden draft, taking a layout back, the reload during placement, and the
// refusal of a pair of layouts that would leave the player who starts without a move. The browser is the host
// (seat 0, side A, who starts), a scripted STOMP client the guest (seat 1, side B); every wait is on a DOM state or
// on a frame, never on a delay. Pits are numbered row * 8 + column (a1 = 0 ... h4 = 31); a layout is 16 counts in
// the order of its owner's loop (side A: a2..h2 then h1..a1; side B: a3..h3 then h4..a4).
import { waitForFrame } from '../support/stompPlayer';
import {
  arrange, asHostInPlacement, canSow, confirmLayout, myHalfSignature, pitEl, tapDraft, TWO_PER_PIT,
} from '../support/mangolaPlayer';

const A2 = 8;
const seeds = (pit: number, text: string) => pitEl(pit).should('have.attr', 'aria-label').and('contain', text);

/** Side A's draft with every move endless: 24 seeds on a2, then a single seed on b2, d2, f2, h2, g1, e1, c1, a1. */
const ENDLESS_TAPS: Array<[number, number]> = [[8, 24], [9, 1], [11, 1], [13, 1], [15, 1], [6, 1], [4, 1], [2, 1], [0, 1]];
/** Side B with all 32 seeds in h4: nothing of side B faces side A's inner row, so no sowing of side A can capture. */
const ALL_IN_H4 = [0, 0, 0, 0, 0, 0, 0, 0, 32, 0, 0, 0, 0, 0, 0, 0];

describe('Mangola placement (end to end)', () => {
  it('keeps each draft hidden from the other seat until the second confirmation reveals both', () => {
    asHostInPlacement().then(({ bob }) => {
      cy.get('[data-test="preset-checkerboard"]').click();
      cy.get('[data-test="seeds-left"]').should('contain.text', 'All seeds placed');
      cy.get('[data-test="confirm"]').should('not.be.disabled').click();
      // The guest hears that the host is ready, and nothing of what the host laid out.
      waitForFrame(bob, (f) => f.state?.players?.some((p: any) => p.seat === 0 && p.ready === true)).then(() => {
        const placing = bob.frames.map((f) => f.state).filter((s) => s && s.phase === 'PLACEMENT');
        expect(placing.length, 'placement frames received').to.be.greaterThan(0);
        placing.forEach((s) => {
          expect(s.board.slice(0, 16), "the host's pits are blank").to.deep.eq(Array(16).fill(null));
          expect(s.players.find((p: any) => p.seat === 0).seeds, "the host's seed count is hidden").to.eq(null);
        });
      });
      // The host's browser sees the same for the guest: 16 ghost pits, still Placing.
      cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'false');
      cy.get('[data-test="ghost-pit"]').should('have.length', 16);
      arrange(bob, TWO_PER_PIT);
      cy.get('[data-test="opponent-status"]').should('contain.text', 'Placing');
      cy.get('[data-test="edit-layout"]').should('be.visible');
      cy.get('[data-test="mangola-board"]').should('not.exist');
      cy.then(() => bob.publish('/app/mangola/confirmLayout', {}));
      // Second confirmation: both layouts are written on the board and the guest's frame carries the host's half.
      cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
      waitForFrame(bob, (f) => f.state?.phase === 'PLAY').then((frame) => {
        const board: number[] = frame.state.board;
        expect(board.every((n) => typeof n === 'number'), 'the 32 pits are public').to.eq(true);
        expect(board.slice(0, 16).reduce((a, b) => a + b, 0), "the host's half").to.eq(32);
      });
    });
  });

  it('takes a confirmation back with Edit again until the opponent has confirmed, then changes the layout', () => {
    asHostInPlacement().then(({ bob }) => {
      cy.get('[data-test="preset-two-per-pit"]').click();
      cy.get('[data-test="confirm"]').should('not.be.disabled').click();
      waitForFrame(bob, (f) => f.state?.players?.some((p: any) => p.seat === 0 && p.ready === true));
      cy.get('[data-test="edit-layout"]').click();
      cy.get('[data-test="confirm"]').should('not.be.disabled');
      cy.wrap(null).should(() => {
        expect(bob.lastState().players.find((p: any) => p.seat === 0).ready, 'the host withdrew').to.eq(false);
      });
      // The layout can be changed again, then confirmed: a2 holds three seeds, b2 one.
      cy.get('[data-test="mode-remove"]').click();
      pitEl(9).click();
      cy.get('[data-test="mode-add"]').click();
      pitEl(A2).click();
      cy.get('[data-test="seeds-left"]').should('contain.text', 'All seeds placed');
      seeds(A2, '3 seeds');
      seeds(9, '1 seed');
      cy.get('[data-test="confirm"]').should('not.be.disabled').click();
      arrange(bob, TWO_PER_PIT);
      cy.then(() => bob.publish('/app/mangola/confirmLayout', {}));
      cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
      seeds(A2, '3 seeds');
      seeds(9, '1 seed');
      canSow(A2);
    });
  });

  it('restores the draft, then the confirmation, after a reload during placement', () => {
    asHostInPlacement().then(({ bob }) => {
      // A reload is only safe once the server has the draft: the guest is told of the change by a newer version.
      let version = 0;
      cy.then(() => { version = bob.lastState().version; });
      cy.get('[data-test="preset-checkerboard"]').click();
      cy.get('[data-test="seeds-left"]').should('contain.text', 'All seeds placed');
      cy.wrap(null).should(() => expect(bob.lastState().version, 'the server took the draft').to.be.greaterThan(version));
      let draft = '';
      cy.get('[data-test="my-half"]').then(($half) => { draft = myHalfSignature($half); });
      cy.reload();
      cy.get('[data-test="placement-editor"]', { timeout: 15000 }).should('be.visible');
      cy.get('[data-test="seeds-left"]').should('contain.text', 'All seeds placed');
      cy.get('[data-test="my-half"]').should(($half) => expect(myHalfSignature($half)).to.eq(draft));
      cy.get('[data-test="confirm"]').should('not.be.disabled').click();
      cy.get('[data-test="edit-layout"]').should('be.visible');
      cy.wrap(null).should(() => {
        expect(bob.lastState().players.find((p: any) => p.seat === 0).ready, 'the server took the confirmation').to.eq(true);
      });
      cy.reload();
      cy.get('[data-test="placement-editor"]', { timeout: 15000 }).should('be.visible');
      cy.get('[data-test="edit-layout"]').should('be.visible');
      cy.get('[data-test="my-half"]').should(($half) => expect(myHalfSignature($half)).to.eq(draft));
      cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'false');
      // Still the same game: the guest confirms and the board appears.
      arrange(bob, TWO_PER_PIT);
      cy.then(() => bob.publish('/app/mangola/confirmLayout', {}));
      cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
    });
  });

  it('puts no cap on a pit: all 32 seeds can sit in one', () => {
    asHostInPlacement();
    tapDraft([[A2, 32]]);
    seeds(A2, '32 seeds');
    cy.get('[data-test="seeds-left"]').should('contain.text', 'All seeds placed');
    cy.get('[data-test="confirm"]').should('not.be.disabled');
    // A 33rd tap is refused: no seed is left to place.
    pitEl(A2).click();
    seeds(A2, '32 seeds');
  });

  it('warns about a layout whose every move is endless, and refuses the pair that leaves the starter without a move', () => {
    asHostInPlacement().then(({ bob }) => {
      // The guest is ready first, with everything in h4.
      arrange(bob, ALL_IN_H4);
      cy.then(() => bob.publish('/app/mangola/confirmLayout', {}));
      cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'true');
      tapDraft(ENDLESS_TAPS);
      cy.get('[data-test="seeds-left"]').should('contain.text', 'All seeds placed');
      seeds(A2, '24 seeds');
      cy.get('[data-test="endless-warning"]').should('be.visible').and('contain.text', 'sow forever');
      // The confirmation that completes the pair is refused: the host starts and would have no move.
      cy.get('[data-test="confirm"]').should('not.be.disabled').click();
      cy.get('[data-test="placement-error"]').should('be.visible').and('contain.text', 'would have no move');
      cy.get('[data-test="mangola-board"]').should('not.exist');
      cy.get('[data-test="placement-editor"]').should('be.visible');
      cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'true');
      // Changing the layout clears the warning and the refusal; the new pair starts.
      cy.get('[data-test="preset-two-per-pit"]').click();
      cy.get('[data-test="endless-warning"]').should('not.exist');
      cy.get('[data-test="placement-error"]').should('not.exist');
      cy.get('[data-test="confirm"]').should('not.be.disabled').click();
      cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-test="placement-editor"]').should('not.exist');
      canSow(A2);
    });
  });
});
