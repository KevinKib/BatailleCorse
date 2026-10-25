// Mangola animation speed, end to end: the speed button under the player plate (cycle, kept across a reload, operable by
// keyboard, 44 px and clear of the plate and of "Your side") and the opponent's move shown before the seeds leave. The
// browser is the host (seat 0) against a scripted guest. Pits: a2 = 8 ... a4 = 24, see support/mangolaPlayer.ts.
import {
  ANIMATION_SPEED_KEY, canSow, pitEl, playFromTwoPerPit, sow, startWithAnimationSpeed,
} from '../support/mangolaPlayer';
import { atSpacingViewports, expectGap, expectNoOverlap } from '../support/spacing';

const A2 = 8; const A4 = 24; const F1 = 5;

const speedButton = () => cy.get('[data-test="speed-button"]');
const speedIs = (name: string) => speedButton().should('have.attr', 'aria-label', `Animation speed: ${name}`);

describe('Mangola animation speed', () => {
  it('the button cycles Normal, Fast, Instant, Normal, says each speed, and the choice survives a reload', () => {
    playFromTwoPerPit();
    speedIs('Normal');
    speedButton().click();
    speedIs('Fast');
    cy.get('[data-test="speed-live"]').should('contain.text', 'Animation speed: Fast');
    speedButton().click();
    speedIs('Instant');
    cy.window().then((win) => expect(win.localStorage.getItem(ANIMATION_SPEED_KEY)).to.eq('instant'));
    cy.reload();
    cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
    speedIs('Instant');
    speedButton().click();
    speedIs('Normal');
  });

  it('is operable with the keyboard alone and shows a focus ring', () => {
    playFromTwoPerPit();
    speedButton().focus().should('have.focus').type('{enter}');
    speedIs('Fast');
    speedButton().type(' ');
    speedIs('Instant');
    speedButton().then(($b) => {
      const outline = getComputedStyle($b[0]).outlineStyle;
      expect(outline, 'a visible focus outline').to.not.eq('none');
    });
  });

  it('a tap on the button while the guest seeds travel applies at once: Instant lands on the final board', () => {
    playFromTwoPerPit().then(({ bob }) => {
      pitEl(A2).click();
      cy.get('[data-test="skip-sowing"]', { timeout: 10000 }).click();
      sow(bob, A4);
      cy.get('[data-test="sowing-hand"]', { timeout: 10000 }).should('exist');
      speedButton().click(); // Normal -> Fast
      speedButton().click(); // Fast -> Instant
      speedIs('Instant');
      cy.get('[data-test="sowing-hand"]').should('not.exist');
      pitEl(A4).should('have.attr', 'data-last-start', 'true');
      canSow(F1);
    });
  });

  it('Normal: the pit the guest chose is ringed, the hand rests on it, and only then do the seeds leave', () => {
    playFromTwoPerPit().then(({ bob }) => {
      pitEl(A2).click();
      cy.get('[data-test="skip-sowing"]', { timeout: 10000 }).click();
      sow(bob, A4);
      // First the decision: the chosen pit is ringed, still full, and the hand rests on it.
      pitEl(A4).should('have.attr', 'data-chosen', 'true').and('have.attr', 'aria-label').and('contain', 'just chosen');
      cy.get('[data-test="sowing-hand"]').should('have.attr', 'data-pit', String(A4));
      pitEl(A4).should('have.attr', 'aria-label').and('not.contain', '0 seeds');
      // Then the seeds leave: the ring goes, the pit is picked up empty.
      pitEl(A4).should('not.have.attr', 'data-chosen');
      pitEl(A4).should('have.attr', 'aria-label').and('contain', '0 seeds');
      // The marks of the move only come once it is over.
      cy.get('[data-test^="pit-"][data-last-start="true"]').should('not.exist');
      canSow(F1);
      pitEl(A4).should('have.attr', 'data-last-start', 'true');
    });
  });

  it('Instant: the guest move lands at once, with no hand and no ring', () => {
    startWithAnimationSpeed('instant');
    playFromTwoPerPit().then(({ bob }) => {
      speedIs('Instant');
      pitEl(A2).click();
      cy.get('[data-test="sowing-hand"]').should('not.exist');
      sow(bob, A4);
      pitEl(A4).should('have.attr', 'data-last-start', 'true');
      cy.get('[data-test="sowing-hand"]').should('not.exist');
      cy.get('[data-chosen="true"]').should('not.exist');
      canSow(F1);
    });
  });

  it('defaults to Instant when the device asks for reduced motion and the player never chose', () => {
    cy.on('window:before:load', (win) => {
      const real = win.matchMedia.bind(win);
      win.matchMedia = (query: string) => (query.includes('prefers-reduced-motion')
        ? ({ ...real(query), matches: true, media: query } as MediaQueryList)
        : real(query));
    });
    playFromTwoPerPit();
    speedIs('Instant');
  });

  it('puts the speed button under the player plate, 44 px and clear of "Your side" and of the plate', () => {
    playFromTwoPerPit();
    atSpacingViewports((width, height) => {
      pitEl(A2).should('be.visible');
      speedButton().should('be.visible').then(($speed) => {
        cy.document().then((doc) => {
          const el = doc.scrollingElement as Element;
          expect(el.scrollWidth, 'no horizontal scroll').to.be.at.most(el.clientWidth + 1);
          const speed = $speed[0].getBoundingClientRect();
          expect(speed.height, 'speed button height').to.be.at.least(44);
          expect(speed.width, 'speed button width').to.be.at.least(44);
          const tags = doc.querySelectorAll('[data-test="player-tag"]');
          const mine = tags[tags.length - 1].getBoundingClientRect();
          expect(speed.top - mine.bottom + 0.5, 'speed button below my plate').to.be.at.least(8);
          const grid = doc.querySelector('.mg-board__grid')!.getBoundingClientRect();
          expect(speed.top, 'speed button under the grid').to.be.at.least(grid.bottom);
        });
      });
      expectGap('.mg-board__legend--bottom', '[data-test="speed-button"]', 8);
      expectNoOverlap('.mg-board__legend--bottom', '[data-test="speed-button"]');
      cy.screenshot(`mangola-speed-${width}x${height}`, { capture: 'viewport' });
    });
  });
});
