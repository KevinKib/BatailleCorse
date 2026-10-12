// Non-regression for the Bullshit lobby bot controls and for the lobby / create screen spacing.
function createBullshitGame(name: string) {
  cy.visit('/games/bullshit');
  cy.get('[data-test="name"]').type(name);
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/bullshit\/room\/.+/);
  cy.get('[data-test="lobby"]').should('be.visible');
}

const addBot = () => cy.get('[data-test="add-bot"]').click();

describe('Bullshit lobby bots', () => {
  it('lets the host add and remove bots, which count as players', () => {
    createBullshitGame('Alice');
    cy.get('[data-test="start"]').should('be.disabled');
    addBot();
    cy.get('[data-test="player-count"]').should('contain.text', '2 / 6');
    cy.get('[data-test="badge-bot"]').should('have.length', 1);
    cy.get('.players li').eq(1).should('contain.text', 'Bot 1');
    // The host plus one bot is enough to start.
    cy.get('[data-test="start"]').should('not.be.disabled');
    cy.get('[data-test="start-hint"]').should('not.exist');
    cy.get('[data-test="remove-bot-1"]').should('not.be.disabled').click();
    cy.get('[data-test="player-count"]').should('contain.text', '1 / 6');
    cy.get('[data-test="badge-bot"]').should('not.exist');
    cy.get('[data-test="start-hint"]').should('contain.text', 'Waiting for 1 more player');
  });

  it('disables Add bot when the lobby is full and never offers to remove the host', () => {
    createBullshitGame('Alice');
    for (let i = 1; i <= 5; i++) {
      addBot();
      cy.get('[data-test="badge-bot"]').should('have.length', i);
    }
    cy.get('[data-test="player-count"]').should('contain.text', '6 / 6');
    cy.get('[data-test="add-bot"]').should('be.disabled');
    cy.get('[data-test="remove-bot-0"]').should('not.exist');
    cy.get('[data-test="remove-bot-5"]').should('not.be.disabled');
  });

  it('shows a guest the bots without any control', () => {
    createBullshitGame('Alice');
    addBot();
    cy.get('[data-test="badge-bot"]').should('have.length', 1);
    cy.url().then((url) => {
      const id = url.split('/room/')[1];
      cy.request('POST', `/api/bullshit/game/${id}/join`, { name: 'Bob' }).then((res) => {
        cy.clearLocalStorage();
        cy.window().then((win) => {
          win.localStorage.setItem(`bullshit:tokens:${id}`, JSON.stringify({ [res.body.playerId]: res.body.token }));
        });
        cy.visit(`/games/bullshit/room/${id}`);
        // The lobby scrolls inside its screen: bring the hint into view before asserting.
        cy.get('[data-test="waiting-host"]').scrollIntoView().should('be.visible');
        cy.get('[data-test="badge-bot"]').should('have.length', 1);
        cy.get('[data-test="add-bot"]').should('not.exist');
        cy.get('[data-test^="remove-bot-"]').should('not.exist');
      });
    });
  });
});

describe('Bullshit lobby layout', () => {
  [375, 1280].forEach((width) => {
    it(`keeps 44 px targets and a 16 px gap above Start in the lobby at ${width} px`, () => {
      cy.viewport(width, 800);
      createBullshitGame('Alice');
      cy.document().then((doc) => {
        const rect = (sel: string) => (doc.querySelector(sel) as HTMLElement).getBoundingClientRect();
        ['[data-test="leave-lobby"]', '[data-test="add-bot"]', '[data-test="copy-link"]', '[data-test="start"]',
          '.players li', '[data-test="option-claimMode"] .choice', '[data-test="option-deckSize"] .choice',
        ].forEach((sel) => expect(rect(sel).height, sel).to.be.at.least(44));
        const start = rect('[data-test="start"]');
        const hint = rect('[data-test="start-hint"]');
        expect(hint.top - start.bottom, 'Start to its hint').to.be.closeTo(16, 1);
      });
    });
  });

  it('draws the lobby subtitle with the --font-title token and keeps 44 px targets', () => {
    createBullshitGame('Alice');
    cy.get('[data-test="lobby-title"]').should('contain.text', 'Lobby').then(($h) => {
      const win = $h[0].ownerDocument.defaultView as Window;
      const token = win.getComputedStyle(win.document.documentElement).getPropertyValue('--font-title').trim();
      expect(win.getComputedStyle($h[0]).fontFamily).to.eq(token);
    });
    ['add-bot', 'copy-link', 'start'].forEach((t) => {
      cy.get(`[data-test="${t}"]`).then(($e) => expect($e[0].getBoundingClientRect().height, t).to.be.at.least(44));
    });
  });
});

describe('Bullshit full lobby on small screens (6 players, bots included)', () => {
  // The panel scrolls inside its screen when the lobby is taller than the viewport: the page itself never
  // scrolls, never sideways, and Start is reached by scrolling the panel.
  const viewports: Array<[number, number]> = [[375, 667], [390, 844], [1280, 720], [1280, 800]];
  viewports.forEach(([vw, vh]) => {
    it(`keeps every block reachable and the page still at ${vw}x${vh}`, () => {
      cy.viewport(vw, vh);
      createBullshitGame('Alice');
      for (let i = 1; i <= 5; i++) {
        addBot();
        cy.get('[data-test="badge-bot"]').should('have.length', i);
      }
      cy.get('[data-test="player-count"]').should('contain.text', '6 / 6');
      cy.document().then((doc) => {
        const label = `${vw}x${vh}`;
        const root = doc.scrollingElement as Element;
        expect(root.scrollHeight, `${label}: the page does not scroll`).to.be.at.most(root.clientHeight + 1);
        expect(root.scrollWidth, `${label}: no horizontal scroll`).to.be.at.most(root.clientWidth + 1);
        const scroller = (doc.querySelector('[data-test="lobby"]') as HTMLElement).parentElement as HTMLElement;
        expect(scroller.scrollWidth, `${label}: the lobby does not scroll sideways`).to.be.at.most(scroller.clientWidth + 1);
      });
      // Nothing the lobby shows is lost: each block can be brought on screen.
      ['lobby-title', 'player-count', 'leave-lobby', 'add-bot', 'option-claimMode', 'option-deckSize', 'invite-link', 'copy-link', 'start']
        .forEach((t) => cy.get(`[data-test="${t}"]`).scrollIntoView().should('be.visible'));
      cy.get('[data-test="start"]').scrollIntoView().then(($start) => {
        const r = $start[0].getBoundingClientRect();
        expect(r.bottom, 'Start above the bottom edge').to.be.at.most(vh);
        expect(r.height, 'Start touch target').to.be.at.least(44);
      });
      cy.document().then((doc) => {
        const root = doc.scrollingElement as Element;
        expect(root.scrollHeight, 'the page still does not scroll').to.be.at.most(root.clientHeight + 1);
        expect(root.scrollWidth, 'no horizontal scroll').to.be.at.most(root.clientWidth + 1);
      });
    });
  });
});
