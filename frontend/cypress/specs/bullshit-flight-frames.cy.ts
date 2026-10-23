// The two Bullshit flights frozen mid-flight: the copies' rectangles, opacity, level and picture, and a screenshot, at
// both widths. Run with `--env tag=before` on one build and `--env tag=after` on another, then compare the JSON files in
// docs/screenshots/bullshit-shared-animation/ (the screenshots are copied from cypress/screenshots next to them).
// Without a tag it only checks that the copies exist while frozen, so it also stands as a regression test.
const GHOSTS = '[data-test="flying-card"], [data-test="flying-pile"], [data-test="motion-card"]';
const OUT = 'docs/screenshots/bullshit-shared-animation';
// The same hand whatever the deal, so the pictures can be compared.
const FIXED = [
  ['ACE', 'HEART'], ['KING', 'SPADE'], ['QUEEN', 'DIAMOND'], ['JACK', 'CLUB'], ['TEN', 'HEART'], ['NINE', 'SPADE'],
  ['EIGHT', 'DIAMOND'], ['SEVEN', 'CLUB'], ['SIX', 'HEART'], ['FIVE', 'SPADE'],
];

function startTwoPlayerGame() {
  cy.visit('/games');
  cy.get('[data-test="play-bullshit"]').click();
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/games\/bullshit\/room\/.+/);
  cy.get('[data-test="start-panel"]').should('not.exist');
  cy.url().then((url) => cy.request('POST', `/api/bullshit/game/${url.split('/room/')[1]}/join`, { name: 'Bob' }));
  cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
  cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
}

// Every copy that reaches the Web Animations API is paused at `at` ms of its timeline, so it stays on screen.
function freezeCopiesAt(at: number) {
  cy.window().then((win) => {
    const original = win.Element.prototype.animate;
    win.Element.prototype.animate = function (this: Element, ...args: Parameters<Element['animate']>) {
      const animation = original.apply(this, args);
      if (this.matches(GHOSTS)) { animation.pause(); animation.currentTime = at; }
      return animation;
    } as typeof original;
  });
}

function level(el: Element): string {
  for (let node: Element | null = el; node; node = node.parentElement) {
    const z = (node as HTMLElement).style?.zIndex;
    if (z) return z;
  }
  return '';
}

function record(name: string, width: number) {
  const tag = Cypress.env('tag') as string | undefined;
  cy.get(GHOSTS).should('have.length.at.least', 1);
  cy.document().then((doc) => {
    const copies = Array.from(doc.querySelectorAll(GHOSTS)).map((el) => {
      const r = el.getBoundingClientRect();
      const round = (n: number) => Math.round(n * 10) / 10;
      return {
        left: round(r.left), top: round(r.top), width: round(r.width), height: round(r.height),
        opacity: Number(getComputedStyle(el).opacity), level: level(el),
        picture: el.querySelector('img')?.getAttribute('src') ?? null,
      };
    });
    if (tag) cy.writeFile(`../${OUT}/${tag}-${width}-${name}.json`, JSON.stringify(copies, null, 2));
  });
  if (tag) cy.screenshot(`${tag}-${width}-${name}`, { capture: 'viewport', overwrite: true });
}

[{ width: 375, height: 667 }, { width: 1280, height: 800 }].forEach(({ width, height }) => {
  describe(`Bullshit flights frozen mid-flight at ${width}px`, () => {
    beforeEach(() => {
      cy.viewport(width, height);
      startTwoPlayerGame();
      cy.window().then((w) => {
        const pinia = (w.document.querySelector('#app') as any).__vue_app__.config.globalProperties.$pinia;
        const store = pinia._s.get('bullshit-store');
        const myHand = store.game.myHand.map((_: unknown, i: number) => {
          const [rank, suit] = FIXED[i % FIXED.length];
          return { rank, suit, name: `${suit}_${rank}` };
        });
        store.applyEvent({ type: 'state-update', state: { ...store.game, myHand } });
      });
    });

    it('freezes the discard of two cards to the pile', () => {
      freezeCopiesAt(250);
      cy.get('[data-test="hand-card-0"]').click({ force: true });
      cy.get('[data-test="hand-card-1"]').click({ force: true });
      cy.get('[data-test="discard"]').click();
      record('discard', width);
    });

    it('freezes the pile on its way to the taker', () => {
      freezeCopiesAt(300);
      cy.window().then((w) => {
        const pinia = (w.document.querySelector('#app') as any).__vue_app__.config.globalProperties.$pinia;
        const store = pinia._s.get('bullshit-store');
        store.applyEvent({
          type: 'event', eventType: 'CALL_BULLSHIT',
          eventData: {
            callerSeat: 0, claimantSeat: 1, truthful: false, pickerSeat: 1,
            revealedCards: store.game.myHand.slice(0, 4),
          },
        });
      });
      cy.get('[data-test="reveal"]').should('exist');
      cy.get('[data-test="reveal"]', { timeout: 6000 }).should('not.exist');
      record('take', width);
    });
  });
});
