// Mangola, played end to end. The browser is the host (seat 0, side A, who starts), a scripted STOMP client the
// guest; every wait is on a DOM state or on a frame, never on a delay, and the sowing animation is skipped by a click
// or waited for through the pits becoming playable again. Pits are numbered row * 8 + column (a1 = 0 ... h4 = 31).
//
// Both players lay out two seeds per pit. From there (hand-checked against the rules, with a simulator) the host
// wins in five plies: a2 (three captures), a4, f1, a3, b2; and loses in six when it plays a2, h2, c1 against the
// guest's a4, d4 (six pits captured), h3.
import {
  arrange, asHostInPlacement, canSow, confirmLayout, pitEl, playFromTwoPerPit, sow, startWithAnimationSpeed, TWO_PER_PIT,
} from '../support/mangolaPlayer';

// pits: a2 8, b2 9, h2 15, c1 2, f1 5 (host); a3 16, h3 23, a4 24, d4 27 (guest).
const A2 = 8; const B2 = 9; const H2 = 15; const C1 = 2; const F1 = 5;
const A3 = 16; const H3 = 23; const A4 = 24; const D4 = 27;

const live = () => cy.get('[data-test="board-live"]');
const seeds = (pit: number, text: string) => pitEl(pit).should('have.attr', 'aria-label').and('contain', text);

describe('Mangola placement', () => {
  it('adds seeds by tapping, removes them with the Remove toggle, fills with a preset and confirms', () => {
    asHostInPlacement().then(({ bob }) => {
      cy.get('[data-test="seeds-left"]').should('contain.text', '32 seeds left');
      cy.get('[data-test="confirm"]').should('be.disabled');
      cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'false');
      cy.get('[data-test="opponent-status"]').should('contain.text', 'Placing');
      pitEl(A2).click();
      cy.get('[data-test="seeds-left"]').should('contain.text', '31 seeds left');
      seeds(A2, '1 seed');
      cy.get('[data-test="mode-remove"]').click();
      pitEl(A2).click();
      cy.get('[data-test="seeds-left"]').should('contain.text', '32 seeds left');
      cy.get('[data-test="mode-add"]').click();
      cy.get('[data-test="preset-checkerboard"]').click();
      cy.get('[data-test="seeds-left"]').should('contain.text', 'All seeds placed');
      cy.get('[data-test="confirm"]').should('not.be.disabled');
      cy.get('[data-test="preset-clear"]').click();
      cy.get('[data-test="seeds-left"]').should('contain.text', '32 seeds left');
      cy.get('[data-test="preset-two-per-pit"]').click();
      cy.get('[data-test="confirm"]').should('not.be.disabled').click();
      // Confirmed: the layout can still be taken back until the opponent is ready.
      cy.get('[data-test="edit-layout"]').should('be.visible').click();
      cy.get('[data-test="confirm"]').should('not.be.disabled');
      // The opponent's half stays blank: nothing of its layout reaches the browser before the reveal.
      arrange(bob, TWO_PER_PIT);
      confirmLayout(bob);
      cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'true');
      cy.get('[data-test="opponent-status"]').should('contain.text', 'Ready');
      cy.get('[data-test="ghost-pit"]').should('have.length', 16);
      cy.get('[data-test="confirm"]').click();
      cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
    });
  });
});

