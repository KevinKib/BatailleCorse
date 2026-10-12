// Bataille Corse and Bullshit share their chrome: Back bottom left, shortcut gear bottom right and a
// duration chip top left on the table; the same gear in the same corner of the Game home and the lobby.
// Measured on the real screens at phone and desktop size: no overlap, no scroll, nothing off screen.
type Box = { name: string; r: DOMRect };

const RANKS = ['2', '3', '4', '5', '6', '7', '8', '9', '10', 'JACK', 'QUEEN', 'KING', 'ACE'];
const SUITS = ['HEART', 'DIAMOND', 'CLUB', 'SPADE'];

const noScroll = () => cy.document().then((doc) => {
  const el = doc.scrollingElement as Element;
  expect(el.scrollWidth, 'no horizontal scroll').to.be.at.most(el.clientWidth + 1);
  expect(el.scrollHeight, 'no vertical scroll').to.be.at.most(el.clientHeight + 1);
});

const intersects = (a: DOMRect, b: DOMRect) =>
  a.left < b.right - 0.5 && b.left < a.right - 0.5 && a.top < b.bottom - 0.5 && b.top < a.bottom - 0.5;

function boxesOf(doc: Document, selectors: Record<string, string>): Box[] {
  return Object.entries(selectors).flatMap(([name, sel]) =>
    ([...doc.querySelectorAll(sel)] as HTMLElement[])
      .filter((e) => e.getClientRects().length > 0)
      .map((e, i) => ({ name: `${name}#${i}`, r: e.getBoundingClientRect() })));
}

function expectNoOverlap(boxes: Box[], vw: number, vh: number, ignorePair: (a: string, b: string) => boolean = () => false) {
  boxes.forEach(({ name, r }) => {
    expect(r.left, `${name} left`).to.be.at.least(-1);
    expect(r.top, `${name} top`).to.be.at.least(-1);
    expect(r.right, `${name} right`).to.be.at.most(vw + 1);
    expect(r.bottom, `${name} bottom`).to.be.at.most(vh + 1);
  });
  for (let i = 0; i < boxes.length; i++) {
    for (let j = i + 1; j < boxes.length; j++) {
      if (ignorePair(boxes[i].name, boxes[j].name)) continue;
      expect(intersects(boxes[i].r, boxes[j].r), `${boxes[i].name} and ${boxes[j].name} must not overlap`).to.eq(false);
    }
  }
}

function startBullshit() {
  cy.visit('/games/bullshit');
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/bullshit\/room\/.+/);
  cy.url().then((url) => cy.request('POST', `/api/bullshit/game/${url.split('/room/')[1]}/join`, { name: 'Bob' }));
  cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
  cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
  // The lobby fades out while the table fades in: wait until the table is alone.
  cy.get('[data-test="lobby"]').should('not.exist');
  cy.get('[data-test="hotkeys-button"]').should('have.length', 1);
}

// The state is rewritten in the store so any table size can be measured without playing it down.
function setTable(players: number, handSize: number) {
  let applied = false;
  cy.window().should((win) => {
    const shown = win.document.querySelectorAll('[data-test^="hand-card-"]').length;
    if (!applied || shown !== handSize) {
      const app = (win.document.querySelector('#app') as any).__vue_app__;
      const store = app.config.globalProperties.$pinia.state.value['bullshit-store'];
      store.state.players = Array.from({ length: players }, (_, i) => ({ id: String(i), handCount: i === 0 ? handSize : 5, isCurrentPlayer: i === 0 }));
      store.state.myHand = Array.from({ length: handSize }, (_, i) => {
        const rank = RANKS[i % 13];
        const suit = SUITS[Math.floor(i / 13) % 4];
        return { rank, suit, name: `${suit}_${rank}` };
      });
      store.state.availableActions = ['DISCARD', 'CALL_BULLSHIT'];
      store.selectedCards = [store.state.myHand[1]];
      applied = true;
    }
    expect(win.document.querySelectorAll('[data-test^="hand-card-"]')).to.have.length(handSize);
  });
}

const VIEWPORTS: Array<[number, number]> = [[375, 667], [1280, 800]];

