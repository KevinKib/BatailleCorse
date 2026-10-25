// Mangola sowing direction, the nominal cases end to end: the lobby option (host picks, guest reads), then the board
// of each player. The browser is the host (seat 0, side A); a scripted STOMP client is the guest (seat 1, side B).
// To see the guest's screen the browser takes over the guest's seat for a moment: the room keeps one token per seat
// in localStorage, so putting the guest's token there and reloading shows the page as the guest sees it.
//
// Every player sees their own half at the bottom. Anticlockwise: the host reads their inner row (a2..h2) from right
// to left, the guest theirs (a3..h3) from left to right on their own screen (a half turn apart); clockwise mirrors it.
import { joinAsScriptedPlayer } from '../support/stompPlayer';
import { arrange, confirmLayout, type Scripted } from '../support/mangolaPlayer';

type Direction = 'anticlockwise' | 'clockwise';

const pit = (name: string) => cy.get(`[data-test="pit-${name}"]`);

/** `left` is drawn to the left of `right` (same row). */
function expectLeftOf(left: string, right: string) {
  pit(left).then(($l) => pit(right).then(($r) => {
    expect($l[0].getBoundingClientRect().right, `${left} is left of ${right}`).to.be.at.most($r[0].getBoundingClientRect().left + 1);
  }));
}

/** `lower` is drawn below `upper`. */
function expectBelow(lower: string, upper: string) {
  pit(lower).then(($lo) => pit(upper).then(($up) => {
    expect($lo[0].getBoundingClientRect().top, `${lower} is below ${upper}`).to.be.greaterThan($up[0].getBoundingClientRect().top);
  }));
}

/** What the host sees at the table: own half at the bottom, the inner row running the way the game says. */
function expectHostTable(direction: Direction) {
  expectBelow('a2', 'a4');
  if (direction === 'anticlockwise') {
    expectLeftOf('h2', 'a2');
    pit('a2').find('[data-test="chevron"]').should('have.attr', 'data-arrow', 'left');
  } else {
    expectLeftOf('a2', 'h2');
    pit('a2').find('[data-test="chevron"]').should('have.attr', 'data-arrow', 'right');
  }
}

/** What the guest sees: own half at the bottom too, their columns a half turn from the host's. */
function expectGuestTable(direction: Direction) {
  expectBelow('a3', 'a1');
  if (direction === 'anticlockwise') {
    expectLeftOf('a3', 'h3');
    pit('b3').find('[data-test="chevron"]').should('have.attr', 'data-arrow', 'left');
  } else {
    expectLeftOf('h3', 'a3');
    pit('b3').find('[data-test="chevron"]').should('have.attr', 'data-arrow', 'right');
  }
}

/** The browser as the host in a fresh lobby, with the scripted guest seated. */
function hostLobbyWithGuest() {
  cy.visit('/mangola');
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/mangola\/room\/.+/);
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  return cy.location('pathname').then((path) => {
    const id = path.split('/room/')[1];
    return joinAsScriptedPlayer(id, 'Bob', 'mangola').then((bob) => {
      cy.get('[data-test="player-count"]').should('contain.text', '2 / 2');
      return cy.window().then((win) => {
        const key = `mangola:tokens:${id}`;
        return { bob, hostTokens: win.localStorage.getItem(key) as string, key };
      });
    });
  });
}

/** Reloads the page as the holder of `tokens` (the host's or the guest's seat). */
function reloadAs(key: string, tokens: string) {
  cy.window().then((win) => win.localStorage.setItem(key, tokens));
  cy.reload();
}

const guestTokens = (bob: Scripted) => JSON.stringify({ [bob.seat]: bob.token });

/** Start from the host's lobby, lay out two seeds per pit on both sides and reach the board. */
function startToBoard(bob: Scripted) {
  cy.get('[data-test="start"]').should('not.be.disabled').click();
  cy.get('[data-test="placement-editor"]', { timeout: 10000 }).should('be.visible');
  cy.get('[data-test="preset-two-per-pit"]').click();
  cy.get('[data-test="seeds-left"]').should('contain.text', 'All seeds placed');
  arrange(bob, Array<number>(16).fill(2));
  confirmLayout(bob);
  cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'true');
  cy.get('[data-test="confirm"]').should('not.be.disabled').click();
  cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
}

describe('Mangola sowing direction', () => {
  it('is anticlockwise by default: both players see the same direction, each with their own half at the bottom', () => {
    hostLobbyWithGuest().then(({ bob, hostTokens, key }) => {
      // Hidden behind the closed Customise until the host looks for it.
      cy.get('[data-test="option-direction"]').should('not.exist');
      cy.get('[data-test="customise"]').should('have.attr', 'aria-expanded', 'false').click();
      cy.get('[data-test="option-direction"] .choice--on').should('contain.text', 'Anticlockwise');
      startToBoard(bob);
      expectHostTable('anticlockwise');
      reloadAs(key, guestTokens(bob));
      cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
      expectGuestTable('anticlockwise');
      // The host's token goes back, so the room is left as it was found.
      reloadAs(key, hostTokens);
    });
  });

  it('is picked in the lobby by the host, read as plain text by the guest, and drawn clockwise on both tables', () => {
    hostLobbyWithGuest().then(({ bob, hostTokens, key }) => {
      cy.get('[data-test="customise"]').click();
      cy.get('[data-test="option-direction-clockwise"]').check({ force: true });
      cy.get('[data-test="option-direction"] .choice--on').should('contain.text', 'Clockwise');

      // The guest's lobby: Customise opens by itself on a non-default value, the choice as text, nothing to change, no Start.
      reloadAs(key, guestTokens(bob));
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
      cy.get('[data-test="option-direction"]').should('contain.text', 'Sowing direction').and('contain.text', 'Clockwise');
      cy.get('[data-test="option-direction"] input').should('not.exist');
      cy.get('[data-test="start"]').should('not.exist');

      // Back to the host: the choice is kept, the game starts and the board is clockwise.
      reloadAs(key, hostTokens);
      cy.get('[data-test="option-direction"] .choice--on', { timeout: 10000 }).should('contain.text', 'Clockwise');
      startToBoard(bob);
      expectHostTable('clockwise');
      reloadAs(key, guestTokens(bob));
      cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
      expectGuestTable('clockwise');
      reloadAs(key, hostTokens);
    });
  });
});
