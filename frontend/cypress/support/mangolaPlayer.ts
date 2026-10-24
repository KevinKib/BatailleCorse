// Mangola commands for a scripted player (see stompPlayer.ts) and the two games the specs replay.
import { joinAsScriptedPlayer, type ScriptedPlayer } from './stompPlayer';

export type Scripted = ScriptedPlayer & { token: string; seat: number };

/** Placement: my whole layout, 16 counts in the order of my loop. */
export const arrange = (p: Scripted, counts: number[]) => cy.then(() => p.publish('/app/mangola/arrange', { counts }));
export const confirmLayout = (p: Scripted) => cy.then(() => p.publish('/app/mangola/confirmLayout', {}));
export const sow = (p: Scripted, pit: number) => cy.then(() => p.publish('/app/mangola/sow', { pit }));

/** Two seeds in every pit: the opening whose first player wins in five plies (hand-checked against the rules). */
export const TWO_PER_PIT = Array<number>(16).fill(2);

/** The pit named `a1..h4` (column letter, row number), numbered as the server does: row * 8 + column. */
export const pitName = (pit: number) => `${String.fromCharCode(97 + (pit % 8))}${Math.floor(pit / 8) + 1}`;

export const pitEl = (pit: number) => cy.get(`[data-test="pit-${pitName(pit)}"]`);

/** My turn on the board: the pit I may sow is selectable (a pit is inert while a sowing is shown; a long move has no cap, 60 s covers any move of these specs). */
export const canSow = (pit: number) => cy.get(`[data-test="pit-${pitName(pit)}"]`, { timeout: 60000 }).should('have.attr', 'data-selectable', 'true');

/**
 * The browser as the host (seat 0, who starts: the option is set to Host) against a scripted guest, in the
 * placement phase of a free game. Yields the guest and the game id.
 */
export function asHostInPlacement(): Cypress.Chainable<{ id: string; bob: Scripted }> {
  cy.visit('/games/mangola');
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/mangola\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  cy.get('[data-test="option-firstPlayer-host"]').check({ force: true });
  cy.get('[data-test="option-firstPlayer"] .choice--on').should('contain.text', 'Host');
  return cy.location('pathname').then((path) => {
    const id = path.split('/room/')[1];
    return joinAsScriptedPlayer(id, 'Bob', 'mangola').then((bob) => {
      cy.get('[data-test="start"]').should('not.be.disabled').click();
      cy.get('[data-test="placement-editor"]', { timeout: 10000 }).should('be.visible');
      return cy.wrap({ id, bob });
    });
  });
}

/** Both players lay out two seeds per pit and confirm; the board appears with the host to move. */
export function playFromTwoPerPit(): Cypress.Chainable<{ id: string; bob: Scripted }> {
  return asHostInPlacement().then(({ id, bob }) => {
    cy.get('[data-test="preset-two-per-pit"]').click();
    cy.get('[data-test="seeds-left"]').should('contain.text', 'All seeds placed');
    arrange(bob, TWO_PER_PIT);
    confirmLayout(bob);
    cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'true');
    cy.get('[data-test="confirm"]').should('not.be.disabled').click();
    cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="placement-editor"]').should('not.exist');
    canSow(8);
    return cy.wrap({ id, bob });
  });
}

/** The browser host's own draft, tapped pit by pit: `[pit, seeds]` pairs, each tap adding one seed (placement editor). */
export function tapDraft(pits: Array<[number, number]>) {
  pits.forEach(([pit, times]) => {
    for (let i = 0; i < times; i += 1) pitEl(pit).click();
  });
}

/** The localStorage key the app keeps the animation speed under (`useAnimationSpeed`). */
export const ANIMATION_SPEED_KEY = 'animationSpeed';

/**
 * Sets the animation speed the app starts with, on each page load of the test (call it first thing in the test or in
 * a `beforeEach`). The specs that do not need to watch a sowing use `'instant'`; the others keep the default (Normal)
 * and watch it, or skip it with the Skip button.
 */
export const startWithAnimationSpeed = (speed: 'normal' | 'fast' | 'instant') => {
  cy.on('window:before:load', (win) => win.localStorage.setItem(ANIMATION_SPEED_KEY, speed));
};

/** The aria-labels of my 16 pits in the placement editor, read as one string to compare two moments. */
export const myHalfSignature = ($half: JQuery<HTMLElement>) =>
  [...$half.find('button')].map((el) => el.getAttribute('aria-label')).join('|');