describe('Bullshit table chrome', () => {
  VIEWPORTS.forEach(([vw, vh]) => {
    [[5, 10], [2, 26]].forEach(([players, hand]) => {
      it(`${vw}x${vh}, ${players} players, ${hand} cards: Back bottom left, gear bottom right, timer top left, nothing overlaps or scrolls`, () => {
        cy.viewport(vw, vh);
        cy.clearLocalStorage();
        startBullshit();
        cy.get('[data-test="hand-card-0"]').should('be.visible');
        setTable(players, hand);
        cy.get('[data-cy="game-timer"]').should('be.visible');
        noScroll();
        cy.document().then((doc) => {
          const boxes = boxesOf(doc, {
            timer: '[data-cy="game-timer"]',
            sound: '[data-test="sound-toggle"]',
            rules: '[data-cy="rules-toggle"]',
            back: '[data-test="leave"]',
            gear: '[data-test="hotkeys-button"]',
            hint: '[data-test="keys-hint"]',
            seat: '.opponent-seat',
            claim: '[data-test="claim-badge"]',
            pile: '.pile-well',
            you: '.my-tag',
            count: '[data-test="my-count"]',
            discard: '[data-test="discard"]',
            call: '[data-test="call"]',
            card: '[data-test^="hand-card-"]',
          });
          // Hand cards fan over each other by design.
          expectNoOverlap(boxes, vw, vh, (a, b) => a.startsWith('card#') && b.startsWith('card#'));
          const by = (prefix: string) => boxes.find((b) => b.name.startsWith(prefix + '#'))!.r;
          expect(by('timer').left, 'timer on the left').to.be.lessThan(vw / 3);
          expect(by('timer').top, 'timer at the top').to.be.lessThan(vh / 6);
          expect(by('back').left, 'Back on the left').to.be.lessThan(vw / 3);
          expect(by('back').top, 'Back at the bottom').to.be.greaterThan(vh * 0.8);
          expect(by('gear').right, 'gear on the right').to.be.greaterThan(vw * 2 / 3);
          expect(by('gear').top, 'gear at the bottom').to.be.greaterThan(vh * 0.8);
          expect(Math.abs(by('gear').bottom - by('back').bottom), 'Back and gear share a baseline').to.be.at.most(8);
        });
      });
    });

    it(`${vw}x${vh}: the settings panel opens inside the screen, upwards from the gear`, () => {
      cy.viewport(vw, vh);
      cy.clearLocalStorage();
      startBullshit();
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('[data-test="hotkeys-panel"]').should('be.visible').then(($p) => {
        const r = $p[0].getBoundingClientRect();
        expect(r.left).to.be.at.least(0);
        expect(r.right).to.be.at.most(vw);
        expect(r.top).to.be.at.least(0);
      });
      noScroll();
    });
  });

  it('the duration chip keeps counting while the game is on', () => {
    cy.viewport(375, 667);
    startBullshit();
    cy.get('[data-cy="game-timer"]').invoke('text').then((first) => {
      cy.get('[data-cy="game-timer"]', { timeout: 6000 }).should(($t) => expect($t.text().trim()).not.to.eq(first.trim()));
    });
  });
});

describe('Bataille Corse table chrome', () => {
  VIEWPORTS.forEach(([vw, vh]) => {
    it(`${vw}x${vh}: Back bottom left, gear bottom right, timer top left`, () => {
      cy.viewport(vw, vh);
      cy.clearLocalStorage();
      cy.createGame();
      cy.get('[data-test="hotkeys-button"]', { timeout: 10000 }).should('be.visible');
      cy.document().then((doc) => {
        const boxes = boxesOf(doc, {
          timer: '[data-cy="game-timer"]',
          rules: '[data-cy="rules-toggle"]',
          back: '.back_button',
          gear: '[data-test="hotkeys-button"]',
        });
        expectNoOverlap(boxes, vw, vh);
        const by = (prefix: string) => boxes.find((b) => b.name.startsWith(prefix + '#'))!.r;
        expect(by('timer').left).to.be.lessThan(vw / 3);
        expect(by('back').left).to.be.lessThan(vw / 3);
        expect(by('back').top).to.be.greaterThan(vh * 0.8);
        expect(by('gear').right).to.be.greaterThan(vw * 2 / 3);
        expect(by('gear').top).to.be.greaterThan(vh * 0.8);
      });
    });
  });
});

