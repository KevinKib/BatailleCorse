// Component-test pilot of the Morris stillness check (the e2e spec specs/morris-stillness.cy.ts is the reference). The real
// Six Men's Morris screen is mounted inside the real App with a store fed by hand: no backend, no docker. Warnings and
// banners come and go as `state-update`s, exactly what the page receives from the server, and the board, the player tags,
// the draw button and the Back row must not move (same probe as the e2e spec: support/morrisStillness.ts).
// checkFits (screen containment, table overflow) is left out on purpose: mounted outside the app shell the table overflows by 5px even in a calm state, see the PR.
import { mountApp } from '../support/component';
import { useThreeMenMorrisStore } from '../../src/state/ThreeMenMorris.store';
import { morrisState } from '../../src/model/threemenmorris/morrisFixtures';
import { expectNothingMoved, resetMoves, stillAcross } from '../support/morrisStillness';

const SIZES: Array<[number, number]> = [[375, 667], [1280, 720]];

// Six Men's Morris, movement phase, White (me) to move: 16 points, six pieces each, none in a mill.
const sixMovement = () => {
  const board: (number | null)[] = Array(16).fill(null);
  [0, 2, 4, 6, 9, 13].forEach((p) => { board[p] = 0; });
  [1, 3, 5, 7, 11, 15].forEach((p) => { board[p] = 1; });
  return morrisState({
    variant: 'six', phase: 'MOVEMENT', board, availableActions: ['MOVE'],
    players: [
      { seat: 0, name: 'Alice', current: true, piecesToPlace: 0 },
      { seat: 1, name: 'Bob', current: false, piecesToPlace: 0 },
    ],
  } as any);
};

const store = () => useThreeMenMorrisStore();
/** Pushes the state the page holds, changed by `change`, as a server `state-update` would. */
const pushState = (change: (state: any) => void) => cy.then(() => {
  const s = store();
  const state = JSON.parse(JSON.stringify(s.game));
  change(state);
  s.applyEvent({ type: 'state-update', state } as any);
});

beforeEach(resetMoves);

SIZES.forEach(([width, height]) => {
  it(`holds still while the Six warnings, the banner and the draw offer come and go at ${width}x${height}`, () => {
    cy.viewport(width, height);
    mountApp('/three-men-morris/room/g1', () => {
      localStorage.setItem('three-men-morris:tokens:g1', JSON.stringify({ 0: 'tok' }));
      store().applyEvent({ type: 'seat-change', seat: 0 } as any);
      store().applyEvent({ type: 'state-update', state: sixMovement() } as any);
    });
    cy.get('[data-test="morris-board"]').should('be.visible').and('have.attr', 'data-variant', 'six');
    cy.get('[data-test="draw-offer"]').should('be.visible');

    stillAcross('the draw warning', () => { pushState((s) => { s.quietPlies = 40; s.quietPlyLimit = 50; }); },
      () => { cy.get('[data-test="quiet-warning"]').should('contain.text', '10 moves without a capture'); });
    stillAcross('the repetition warning', () => { pushState((s) => { s.repetitions = 2; }); },
      () => { cy.get('[data-test="repetition-hint"]').should('be.visible'); });
    stillAcross('my mill banner', () => { pushState((s) => { s.removal = { seat: 0, mill: [1, 3, 5] }; }); },
      () => { cy.get('[data-test="removal-banner"]').should('be.visible'); });
    stillAcross('the banner leaves', () => { pushState((s) => { delete s.removal; }); },
      () => { cy.get('[data-test="removal-banner"]').should('not.exist'); });
    stillAcross('an incoming draw offer', () => { pushState((s) => { s.drawOffer = { seat: 1 }; }); },
      () => { cy.get('[data-test="draw-incoming"]').should('be.visible'); });
    stillAcross('the warnings and the offer leave', () => { pushState((s) => { delete s.quietPlies; delete s.quietPlyLimit; s.repetitions = 0; s.drawOffer = null; }); },
      () => { cy.get('[data-test="quiet-warning"]').should('not.exist'); cy.get('[data-test="draw-incoming"]').should('not.exist'); });
    expectNothingMoved();
  });
});
