// Non-regression for the Atkinson Hyperlegible switch: the face is wider than the previous
// ones, so long names, seats, labels and the 26-card hand must still fit every phone and
// desktop viewport without scrolling, clipping or overflowing.
const VIEWPORTS: Array<[number, number]> = [[375, 667], [390, 844], [1280, 720]];
const LONG_NAME = 'Maximilienne-Alexandrine'; // 24 characters

function createBullshitGame(name: string) {
  cy.visit('/games/bataillecorse');
  cy.contains('button', 'Play Bullshit').click();
  cy.url().should('include', '/games/bullshit/create');
  cy.get('[data-test="name"]').type(name);
  // Deliberate fixed wait: no "connected" signal is exposed and a create sent before the
  // WebSocket handshake ends is reported to the player as an error.
  cy.wait(1500);
  cy.get('[data-test="submit"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/bullshit\/room\/.+/);
}

function joinOthers(count: number) {
  cy.url().then((url) => {
    const id = url.split('/room/')[1];
    for (let i = 0; i < count; i++) {
      cy.request('POST', `/api/bullshit/game/${id}/join`, { name: `${LONG_NAME}${i + 2}` });
    }
  });
}

// Fails when something sticks out of the viewport or when text is silently clipped.
// `slack` allows a few px for panels that already carried a fixed min-width before the font change.
function expectNoOverflow(selector: string, width: number, slack = 1) {
  cy.get(selector).then(($root) => {
    const root = $root[0];
    const offenders: string[] = [];
    root.querySelectorAll<HTMLElement>('*').forEach((el) => {
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.height === 0) return;
      const style = getComputedStyle(el);
      if (style.visibility === 'hidden' || style.display === 'none') return;
      if (el.closest('.felt-watermark, .deco-cards')) return; // decorative, bleeds on purpose
      if (r.left < -slack || r.right > width + slack) {
        offenders.push(`${el.tagName}.${el.className} sticks out [${Math.round(r.left)},${Math.round(r.right)}]`);
      }
      const clipsText = style.overflow !== 'visible' && el.scrollWidth > el.clientWidth + 1;
      if (clipsText && style.textOverflow !== 'ellipsis' && el.children.length === 0 && el.textContent?.trim()) {
        offenders.push(`${el.tagName}.${el.className} clips "${el.textContent.trim()}"`);
      }
    });
    expect(offenders, 'overflowing elements').to.deep.eq([]);
  });
}

describe('Atkinson Hyperlegible fit', () => {
  VIEWPORTS.forEach(([width, height]) => {
    describe(`${width}x${height}`, () => {
      beforeEach(() => cy.viewport(width, height));

      it('keeps the create screen and a lobby with long names inside the screen', () => {
        cy.visit('/games/bullshit/create');
        cy.get('[data-test="name"]').type(LONG_NAME);
        expectNoOverflow('#app', width);
        createBullshitGame(LONG_NAME);
        joinOthers(5);
        cy.get('.players li').should('have.length', 6);
        // The lobby panel scrolls inside the screen when 6 players do not fit a short viewport.
        cy.get('[data-test="start"]').scrollIntoView().should('be.visible');
        expectNoOverflow('#app', width);
      });

      [2, 4, 6].forEach((players) => {
        it(`never scrolls with ${players} players and a full hand`, () => {
          createBullshitGame(LONG_NAME);
          joinOthers(players - 1);
          cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
          cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
          cy.document().then((doc) => {
            const el = doc.scrollingElement as Element;
            expect(el.scrollHeight, 'no vertical overflow').to.be.at.most(el.clientHeight + 1);
            expect(el.scrollWidth, 'no horizontal overflow').to.be.at.most(el.clientWidth + 1);
          });
          expectNoOverflow('.bullshit-screen', width);
          ['discard', 'call'].forEach((t) => cy.get(`[data-test="${t}"]`).should('be.visible'));
        });
      });
    });
  });

  it('keeps the Bataille Corse menus and game screen inside a phone screen', () => {
    cy.viewport(375, 667);
    cy.visit('/games/bataillecorse');
    expectNoOverflow('#app', 375, 3); // the menu panel keeps its pre-existing 380px min-width
    cy.createGame();
    cy.get('.game-timer').should('be.visible');
    cy.get('.title-panel').should('not.exist'); // route transition finished
    expectNoOverflow('#app', 375);
    cy.get('[data-cy="rules-toggle"]').click();
    expectNoOverflow('#app', 375);
  });
});
