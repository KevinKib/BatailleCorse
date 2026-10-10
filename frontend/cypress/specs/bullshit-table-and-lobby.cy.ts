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
    cy.get('[data-test="seat-label"]').should('be.visible').and('contain.text', 'Bob');
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

describe('Bullshit arrival by invite link (no flash of the table)', () => {
  it('shows the lobby without ever rendering the game table, even on a slow network', () => {
    createBullshitGame('Alice');
    gameIdFromUrl().then((id) => {
      cy.clearLocalStorage();
      // Slow every read so the "connecting" phase lasts long enough to be seen.
      cy.intercept('GET', '/api/**', (req) => req.continue((res) => { res.delay = 1500; }));
      cy.visit(`/games/bullshit/join/${id}`, {
        onBeforeLoad(win) {
          // Records any moment the table or the in-game actions exist in the DOM.
          (win as any).__sawTable = false;
          new win.MutationObserver(() => {
            if (win.document.querySelector('.table-frame, [data-test="discard"], [data-test="call"]')) {
              (win as any).__sawTable = true;
            }
          }).observe(win.document, { childList: true, subtree: true });
        },
      });
      cy.get('[data-test="name"]').type('Bob');
      cy.get('[data-test="submit"]').click();
      cy.get('[data-test="lobby"]', { timeout: 15000 }).should('be.visible');
      cy.window().its('__sawTable').should('eq', false);
    });
  });
});

// Layout matrix. The game state is rewritten in the store (players, hand size) so every
// combination can be measured on the real screen without playing the game down.
const RANKS = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'JACK', 'QUEEN', 'KING', 'ACE'];
const SUITS = ['HEART', 'DIAMOND', 'CLUB', 'SPADE'];

function setTable(players: number, handSize: number) {
  // Retried until it sticks: a late server push may overwrite the rewritten state once.
  let applied = false;
  cy.window().should((win) => {
    const shown = win.document.querySelectorAll('[data-test^="hand-card-"]').length;
    if (!applied || shown !== handSize) { applyTable(win, players, handSize); applied = true; }
    expect(win.document.querySelectorAll('[data-test^="hand-card-"]')).to.have.length(handSize);
  });
}

function applyTable(win: Cypress.AUTWindow, players: number, handSize: number) {
  {
    const app = (win.document.querySelector('#app') as any).__vue_app__;
    const state = app.config.globalProperties.$pinia.state.value['bullshit-store'].state;
    state.players = Array.from({ length: players }, (_, i) => ({
      id: String(i), handCount: i === 0 ? handSize : 5, isCurrentPlayer: i === 0,
    }));
    state.myHand = Array.from({ length: handSize }, (_, i) => {
      const rank = RANKS[i % 13];
      const suit = SUITS[Math.floor(i / 13) % 4];
      return { rank, suit, name: `${suit}_${rank}` };
    });
    state.availableActions = ['DISCARD', 'CALL_BULLSHIT'];
    // One raised (selected) card: the spacing must hold with it too.
    const store = app.config.globalProperties.$pinia.state.value['bullshit-store'];
    store.selectedCards = [state.myHand[1]];
  }
}

function startTwoPlayerGame() {
  createBullshitGame('Alice');
  gameIdFromUrl().then((id) => cy.request('POST', `/api/bullshit/game/${id}/join`, { name: 'Bob' }));
  cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
  cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
  // Deliberate fixed wait: let the last server pushes land so they cannot overwrite the
  // state the matrix rewrites afterwards.
  cy.wait(1000);
}

describe('Bullshit hand / actions spacing matrix', () => {
  const viewports: Array<[number, number]> = [[375, 667], [390, 844], [1280, 720], [1280, 800]];
  const players = [2, 3, 4, 5, 6];
  const hands = [5, 13, 26];

  function measure(vw: number, vh: number, handSize: number, label: string) {
    cy.document().then((doc) => {
      const el = doc.scrollingElement as Element;
      expect(el.scrollHeight, `${label}: no vertical scroll`).to.be.at.most(el.clientHeight + 1);
      expect(el.scrollWidth, `${label}: no horizontal scroll`).to.be.at.most(el.clientWidth + 1);
      const cards = [...doc.querySelectorAll('[data-test^="hand-card-"]')] as HTMLElement[];
      expect(cards, `${label}: cards`).to.have.length(handSize);
      const rects = cards.map((c) => c.getBoundingClientRect());
      const resting = cards.filter((c) => !c.classList.contains('selected'));
      expect(resting.length, `${label}: a card is raised`).to.eq(cards.length - 1);
      expect(new Set(resting.map((c) => Math.round(c.getBoundingClientRect().top))).size, `${label}: one row`).to.eq(1);
      rects.forEach((r) => {
        expect(r.left, label).to.be.at.least(-1);
        expect(r.right, label).to.be.at.most(vw + 1);
      });
      // Visible boxes: the "You" tag, the cards (raised one included) and the buttons.
      const you = (doc.querySelector('.my-tag') as HTMLElement).getBoundingClientRect();
      const cardsTop = Math.min(...rects.map((r) => r.top));
      const cardsBottom = Math.max(...rects.map((r) => r.bottom));
      expect(cardsTop - you.bottom, `${label}: You to the raised card`).to.be.at.least(11.5);
      const buttons = ['discard', 'call'].map((t) =>
        (doc.querySelector(`[data-test="${t}"]`) as HTMLElement).getBoundingClientRect());
      buttons.forEach((b) => {
        expect(b.bottom, `${label}: button above the bottom edge`).to.be.at.most(vh);
        expect(b.height, `${label}: touch target`).to.be.at.least(44);
      });
      expect(Math.min(...buttons.map((b) => b.top)) - cardsBottom, `${label}: cards to buttons`).to.be.at.least(15.5);
      expect(buttons[1].left - buttons[0].right, `${label}: between buttons`).to.be.within(7.5, 12.5);
    });
  }

  viewports.forEach(([vw, vh]) => {
    it(`holds at ${vw}x${vh} for every player count and hand size`, () => {
      cy.viewport(vw, vh);
      startTwoPlayerGame();
      players.forEach((p) => hands.forEach((h) => {
        setTable(p, h);
        cy.get('[data-test^="hand-card-"]').should('have.length', h);
        cy.get('.hand-card.selected').should('have.length', 1);
        // Deliberate fixed wait: the raised card finishes its 0.12s transform transition.
        cy.wait(250);
        measure(vw, vh, h,`${vw}x${vh} ${p}p ${h}c`);
      }));
    });
  });
});
