// The two Bullshit flights frozen mid-flight: the copies' rectangles, opacity, level and picture, and a screenshot, at
// both widths. Run with `--env tag=before` on one build and `--env tag=after` on another, then compare the JSON files in
// docs/screenshots/bullshit-shared-animation/ (the screenshots are copied from cypress/screenshots next to them).
// Without a tag it only checks that the copies exist while frozen, so it also stands as a regression test.
const GHOSTS = '[data-test="flying-card"], [data-test="flying-pile"], [data-test="motion-card"]';
const OUT = 'docs/screenshots/bullshit-shared-animation';
// The same hand whatever the deal, so the pictures can be compared.
const RANKS = ['ACE', 'KING', 'QUEEN', 'JACK', '10', '9', '8', '7', '6', '5', '4', '3', '2'];
const SUITS = ['HEART', 'SPADE', 'DIAMOND', 'CLUB'];
const FIXED = RANKS.flatMap(rank => SUITS.map(suit => [rank, suit]));

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
        picture: (el.matches('img') ? el : el.querySelector('img'))?.getAttribute('src') ?? null,
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
      cy.wait(1500); // the late state updates of the start are in: the fixed hand below is the last word
      cy.window().then((w) => {
        const pinia = (w.document.querySelector('#app') as any).__vue_app__.config.globalProperties.$pinia;
        const store = pinia._s.get('bullshit-store');
        const myHand = store.game.myHand.map((_: unknown, i: number) => {
          const [rank, suit] = FIXED[i];
          return { rank, suit, name: `${suit}_${rank}` };
        });
        store.applyEvent({ type: 'state-update', state: { ...store.game, myHand } });
      });
    });

    it('freezes the discard of two cards to the pile', () => {
      cy.clock(); // the layer drops a copy that never finishes after a safety delay: stop that clock, the copies stay
      freezeCopiesAt(250);
      cy.get('[data-test="hand-card-0"]').click({ force: true });
      cy.get('[data-test="hand-card-1"]').click({ force: true });
      cy.get('[data-test="discard"]').click();
      record('discard', width);
    });

    // The hand is sent in an order that is not the display order (reversed): each copy must still start from the place
    // of the card that was played, and show its face (it used to take those of the card at the same index in the sent hand).
    it('starts each copy of a discard from the place of the played card, whatever order the hand was sent in', () => {
      cy.window().then((w) => {
        const pinia = (w.document.querySelector('#app') as any).__vue_app__.config.globalProperties.$pinia;
        const store = pinia._s.get('bullshit-store');
        store.applyEvent({ type: 'state-update', state: { ...store.game, myHand: [...store.game.myHand].reverse() } });
      });
      cy.clock();
      freezeCopiesAt(0);
      const places: { left: number; top: number; picture: string | null }[] = [];
      const place = (n: number) => cy.get(`[data-test="hand-card-${n}"]`).then(($el) => {
        const r = $el[0].getBoundingClientRect();
        places.push({ left: r.left, top: r.top, picture: $el.find('img').attr('src') ?? null });
      });
      // Two cards picked from the left of the fan, in a non-trivial order: the second place first.
      cy.get('[data-test="hand-card-1"]').click({ force: true });
      cy.get('[data-test="hand-card-0"]').click({ force: true });
      place(1);
      place(0);
      cy.get('[data-test="discard"]').click();
      cy.get(GHOSTS).should('have.length', 2);
      // `--env tag=before|after`: a screenshot to compare (copied from cypress/screenshots to docs/screenshots/bullshit-discard-source/).
      const tag = Cypress.env('tag') as string | undefined;
      if (tag) cy.screenshot(`${tag}-${width}-discard-source`, { capture: 'viewport', overwrite: true });
      cy.document().then((doc) => {
        const copies = Array.from(doc.querySelectorAll(GHOSTS)).map((el) => {
          const r = el.getBoundingClientRect();
          return { left: r.left, top: r.top, picture: (el.matches('img') ? el : el.querySelector('img'))?.getAttribute('src') ?? null };
        });
        // At the start of the flight a copy sits exactly on its hand place and shows the same picture.
        copies.sort((a, b) => a.left - b.left);
        const expected = [...places].sort((a, b) => a.left - b.left);
        copies.forEach((c, i) => {
          expect(c.picture, `picture of copy ${i}`).to.equal(expected[i].picture);
          expect(c.left, `left of copy ${i}`).to.be.closeTo(expected[i].left, 1);
          // A picked card is lifted (and a hovered one too), so the vertical position is only roughly the same.
          expect(c.top, `top of copy ${i}`).to.be.closeTo(expected[i].top, 12);
        });
      });
    });

    it('freezes the pile on its way to the taker', () => {
      cy.clock();
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
      cy.tick(3000); // the verdict goes away
      record('take', width);
    });
  });
});
