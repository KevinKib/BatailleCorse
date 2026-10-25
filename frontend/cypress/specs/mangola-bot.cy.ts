// Mangola against a bot, end to end. The browser is the host (seat 0, side A); the bot answers at once under the
// `test` profile (zero delay, constant generator). Every wait is on a DOM state, never on a delay. Pits are numbered
// row * 8 + column (a1 = 0 ... h4 = 31), see support/mangolaPlayer.ts.

import { startWithAnimationSpeed } from '../support/mangolaPlayer';

const SIZES: Array<[number, number]> = [[375, 667], [1280, 800]];
const MAX_MOVES = 150;

const level = () => cy.get('[data-test="seat-option-1-level"]');

/** The host's lobby with a bot on seat 1, the host to move first (the set-up is always free placement). */
function lobbyWithBot() {
  cy.visit('/mangola');
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  cy.get('[data-test="option-firstPlayer-host"]').check({ force: true });
  cy.get('[data-test="add-bot"]').click();
}

/** The board of the pits as a string (my pits first), to see it change between two moves. */
const boardSignature = ($body: JQuery<HTMLElement>) =>
  [...$body.find('[data-test^="pit-"]')].map((el) => el.getAttribute('aria-label')).join('|');

/**
 * Plays the first sowable pit each turn until the end screen shows. After each move it waits for the bot's answer (my
 * turn again), so a move is never sent while the previous one is still unanswered.
 */
function playUntilTheEnd(moves = MAX_MOVES) {
  cy.get('[data-test="end"], [data-test^="pit-"][data-selectable="true"]', { timeout: 30000 }).then(($found) => {
    if ($found.is('[data-test="end"]')) return;
    expect(moves, 'moves left before giving up').to.be.greaterThan(0);
    cy.get('body').then(($body) => {
      const before = boardSignature($body);
      cy.wrap($found.first()).click();
      // My move is on the board (or the game is over)...
      cy.get('body').should(($b) => {
        expect($b.find('[data-test="end"]').length > 0 || boardSignature($b) !== before, 'my move reached the board').to.eq(true);
      });
      // ...and the bot answered: it is my turn again (a pit is playable) or the game is over. Waiting on the
      // announcement would race the instant boards, and an older bot sentence stays in the region.
      cy.get('[data-test="end"], [data-test^="pit-"][data-selectable="true"]', { timeout: 30000 }).should('exist');
      playUntilTheEnd(moves - 1);
    });
  });
}

describe('Mangola against a bot', () => {
  // A whole game against the bot: nothing here needs to watch a sowing, so the boards come at once.
  beforeEach(() => startWithAnimationSpeed('instant'));

  it('offers Add bot, puts the bot on the seat at the Medium level, and lets the host change it', () => {
    lobbyWithBot();
    cy.get('[data-test="player-count"]').should('contain.text', '2 / 2');
    cy.get('[data-test="badge-bot"]').should('be.visible');
    cy.get('.players li').eq(1).should('contain.text', 'Bot 1');
    level().should('have.value', 'greedy').find('option').then(($o) => {
      expect([...$o].map((o) => o.textContent?.trim())).to.deep.eq(['Easy', 'Medium', 'Expert']);
    });
    level().should('have.attr', 'aria-label', 'Level for Bot 1');
    level().select('Expert');
    level().should('have.value', 'expert');
    cy.get('[data-test="add-bot"]').should('be.disabled'); // two seats only
    cy.get('[data-test="start"]').should('not.be.disabled');
    cy.get('[data-test="remove-bot-1"]').click();
    cy.get('[data-test="player-count"]').should('contain.text', '1 / 2');
    cy.get('[data-test="add-bot"]').should('not.be.disabled');
  });

  it('free placement: the bot lays out its seeds like a human, Placing then Ready', () => {
    lobbyWithBot();
    level().select('Expert');
    cy.get('[data-test="start"]').click();
    cy.get('[data-test="placement-editor"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'true');
    cy.get('[data-test="opponent-status"]').should('contain.text', 'Ready');
    cy.get('[data-test="ghost-pit"]').should('have.length', 16); // its layout stays hidden until I confirm
    cy.get('[data-test="preset-two-per-pit"]').click();
    cy.get('[data-test="confirm"]').should('not.be.disabled').click();
    cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="placement-editor"]').should('not.exist');
    cy.get('[data-test="tag-name"]').first().should('have.text', 'Bot 1 (Expert)');
    // The game view carries the level itself: after a reload (no lobby seen) the name is still right.
    cy.reload();
    cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="tag-name"]').first().should('have.text', 'Bot 1 (Expert)');
  });

  it('Expert from a placed checkerboard: the bot answers each move, to an end screen, and Play again keeps it and its level', () => {
    lobbyWithBot();
    level().select('Expert');
    level().should('have.value', 'expert');
    cy.get('[data-test="start"]').click();
    cy.get('[data-test="placement-editor"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="preset-checkerboard"]').click();
    cy.get('[data-test="seeds-left"]').should('contain.text', 'All seeds placed');
    cy.get('[data-test="opponent-half"]').should('have.attr', 'data-ready', 'true');
    cy.get('[data-test="confirm"]').should('not.be.disabled').click();
    cy.get('[data-test="mangola-board"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="placement-editor"]').should('not.exist');
    cy.get('[data-test="tag-name"]').first().should('have.text', 'Bot 1 (Expert)');
    cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
    // First exchange: the bot's move is read out and leaves its mark on the board.
    cy.get('[data-test^="pit-"][data-selectable="true"]').first().click();
    cy.get('[data-test="board-live"]:contains("Bot 1 (Expert) sowed")', { timeout: 30000 }).should('exist');
    // Once the bot's hand has landed it is my turn again, and its starting pit stays marked.
    cy.get('[data-test="turn-hint"]', { timeout: 30000 }).should('contain.text', 'Your turn');
    cy.get('[data-test^="pit-"][data-last-start="true"]', { timeout: 30000 }).should('exist');
    playUntilTheEnd();
    cy.get('[data-test="end"]', { timeout: 30000 }).should('be.visible');
    cy.get('[data-cy="play-again"]').click();
    cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="badge-bot"]').should('be.visible');
    level().should('have.value', 'expert');
    cy.get('[data-test="option-firstPlayer"] .choice--on').should('contain.text', 'Host');
  });

  SIZES.forEach(([width, height]) => {
    it(`fits the lobby with a bot at ${width}x${height} without horizontal scroll`, () => {
      cy.viewport(width, height);
      lobbyWithBot();
      level().scrollIntoView().should('be.visible');
      cy.get('[data-test="remove-bot-1"]').should('be.visible');
      cy.get('[data-test="start"]').scrollIntoView().should('be.visible');
      cy.document().then((doc) => {
        expect(doc.documentElement.scrollWidth, 'page width').to.be.at.most(doc.documentElement.clientWidth);
      });
    });
  });
});
