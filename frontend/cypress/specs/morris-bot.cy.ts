// Three Men's Morris against a bot, end to end. The browser is the host (White); the bot answers at once under the
// `test` profile (zero delay, constant generator). Every wait is on a DOM state, never on a delay.
// Points are numbered row by row (0 1 2 / 3 4 5 / 6 7 8).
const SIZES: Array<[number, number]> = [[375, 667], [1280, 800]];

const level = () => cy.get('[data-test="seat-option-1-level"]');
const mine = () => cy.get('[data-test^="point-"][data-owner="white"]');
const theirs = () => cy.get('[data-test^="point-"][data-owner="black"]');

/** The host's lobby with a bot on seat 1; `variant` picks the radio when it is not Tapatan. */
function lobbyWithBot(variant?: 'achi' | 'picaria') {
  cy.visit('/games/three-men-morris');
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
  if (variant) cy.get(`[data-test="option-variant-${variant}"]`).check({ force: true });
  cy.get('[data-test="add-bot"]').click();
}

describe("Three Men's Morris against a bot", () => {
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
    level().should('have.value', 'perfect');
    cy.get('[data-test="add-bot"]').should('be.disabled'); // two seats only
    cy.get('[data-test="start"]').should('not.be.disabled');
    cy.get('[data-test="remove-bot-1"]').click();
    cy.get('[data-test="player-count"]').should('contain.text', '1 / 2');
    cy.get('[data-test="add-bot"]').should('not.be.disabled');
  });

  it('Tapatan at Expert: the bot answers each placement', () => {
    lobbyWithBot();
    level().select('Expert');
    cy.get('[data-test="start"]').click();
    cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
    cy.get('[data-test="point-4"]').click();
    mine().should('have.length', 1);
    theirs().should('have.length', 1);
    cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
    cy.get('[data-test="tag-name"]').first().should('have.text', 'Bot 1');
  });

  it('Picaria at Expert: the bot answers each placement, solved: a win for White', () => {
    lobbyWithBot('picaria');
    level().select('Expert');
    cy.get('[data-test="start"]').click();
    cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('have.attr', 'data-variant', 'picaria');
    cy.get('[data-test="point-0"]').click();
    mine().should('have.length', 1);
    theirs().should('have.length', 1);
    cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
  });

  it('Picaria at Expert: the bot, which solves the 13-point board (a win for White), punishes a poor opening, and Play again keeps the bot and its level', () => {
    lobbyWithBot('picaria');
    level().select('Expert');
    cy.get('[data-test="start"]').click();
    cy.get('[data-test="morris-board"]', { timeout: 10000 }).should('be.visible');
    // Three placements, on the free points the board highlights (the first one, every time): not the winning opening
    // (a side middle), so Black lines up three. A draw is no longer reachable, the board being a win for White.
    [1, 2, 3].forEach((n) => {
      cy.get('[data-test="turn-hint"]').should('contain.text', 'Your turn');
      cy.get('[data-test^="point-"][data-state="highlighted"]').first().click();
      mine().should('have.length', n);
      theirs().should('have.length', n);
    });
    cy.get('[data-test="end"]', { timeout: 10000 }).should('be.visible');
    cy.get('.end-title').should('have.text', 'DEFEAT');
    cy.get('.end-sub').should('have.text', 'Bot 1 lined up three.');
    cy.get('[data-cy="play-again"]').click();
    cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
    cy.get('[data-test="badge-bot"]').should('be.visible');
    level().should('have.value', 'perfect');
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
