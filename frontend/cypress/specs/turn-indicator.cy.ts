// The "Your turn" cue is one shared component: Bataille Corse and Bullshit show it with the very same
// text, look and accessibility, measured here on both tables at phone and desktop widths.
const VIEWPORTS: Array<[number, number]> = [[375, 667], [1280, 800]];

interface HintLook {
  text: string; fontFamily: string; fontSize: string; fontWeight: string; lineHeight: string;
  letterSpacing: string; textTransform: string; color: string; height: number;
  dotWidth: string; dotHeight: string; dotColor: string; dotRadius: string;
  regionRole: string | null; regionLive: string | null; dotHidden: string | null;
}

function lookOf($hint: JQuery<HTMLElement>): HintLook {
  const el = $hint[0];
  const style = getComputedStyle(el);
  const dot = el.querySelector('.turn-hint__dot') as HTMLElement;
  const dotStyle = getComputedStyle(dot);
  const region = el.closest('[data-test="turn-indicator"]') as HTMLElement;
  return {
    text: el.textContent!.trim(), fontFamily: style.fontFamily, fontSize: style.fontSize, fontWeight: style.fontWeight,
    lineHeight: style.lineHeight, letterSpacing: style.letterSpacing, textTransform: style.textTransform, color: style.color,
    height: Math.round(el.getBoundingClientRect().height),
    dotWidth: dotStyle.width, dotHeight: dotStyle.height, dotColor: dotStyle.backgroundColor, dotRadius: dotStyle.borderRadius,
    regionRole: region.getAttribute('role'), regionLive: region.getAttribute('aria-live'), dotHidden: dot.getAttribute('aria-hidden'),
  };
}

function startBullshitGame() {
  // Enter through the menu so the WebSocket is connected before we create (see bullshit-table-and-lobby).
  cy.visit('/');
  cy.get('[data-test="play-bullshit"]').click();
  cy.get('[data-test="name"]').type('Alice');
  cy.get('[data-test="new-game"]').click();
  cy.url({ timeout: 10000 }).should('match', /\/bullshit\/room\/.+/);
  cy.url().then((url) => cy.request('POST', `/api/bullshit/game/${url.split('/room/')[1]}/join`, { name: 'Bob' }));
  cy.get('[data-test="start"]', { timeout: 10000 }).should('not.be.disabled').click();
  cy.get('[data-test="hand-card-0"]', { timeout: 10000 }).should('exist');
}

VIEWPORTS.forEach(([width, height]) => {
  describe(`Turn indicator at ${width}px`, () => {
    let batailleCorseLook: HintLook;

    beforeEach(() => cy.viewport(width, height));

    it('Bataille Corse: shows a one-time "Your turn" hint at the start of the player turn', () => {
      cy.createGame();
      // On a fresh game it is player 0's turn, so the one-time onboarding hint appears and stays for
      // the duration of that first turn (no Send issued here).
      cy.get('[data-test="turn-hint"]', { timeout: 10000 }).should('be.visible').and('have.text', 'Your turn')
        .then(($hint) => { batailleCorseLook = lookOf($hint); });
      cy.get('[data-test="turn-indicator"]').should('have.attr', 'role', 'status');
      cy.window().then((w) => expect(w.document.documentElement.scrollWidth).to.be.at.most(w.innerWidth));
    });

    it('Bullshit: shows the very same cue, measured identical to Bataille Corse', () => {
      startBullshitGame();
      cy.get('[data-test="turn-hint"]').should('be.visible').and('have.text', 'Your turn').then(($hint) => {
        const look = lookOf($hint);
        expect(look).to.deep.equal(batailleCorseLook);
        expect(look.regionRole).to.eq('status');
        expect(look.dotHidden).to.eq('true');
        expect(look.dotWidth).to.eq('6px');
      });
      cy.window().then((w) => expect(w.document.documentElement.scrollWidth).to.be.at.most(w.innerWidth));
    });
  });
});
