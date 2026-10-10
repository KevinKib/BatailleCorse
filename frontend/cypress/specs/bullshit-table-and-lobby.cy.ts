// Non-regression for the Bullshit table (never scrolls, one-row hand) and the lobby /
// start screens (same menu look as BatailleCorse, errors visible, nothing lost).
function createBullshitGame(name: string, claimLabel?: string) {
  // Enter through the menu, as a player does, so the WebSocket is connected before we create.
  cy.visit('/games/bataillecorse');
  cy.contains('button', 'Play Bullshit').click();
  cy.url().should('include', '/games/bullshit/create');
  cy.get('[data-test="name"]').type(name);
  if (claimLabel) cy.contains('label', claimLabel).click();
  // Deliberate fixed wait: the app exposes no "connected" signal and a create sent before the
  // WebSocket handshake finishes is reported to the player as an error (asserted elsewhere).
  cy.wait(1500);
  cy.get('[data-test="submit"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/bullshit\/room\/.+/);
}

function gameIdFromUrl(): Cypress.Chainable<string> {
  return cy.url().then((url) => url.split('/room/')[1]);
}

describe('Bullshit start screens', () => {
  it('keeps every field of the create screen and a visible name label', () => {
    cy.visit('/games/bullshit/create');
    cy.get('h1').should('contain.text', 'Bullshit');
    cy.get('label[for="bullshit-name"]').should('be.visible').and('contain.text', 'Your name');
    cy.contains('legend', 'Claim mode').should('be.visible');
    cy.contains('label', 'By rank').should('be.visible');
    cy.contains('label', 'By suit').should('be.visible');
    cy.get('[data-test="submit"]').should('be.visible').and('contain.text', 'Create game');
    cy.get('[data-test="back"]').should('be.visible');
  });

  it('shows a visible error, and stays put, when joining an unknown game', () => {
    cy.visit('/games/bullshit/join/does-not-exist');
    cy.get('[data-test="name"]').type('Zoe');
    cy.get('[data-test="submit"]').should('contain.text', 'Join game').click();
    cy.get('[data-test="start-error"]', { timeout: 10000 })
      .should('be.visible')
      .and('contain.text', 'Could not join this game');
    cy.location('pathname').should('include', '/games/bullshit/join/');
  });
});

describe('Bullshit lobby', () => {
  it('shows the title, players, claim mode, invite link, Copy and Start', () => {
    createBullshitGame('Alice', 'By suit');
    cy.get('[data-test="lobby"]').should('be.visible');
    cy.get('[data-test="lobby"] h1').should('contain.text', 'Bullshit');
    cy.get('[data-test="player-count"]').should('contain.text', '1 / 6');
    cy.get('.players li').should('have.length', 1).first().should('contain.text', 'Alice');
    cy.get('[data-test="badge-you"]').should('be.visible');
    cy.get('[data-test="claim-mode"]').should('contain.text', 'By suit');
    cy.get('[data-test="invite-link"]').invoke('val').should('match', /\/games\/bullshit\/join\/.+/);
    cy.get('[data-test="copy-link"]').should('be.visible').and('contain.text', 'Copy');
    cy.get('[data-test="start"]').should('be.visible').and('be.disabled');
    cy.get('[data-test="start-hint"]').should('contain.text', 'Waiting for 1 more player');
  });

  it('fits a phone screen without horizontal overflow', () => {
    cy.viewport(375, 667);
    createBullshitGame('Alice');
    cy.get('[data-test="start"]').should('exist');
    cy.document().then((doc) => {
      const el = doc.scrollingElement as Element;
      expect(el.scrollWidth, 'no horizontal overflow').to.be.at.most(el.clientWidth + 1);
      expect(el.scrollHeight, 'the page itself does not scroll').to.be.at.most(el.clientHeight + 1);
    });
  });
});

describe('Bullshit table (phone, 375x667)', () => {
  beforeEach(() => {
    cy.viewport(375, 667);
    createBullshitGame('Alice');
    gameIdFromUrl().then((id) => cy.request('POST', `/api/bullshit/game/${id}/join`, { name: 'Bob' }));
    cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
    cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
  });

  it('never scrolls vertically, even with a 26-card hand', () => {
    cy.get('[data-test^="hand-card-"]').should('have.length', 26);
    cy.document().then((doc) => {
      const el = doc.scrollingElement as Element;
      expect(el.scrollHeight, 'no vertical overflow').to.be.at.most(el.clientHeight + 1);
      expect(el.scrollWidth, 'no horizontal overflow').to.be.at.most(el.clientWidth + 1);
      const screen = doc.querySelector('.bullshit-screen') as HTMLElement;
      expect(screen.scrollHeight, 'screen content fits').to.be.at.most(screen.clientHeight + 1);
    });
  });

  it('keeps the whole hand on one row, inside the screen, and tappable', () => {
    cy.get('[data-test^="hand-card-"]').then(($cards) => {
      const rects = [...$cards].map((c) => c.getBoundingClientRect());
      expect(new Set(rects.map((r) => Math.round(r.top))).size, 'one row').to.eq(1);
      rects.forEach((r) => {
        expect(r.left).to.be.at.least(-1);
        expect(r.right).to.be.at.most(376);
      });
    });
    cy.get('[data-test="hand-card-25"]').click();
    cy.get('[data-test="hand-card-25"]').should('have.class', 'selected');
    cy.get('[data-test="hand-card-25"]').click();
    cy.get('[data-test="hand-card-25"]').should('not.have.class', 'selected');
  });

  it('caps the selection at 4 cards', () => {
    [0, 1, 2, 3, 4].forEach((i) => cy.get(`[data-test="hand-card-${i}"]`).click(4, 40));
    cy.get('[data-test^="hand-card-"].selected').should('have.length', 4);
  });

  it('keeps every piece of game information and both actions on screen', () => {
    cy.get('[data-test="claim-badge"]').should('be.visible').and('contain.text', 'Claim');
    cy.get('[data-test="seat-label"]').should('be.visible').and('contain.text', 'Player 2');
    cy.get('[data-test="seat-count"]').should('be.visible');
    cy.contains('.my-tag', 'You').should('be.visible');
    cy.get('[data-cy="rules-toggle"]').should('be.visible');
    cy.get('[data-test="leave"]').should('be.visible');
    ['discard', 'call'].forEach((t) => {
      cy.get(`[data-test="${t}"]`).should('be.visible').then(($b) => {
        const r = $b[0].getBoundingClientRect();
        expect(r.bottom, `${t} above the bottom edge`).to.be.at.most(667);
        expect(r.left).to.be.at.least(0);
        expect(r.right).to.be.at.most(375);
      });
    });
  });
});

describe('Bullshit table (desktop, 1280x720)', () => {
  it('does not scroll either', () => {
    cy.viewport(1280, 720);
    createBullshitGame('Alice');
    gameIdFromUrl().then((id) => cy.request('POST', `/api/bullshit/game/${id}/join`, { name: 'Bob' }));
    cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
    cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
    cy.document().then((doc) => {
      const el = doc.scrollingElement as Element;
      expect(el.scrollHeight).to.be.at.most(el.clientHeight + 1);
    });
    cy.get('[data-test="discard"]').should('be.visible');
    cy.get('[data-test="call"]').should('be.visible');
  });
});
