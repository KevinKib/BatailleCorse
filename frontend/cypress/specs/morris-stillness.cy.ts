// The Morris table holds still, like the President table (same owner rule): a text that comes and goes (the draw warning
// "N moves without a capture before a draw", the last-pieces warning, the repetition warning, the draw offer, the mill
// banner, the phase hint) never moves or resizes the board, the player tags, the draw button or the Back row, on a phone and
// on a wide screen. The probe (support/morrisStillness.ts) records those boxes before and after each change of state.
// The browser is White (the host, through the UI) in Six Men's Morris against a scripted STOMP Black. The warnings are
// states the server only sends late in a game (40 quiet moves, a side down to three pieces): they are pushed into the
// store as a `state-update`, exactly what the page receives from the server.
import { joinAsScriptedPlayer, type ScriptedPlayer } from '../support/stompPlayer';
import { checkFits, expectNothingMoved, resetMoves, stillAcross } from '../support/morrisStillness';

type Scripted = ScriptedPlayer & { token: string; seat: number };

const place = (p: Scripted, point: number) => cy.then(() => p.publish('/app/three-men-morris/place', { point }));
const owner = (n: number, who: 'white' | 'black' | 'none') => cy.get(`[data-test="point-${n}"]`).should('have.attr', 'data-owner', who);
const myTurn = () => cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
const click = (n: number) => cy.get(`[data-test="point-${n}"]`).click();
// 390x844 is a tall phone: the old layout gave a calm table a bigger board there, and took it back when a text came in.
const SIZES: Array<[number, number]> = [[375, 667], [1280, 720], [390, 844]];

beforeEach(resetMoves);

/** The browser as White (host, through the UI) against a scripted Black, Six Men's Morris, the game started. */
function asWhiteInSix(): Cypress.Chainable<Scripted> {
  cy.visit('/three-men-morris');
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/three-men-morris\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  cy.get('[data-test="variant-card-six"]').click();
  cy.get('[data-test="option-variant-six"]').should('be.checked');
  cy.get('[data-test="colour-white"]').click();
  cy.get('[data-test="option-hostColour-white"]').should('be.checked');
  return cy.location('pathname').then((path) => {
    const id = path.split('/room/')[1];
    return joinAsScriptedPlayer(id, 'Bob', 'three-men-morris').then((black) => {
      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible').and('have.attr', 'data-variant', 'six');
      myTurn();
      return cy.wrap(black as Scripted);
    });
  });
}

/** Pushes the state the page holds, changed by `change`, as a server `state-update` would. */
function pushState(change: (state: any) => void) {
  cy.window().then((win) => {
    const pinia = (win.document.querySelector('#app') as any).__vue_app__.config.globalProperties.$pinia;
    const store = pinia._s.get('three-men-morris-store');
    const state = JSON.parse(JSON.stringify(store.game));
    change(state);
    store.applyEvent({ type: 'state-update', state });
  });
}

// The server sends the count only from 40 quiet moves on (and the limit with it): none before.
const quiet = (plies: number | null) => pushState((s) => {
  if (plies === null) { delete s.quietPlies; delete s.quietPlyLimit; } else { s.quietPlies = plies; s.quietPlyLimit = 50; }
});
const repetitions = (n: number) => pushState((s) => { s.repetitions = n; });
/** Takes the listed points off the board (a side down to three pieces). */
const takeOff = (points: number[]) => pushState((s) => { points.forEach((p) => { s.board[p] = null; }); });
/** A pending removal by White (me) or Black, on top of the movement phase. */
const removal = (seat: number | null) => pushState((s) => {
  if (seat === null) delete s.removal; else s.removal = { seat, mill: [1, 3, 5] };
});
const drawOffer = (seat: number | null) => pushState((s) => { s.drawOffer = seat === null ? null : { seat }; });

const take = (p: Scripted, point: number) => cy.then(() => p.publish('/app/three-men-morris/remove', { point }));