describe('Mangola game', () => {
  // These games watch the sowing through Skip and the marks, at the rhythm they were written for (Fast); the
  // default Normal speed and its pause before the seeds leave are covered by mangola-animation-speed.cy.ts.
  beforeEach(() => startWithAnimationSpeed('fast'));

  it('wins in five plies: a capture on the board, the move read out, the end screen, then Play again', () => {
    playFromTwoPerPit().then(({ bob }) => {
      cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
      pitEl(A2).click();
      // The hand travels; a click on Skip jumps to the final board.
      cy.get('[data-test="skip-sowing"]', { timeout: 10000 }).click();
      cy.get('[data-test="sowing-hand"]').should('not.exist');
      // The capture is on the board: the pits taken are empty.
      [18, 26, 20, 28, 17, 25].forEach((pit) => seeds(pit, '0 seeds'));
      live().should('contain.text', 'Alice sowed from a2').and('contain.text', 'seeds captured');
      cy.get('[data-test="turn-hint"]').should('not.exist');

      sow(bob, A4);
      canSow(F1);
      live().should('contain.text', 'Bob sowed from a4');
      pitEl(A4).should('have.attr', 'data-last-start', 'true');
      pitEl(F1).click();
      sow(bob, A3);
      canSow(B2);
      pitEl(B2).click();
      cy.get('[data-test="end"]', { timeout: 20000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'VICTORY');
      cy.get('[data-test="end-winner"]').should('have.text', 'Winner: Alice');
      cy.get('[data-cy="play-again"]').click();
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
    });
  });

  it('loses in six plies: the guest captures six pits, shown on the board, and the end screen names the winner', () => {
    playFromTwoPerPit().then(({ bob }) => {
      pitEl(A2).click();
      cy.get('[data-test="skip-sowing"]', { timeout: 10000 }).click();
      sow(bob, A4);
      canSow(H2);
      pitEl(H2).click();
      sow(bob, D4);
      // The guest's move is replayed, then its marks stay: the pit it started from and the pits it emptied.
      canSow(C1);
      live().should('contain.text', 'Bob sowed from d4').and('contain.text', 'captured');
      pitEl(D4).should('have.attr', 'data-last-start', 'true');
      [14, 6, 8, 0, 11, 3].forEach((pit) => {
        pitEl(pit).should('have.attr', 'data-captured', 'true').find('[data-test="captured-seeds"]').should('exist');
      });
      pitEl(C1).click();
      // My own move replaces the marks of the opponent's.
      pitEl(D4).should('not.have.attr', 'data-last-start');
      sow(bob, H3);
      cy.get('[data-test="end"]', { timeout: 20000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'DEFEAT');
      cy.get('[data-test="end-winner"]').should('have.text', 'Winner: Bob');
    });
  });

  it('survives a reload in the middle of the game: the board, the turn and the seat come back', () => {
    playFromTwoPerPit().then(({ bob }) => {
      pitEl(A2).click();
      cy.get('[data-test="skip-sowing"]', { timeout: 10000 }).click();
      sow(bob, A4);
      canSow(F1);
      cy.reload();
      cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
      // b4 and d4 got a seed from the guest's a4; the others are still empty.
      [17, 18, 20, 26, 28].forEach((pit) => seeds(pit, '0 seeds'));
      canSow(F1);
      cy.get('[data-test="player-tag"]').should('contain.text', 'Alice');
    });
  });

  it('the opponent resigning ends my game: victory, and the subtitle says who resigned', () => {
    playFromTwoPerPit().then(({ bob }) => {
      cy.then(() => bob.publish('/app/forfeit', {}));
      cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
      cy.get('.end-title').should('have.text', 'VICTORY');
      cy.get('.end-sub').invoke('text').should('match', /Bob resigned/);
    });
  });

  it('Back during the game asks, resigns, and tells the opponent', () => {
    playFromTwoPerPit().then(({ bob }) => {
      cy.on('window:confirm', (text) => { expect(text).to.contain('forfeit'); return true; });
      cy.get('[data-test="leave"]').click();
      cy.location('pathname').should('eq', '/');
      cy.wrap(null).should(() => {
        const frame = bob.frames.find((f) => f.eventType === 'FORFEIT');
        expect(frame, 'Bob got the forfeit').to.exist;
        expect((frame as any).eventData.reason).to.eq('RESIGNED');
      });
    });
  });

  it('is operable with the keyboard alone: arrows move between pits, Enter sows', () => {
    playFromTwoPerPit().then(() => {
      cy.get('[data-test="mangola-board"] button[tabindex="0"]').should('have.length', 1);
      pitEl(A2).focus();
      // The default direction is anticlockwise: b2 is to the left of a2 on the host's screen.
      cy.focused().type('{leftarrow}');
      pitEl(B2).should('have.focus');
      cy.focused().type('{rightarrow}');
      pitEl(A2).should('have.focus').type('{enter}');
      cy.get('[data-test="skip-sowing"]', { timeout: 10000 }).should('exist');
    });
  });
});

describe('Mangola layout', () => {
  [[375, 667], [1280, 800]].forEach(([width, height]) => {
    it(`lays the board out at ${width}x${height}: no horizontal scroll, pits of 44 px, Skip clear of the legend`, () => {
      cy.viewport(width, height);
      playFromTwoPerPit().then(() => {
        pitEl(A2).click();
        cy.get('[data-test="skip-sowing"]', { timeout: 10000 }).should('be.visible').then(($skip) => {
          cy.document().then((doc) => {
            const el = doc.scrollingElement as Element;
            expect(el.scrollWidth, 'no horizontal scroll').to.be.at.most(el.clientWidth + 1);
            const skip = $skip[0].getBoundingClientRect();
            expect(skip.left, 'Skip left').to.be.at.least(0);
            expect(skip.right, 'Skip right').to.be.at.most(width + 1);
            expect(skip.bottom, 'Skip bottom').to.be.at.most(height + 1);
            expect(skip.height, 'Skip height').to.be.at.least(44);
            // The legend's text (not its full-width box) must stay clear of the button.
            const range = doc.createRange();
            range.selectNodeContents(doc.querySelector('.mg-board__legend--bottom')!);
            const legend = range.getBoundingClientRect();
            const overlap = skip.left < legend.right && legend.left < skip.right && skip.top < legend.bottom && legend.top < skip.bottom;
            expect(overlap, 'Skip does not cover the legend').to.eq(false);
            const grid = doc.querySelector('.mg-board__grid')!.getBoundingClientRect();
            expect(skip.top, 'Skip sits under the grid, not over its last row').to.be.at.least(grid.bottom - 1);
          });
        });
        cy.get('[data-test="mangola-board"]').then(($board) => {
          const board = $board[0].getBoundingClientRect();
          expect(board.left, 'board left').to.be.at.least(0);
          expect(board.right, 'board right').to.be.at.most(width + 1);
        });
        cy.get('[data-test="mangola-board"] button.mg-pit').should('have.length', 32).each(($pit) => {
          const r = $pit[0].getBoundingClientRect();
          expect(r.width, 'pit width').to.be.at.least(44);
          expect(r.height, 'pit height').to.be.at.least(44);
        });
        cy.screenshot(`mangola-board-${width}`, { capture: 'viewport' });
        cy.get('[data-cy="rules-toggle"]').click();
        cy.get('[data-cy="rules-panel"]').should('be.visible').and('contain.text', 'Mangola');
      });
    });

    it(`lays the placement screen out at ${width}x${height}: no horizontal scroll, Confirm reachable`, () => {
      cy.viewport(width, height);
      asHostInPlacement();
      cy.get('[data-test="preset-two-per-pit"]').click();
      cy.get('[data-test="confirm"]').scrollIntoView().should('be.visible').and('not.be.disabled');
      cy.document().then((doc) => {
        const el = doc.scrollingElement as Element;
        expect(el.scrollWidth, 'no horizontal scroll').to.be.at.most(el.clientWidth + 1);
      });
      cy.get('[data-test="my-half"] button').each(($pit) => {
        const r = $pit[0].getBoundingClientRect();
        expect(r.width, 'pit width').to.be.at.least(44);
        expect(r.height, 'pit height').to.be.at.least(44);
      });
      cy.screenshot(`mangola-placement-${width}`, { capture: 'viewport' });
    });
  });
});
