// Non-regression for the Bullshit lobby bot controls and for the lobby / create screen spacing.
function createBullshitGame(name: string) {
  cy.visit('/games/bullshit/create');
  cy.url().should('include', '/games/bullshit/create');
  cy.get('[data-test="name"]').type(name);
  // Deliberate fixed wait: the app exposes no "connected" signal and a create sent before the
  // WebSocket handshake finishes is reported to the player as an error.
  cy.wait(1500);
  cy.get('[data-test="submit"]').click();
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
    it(`keeps 44 px targets and 24/16 px section gaps on the create screen at ${width} px`, () => {
      cy.viewport(width, 800);
      cy.visit('/games/bullshit/create');
      cy.get('[data-test="start-panel"]').should('be.visible');
      cy.document().then((doc) => {
        const rect = (sel: string) => (doc.querySelector(sel) as HTMLElement).getBoundingClientRect();
        const name = rect('[data-test="name"]');
        const claim = rect('fieldset.claim-mode');
        const legend = rect('fieldset.claim-mode legend');
        const toggle = rect('.mode-toggle');
        const submit = rect('[data-test="submit"]');
        const back = rect('[data-test="back"]');
        expect(claim.top - name.bottom, 'name field to Claim mode').to.be.at.least(24);
        expect(toggle.top - legend.bottom, 'Claim mode label to its buttons').to.be.closeTo(8, 1);
        expect(submit.top - toggle.bottom, 'Claim mode to Create game').to.be.at.least(24);
        expect(back.top - submit.bottom, 'Create game to Back').to.be.closeTo(16, 1);
        [name, toggle, submit, back].forEach((r) => expect(r.height).to.be.at.least(44));
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
    ['add-bot', 'claim-mode', 'copy-link', 'start'].forEach((t) => {
      cy.get(`[data-test="${t}"]`).then(($e) => expect($e[0].getBoundingClientRect().height, t).to.be.at.least(44));
    });
  });
});

describe('Bullshit full lobby fits the viewport (6 players, bots included)', () => {
  const viewports: Array<[number, number]> = [[375, 667], [390, 844], [1280, 720], [1280, 800]];
  viewports.forEach(([vw, vh]) => {
    it(`keeps Start visible without scrolling at ${vw}x${vh}`, () => {
      cy.viewport(vw, vh);
      createBullshitGame('Alice');
      for (let i = 1; i <= 5; i++) {
        addBot();
        cy.get('[data-test="badge-bot"]').should('have.length', i);
      }
      cy.get('[data-test="player-count"]').should('contain.text', '6 / 6');
      cy.document().then((doc) => {
        const label = `${vw}x${vh}`;
        const scroller = (doc.querySelector('[data-test="lobby"]') as HTMLElement).parentElement as HTMLElement;
        expect(scroller.scrollHeight, `${label}: the lobby does not scroll`).to.be.at.most(scroller.clientHeight + 1);
        const root = doc.scrollingElement as Element;
        expect(root.scrollHeight, `${label}: the page does not scroll`).to.be.at.most(root.clientHeight + 1);
        expect(root.scrollWidth, `${label}: no horizontal scroll`).to.be.at.most(root.clientWidth + 1);
        const start = (doc.querySelector('[data-test="start"]') as HTMLElement).getBoundingClientRect();
        expect(start.bottom, `${label}: Start above the bottom edge`).to.be.at.most(vh);
        expect(start.height, `${label}: Start touch target`).to.be.at.least(44);
        // Nothing the lobby shows is lost: each block is still on screen.
        ['lobby-title', 'player-count', 'add-bot', 'claim-mode', 'deck-size', 'invite-link', 'copy-link'].forEach((t) => {
          const r = (doc.querySelector(`[data-test="${t}"]`) as HTMLElement).getBoundingClientRect();
          expect(r.height, `${label}: ${t} is shown`).to.be.greaterThan(0);
          expect(r.top, `${label}: ${t} inside the screen`).to.be.at.least(0);
          expect(r.bottom, `${label}: ${t} inside the screen`).to.be.at.most(vh);
        });
        const panel = (doc.querySelector('[data-test="lobby"]') as HTMLElement).getBoundingClientRect();
        expect(panel.top, `${label}: panel inside the screen`).to.be.at.least(0);
        expect(panel.bottom, `${label}: panel inside the screen`).to.be.at.most(vh);
      });
    });
  });
});