SIZES.forEach(([width, height]) => {
  it(`holds still through the mill banners at ${width}x${height}`, () => {
    cy.viewport(width, height);
    asWhiteInSix().then((black) => {
      // White closes the outer top side (0 1 2): the banner (two lines) takes the place of the phase hint.
      click(0); owner(0, 'white'); place(black, 12); owner(12, 'black'); myTurn();
      click(1); owner(1, 'white'); place(black, 13); owner(13, 'black'); myTurn();
      stillAcross('my mill banner', () => { click(2); },
        () => { cy.get('[data-test="removal-banner"]').should('be.visible'); });
      stillAcross('the banner leaves after my capture', () => { click(12); },
        () => { cy.get('[data-test="removal-banner"]').should('not.exist'); owner(12, 'none'); });
      // Black rebuilds 12 13 14 and takes the only white piece outside the mill.
      place(black, 12); owner(12, 'black'); myTurn();
      click(8); owner(8, 'white');
      stillAcross("Black's mill banner", () => { place(black, 14); },
        () => { cy.get('[data-test="removal-title"]').should('have.text', 'Bob closed a mill'); });
      stillAcross('the banner leaves after the capture', () => { take(black, 8); },
        () => { cy.get('[data-test="removal-banner"]').should('not.exist'); owner(8, 'none'); });
      checkFits('the end of the banners');
      expectNothingMoved();
    });
  });

  it(`holds still while the Six warnings and the draw offer come and go at ${width}x${height}`, () => {
    cy.viewport(width, height);
    asWhiteInSix().then((black) => {
      // Twelve pieces down, no mill for either side (White on the corners and 9, 13; Black on the middles and 11, 15).
      const white = [0, 2, 4, 6, 9, 13];
      const dark = [1, 3, 5, 7, 11, 15];
      white.forEach((w, i) => {
        click(w); owner(w, 'white');
        if (i < white.length - 1) { place(black, dark[i]); owner(dark[i], 'black'); myTurn(); }
      });
      // The movement phase begins: the draw button comes in.
      stillAcross('the movement phase begins', () => { place(black, dark[5]); },
        () => { myTurn(); cy.get('[data-test="phase-hint"]').should('have.text', 'Move a piece'); cy.get('[data-test="draw-offer"]').should('be.visible'); });

      stillAcross('the draw warning (40 moves without a capture)', () => { quiet(40); },
        () => { cy.get('[data-test="quiet-warning"]').should('contain.text', '10 moves without a capture'); });
      stillAcross('the draw warning at one move left', () => { quiet(49); },
        () => { cy.get('[data-test="quiet-warning"]').should('have.text', '1 move without a capture before a draw.'); });
      stillAcross('the repetition warning', () => { repetitions(2); },
        () => { cy.get('[data-test="repetition-hint"]').should('be.visible'); });
      stillAcross('a side down to three pieces', () => { takeOff([5, 7, 11]); },
        () => { cy.get('[data-test="last-pieces"]').should('have.length', 1); });
      stillAcross('the mill banner on top of the warnings (mine)', () => { removal(0); },
        () => { cy.get('[data-test="removal-banner"]').should('be.visible'); });
      checkFits('my banner and the warnings');
      stillAcross('the mill banner on top of the warnings (theirs)', () => { removal(1); },
        () => { cy.get('[data-test="removal-sub"]').should('be.visible'); });
      checkFits('the banner of Black and the warnings');
      stillAcross('the banner leaves', () => { removal(null); },
        () => { cy.get('[data-test="removal-banner"]').should('not.exist'); });
      stillAcross('both sides down to three pieces', () => { takeOff([0, 2, 4]); },
        () => { cy.get('[data-test="last-pieces"]').should('have.length', 2); });
      checkFits('all the warnings');
      stillAcross('an incoming draw offer', () => { drawOffer(1); },
        () => { cy.get('[data-test="draw-incoming"]').should('be.visible'); });
      checkFits('the incoming offer');
      stillAcross('the offer leaves', () => { drawOffer(null); },
        () => { cy.get('[data-test="draw-incoming"]').should('not.exist'); });
      stillAcross('the warnings leave', () => { quiet(null); repetitions(0); },
        () => { cy.get('[data-test="quiet-warning"]').should('not.exist'); cy.get('[data-test="repetition-hint"]').should('not.exist'); });
      checkFits('the end');
      expectNothingMoved();
    });
  });
});
