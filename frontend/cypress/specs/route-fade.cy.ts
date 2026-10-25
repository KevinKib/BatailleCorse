// A navigation that lands in the middle of a route fade (select a game, then Back at once) must never
// leave two views fully visible together: the previous fade is cancelled or finished, not mixed in.
const VIEWPORTS: Array<[number, number]> = [[375, 667], [1280, 800]];

// Samples every animation frame: a violation is two routed views both (almost) fully opaque.
function startSampler() {
  cy.window().then((w) => {
    const state = { violations: 0, running: true };
    (w as any).__fadeSampler = state;
    const sample = () => {
      if (!state.running) return;
      const root = w.document.querySelector('.app-root');
      const views = [...(root?.children ?? [])].filter((c) => !c.classList.contains('app-background'));
      const opaque = views.filter((v) => parseFloat(w.getComputedStyle(v).opacity) >= 0.99);
      if (opaque.length > 1) state.violations++;
      w.requestAnimationFrame(sample);
    };
    sample();
  });
}

// Select Bullshit, then Back after a short beat, all inside the page so the timing does not depend on
// Cypress's own command latency. The Back button is clicked while the Game home is still fading in.
function quickRoundTrips(count: number, beatMs: number) {
  cy.window().then((w) => new Cypress.Promise<void>((resolve) => {
    let left = count;
    const click = (selector: string) => w.document.querySelector<HTMLElement>(selector)?.click();
    const next = () => {
      if (left-- === 0) { resolve(); return; }
      click('[data-test="play-bullshit"]');
      w.setTimeout(() => { click('[data-test="back"]'); w.setTimeout(next, beatMs); }, beatMs);
    };
    next();
  }));
}

function stopSamplerAndAssert() {
  cy.window().then((w) => {
    const state = (w as any).__fadeSampler;
    state.running = false;
    expect(state.violations, 'frames with two fully visible views').to.eq(0);
  });
}

VIEWPORTS.forEach(([width, height]) => {
  describe(`Route fade at ${width}px`, () => {
    beforeEach(() => {
      cy.viewport(width, height);
      cy.visit('/');
      cy.get('[data-test="picker-title"]').should('be.visible');
    });

    it('selecting Bullshit then Back at once ends on the picker alone', () => {
      startSampler();
      quickRoundTrips(1, 10);
      cy.location('pathname').should('eq', '/');
      cy.get('[data-test="picker-title"]').should('be.visible');
      cy.get('[data-test="back"]').should('not.exist');
      cy.get('.app-root').children().not('.app-background').should('have.length', 1);
      stopSamplerAndAssert();
      cy.window().then((w) => expect(w.document.documentElement.scrollWidth).to.be.at.most(w.innerWidth));
    });

    it('survives several quick round trips', () => {
      startSampler();
      quickRoundTrips(4, 15);
      cy.location('pathname').should('eq', '/');
      cy.get('[data-test="picker-title"]').should('be.visible');
      cy.get('[data-test="back"]').should('not.exist');
      cy.get('.app-root').children().not('.app-background').should('have.length', 1);
      stopSamplerAndAssert();
    });
  });
});