describe('Local preferences gear on the Game home and in the lobby', () => {
  const gearOffsets: Record<string, { fromRight: number; fromTop: number }> = {};

  VIEWPORTS.forEach(([vw, vh]) => {
    ['bataillecorse', 'bullshit'].forEach((key) => {
      it(`${vw}x${vh} ${key} Game home: the gear sits in the top right corner of the panel, clear of the title, keyboard-operable`, () => {
        cy.viewport(vw, vh);
        cy.clearLocalStorage();
        cy.visit(`/games/${key}`);
        cy.get('[data-test="hotkeys-button"]').should('be.visible').then(($g) => {
          cy.get('[data-test="start-panel"]').then(($p) => {
            const g = $g[0].getBoundingClientRect();
            const p = $p[0].getBoundingClientRect();
            expect(g.top - p.top, 'inside the panel, near its top').to.be.within(0, 24);
            expect(p.right - g.right, 'inside the panel, near its right').to.be.within(0, 24);
            gearOffsets[`${key}-${vw}`] = { fromRight: Math.round(p.right - g.right), fromTop: Math.round(g.top - p.top) };
          });
          cy.get('.game-title').then(($t) => {
            expect(intersects($g[0].getBoundingClientRect(), $t[0].getBoundingClientRect()), 'gear and title').to.eq(false);
          });
        });
        noScroll();
        // A real button, reachable by Tab: it takes focus and opens with the keyboard activation (a click).
        cy.get('[data-test="hotkeys-button"]').should('match', 'button').focus().should('have.focus').click();
        cy.get('[data-test="hotkeys-panel"]').should('be.visible').then(($p) => {
          const r = $p[0].getBoundingClientRect();
          expect(r.left).to.be.at.least(0);
          expect(r.right).to.be.at.most(vw);
        });
        noScroll();
        cy.get('[data-test="hotkeys-button"]').trigger('keydown', { key: 'Escape', keyCode: 27, bubbles: true });
        cy.get('[data-test="hotkeys-panel"]').should('not.exist');
      });
    });

    it(`${vw}x${vh}: both Game homes put the gear at the same place`, () => {
      expect(gearOffsets[`bataillecorse-${vw}`], 'measured').to.deep.eq(gearOffsets[`bullshit-${vw}`]);
    });

    it(`${vw}x${vh} Bullshit lobby: same gear, same corner, no overlap with the title, no scroll`, () => {
      cy.viewport(vw, vh);
      cy.clearLocalStorage();
      cy.visit('/games/bullshit');
      cy.get('[data-test="new-game"]').click();
      cy.get('[data-test="lobby"]', { timeout: 10000 }).should('be.visible');
      // The Game home fades out while the lobby fades in: measure once the lobby is alone.
      cy.get('[data-test="start-panel"]').should('not.exist');
      cy.get('[data-test="hotkeys-button"]').should('have.length', 1).and('be.visible').then(($g) => {
        cy.get('[data-test="lobby"]').then(($p) => {
          const g = $g[0].getBoundingClientRect();
          const p = $p[0].getBoundingClientRect();
          expect(g.top - p.top).to.be.within(0, 24);
          expect(p.right - g.right).to.be.within(0, 24);
        });
        cy.get('.game-title').then(($t) => {
          expect(intersects($g[0].getBoundingClientRect(), $t[0].getBoundingClientRect()), 'gear and title').to.eq(false);
        });
      });
      noScroll();
      cy.get('[data-test="hotkeys-button"]').click();
      cy.get('[data-test="hotkey-discard-key"]').should('have.text', 'D');
      cy.get('[data-test="hotkey-call-key"]').should('have.text', 'B');
      noScroll();
    });
  });

  it('a key set on the Game home applies on the table', () => {
    cy.viewport(1280, 800);
    cy.clearLocalStorage();
    cy.visit('/games/bullshit');
    cy.get('[data-test="name"]').type('Alice');
    cy.get('[data-test="hotkeys-button"]').click();
    cy.get('[data-test="hotkey-discard-change"]').click();
    cy.get('body').type('x');
    cy.get('[data-test="hotkey-discard-key"]').should('have.text', 'X');
    cy.get('[data-test="hotkeys-button"]').click();
    cy.get('[data-test="new-game"]').click();
    cy.url({ timeout: 10000 }).should('match', /\/games\/bullshit\/room\/.+/);
    cy.url().then((url) => cy.request('POST', `/api/bullshit/game/${url.split('/room/')[1]}/join`, { name: 'Bob' }));
    cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
    cy.get('[data-test="discard"]', { timeout: 10000 }).should('have.attr', 'aria-keyshortcuts', 'x');
  });
});
