// Kobo's travelling cards, end to end. The animation is a picture over a table whose state is already right, so
// this spec checks three things: a card flies and lands (a copy exists, then is gone) within the limit; a click
// never waits for it (a one-click match is sent from inside a flight, a button of the score sheet works while the
// sheet is still rising); and `prefers-reduced-motion: reduce` shows the state at once with nothing moving.
// No fixed wait anywhere: flights are observed from the page (a MutationObserver records every copy as it is added),
// so a short flight cannot be missed by a slow poll. Two rooms only.
import { awaitState, DECK_CODES, scriptedDiscardTurn, startKoboTable } from '../support/koboRoom';

const slot = (seat: number, n: number) => `[data-test="slot-${seat}-${n}"]`;
const logLine = '[data-test="event-log-line"]';
const layer = '[data-test="motion-layer"]';
const sheet = '[data-test="score-sheet"]';
const MAX_MS = 700;

interface Seen {
  kind: string | null;
  layerPointerEvents: string;
  layerHiddenFromAT: string | null;
  left: number;
  right: number;
  arrows: number;
}
interface Spy {
  __seen: Seen[];
  __timings: { duration: number; delay: number }[];
  __live: number;
  __clickedInFlight: boolean | null;
  __clickWhen?: { slot: string; discardAlt: string };
}

/** Records every flying copy as the page adds it, and the timing of every animation the layer starts. */
function spyOnMotion() {
  cy.window({ log: false }).then((win) => {
    const w = win as unknown as Window & Spy;
    w.__seen = [];
    w.__timings = [];
    w.__live = 0;
    w.__clickedInFlight = null;
    const original = win.Element.prototype.animate;
    win.Element.prototype.animate = function (this: Element, frames: any, options: any) {
      if (this.closest?.('[data-test="motion-layer"]') && options && typeof options === 'object') {
        w.__timings.push({ duration: Number(options.duration), delay: Number(options.delay ?? 0) });
      }
      return original.call(this, frames, options);
    } as typeof original;
    const observer = new win.MutationObserver((records) => {
      for (const record of records) {
        record.addedNodes.forEach((node) => {
          if (!(node instanceof win.HTMLElement) || node.dataset.test !== 'motion-card') return;
          const layerEl = win.document.querySelector(layer) as HTMLElement;
          const r = node.getBoundingClientRect();
          w.__seen.push({
            kind: node.dataset.kind ?? null,
            layerPointerEvents: win.getComputedStyle(layerEl).pointerEvents,
            layerHiddenFromAT: layerEl.getAttribute('aria-hidden'),
            left: r.left, right: r.right,
            arrows: win.document.querySelectorAll('[data-test="motion-arrow"]').length,
          });
        });
      }
    });
    observer.observe(win.document.body, { childList: true, subtree: true });
    // A match clicked from inside a flight: as soon as a copy is on the table and the pile shows the wanted card
    // (and the one-click match is armed), click my card in the same frame.
    const poll = () => {
      const when = w.__clickWhen;
      const button = when ? (win.document.querySelector(when.slot) as HTMLButtonElement | null) : null;
      const top = win.document.querySelector('[data-test="discard-pile"] img') as HTMLImageElement | null;
      const flying = win.document.querySelector('[data-test="motion-card"]') !== null;
      if (when && button && flying && top?.alt === when.discardAlt && button.dataset.match === 'true' && !button.disabled) {
        w.__clickWhen = undefined;
        w.__clickedInFlight = true;
        button.click();
      }
      win.requestAnimationFrame(poll);
    };
    win.requestAnimationFrame(poll);
  });
}

function emulateReducedMotion(reduce: boolean) {
  cy.then(() => Cypress.automation('remote:debugger:protocol', {
    command: 'Emulation.setEmulatedMedia',
    params: { features: [{ name: 'prefers-reduced-motion', value: reduce ? 'reduce' : 'no-preference' }] },
  }));
}

function memoriseBoth(bob: { publish: (d: string, b: Record<string, unknown>) => void }) {
  cy.get('[data-test="action-ready"]').click();
  cy.then(() => bob.publish('/app/kobo/ready', {}));
  cy.get('[data-test="draw-pile"]').should('match', 'button').and('not.be.disabled');
}

describe('Kobo animations', () => {
  afterEach(() => { emulateReducedMotion(false); });

  it('flies the cards of every move, opponents included, without ever making a click wait', () => {
    cy.viewport(375, 667);
    // I hold 4H QH JH 10H, Bob 9H 5H 8H 6H. I draw 2S and discard it; Bob then draws 4S and discards it, the
    // card my 4H matches.
    startKoboTable(2, ['4H', 'QH', 'JH', '10H', '9H', '5H', '8H', '6H', '2S', '4S']).then(({ guests }) => {
      const bob = guests[0] as typeof guests[0] & { seat: number };
      spyOnMotion();
      memoriseBoth(bob);

      // My draw: a copy of the card flies, then it is gone, and the drawn card is there for good.
      cy.get('[data-test="draw-pile"]').click();
      cy.window().should((win) => expect((win as unknown as Spy).__seen.length, 'a copy flew').to.be.greaterThan(0));
      cy.get('[data-test="motion-card"]').should('not.exist');
      cy.get('[data-test="drawn-zone"] img').should('have.attr', 'alt', '2 of spade').and('have.css', 'opacity', '1');
      cy.window().then((win) => {
        const mine = (win as unknown as Spy).__seen[0];
        expect(mine.arrows, 'no arrow on my own move').to.eq(0);
      });

      cy.get('[data-test="discard-pile"]').click();
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '2 of spade').and('have.css', 'opacity', '1');
      cy.get('[data-test="action-endTurn"]').click();
      // Once my own command has settled, a click on a card is a one-click match again.
      cy.get(slot(0, 0)).should('have.attr', 'data-match', 'true');

      // Bob's turn is drawn too (that is where reading the table is hardest), and my click is sent from inside
      // the flight of his discard, not after it.
      cy.window({ log: false }).then((win) => {
        const w = win as unknown as Spy;
        w.__seen.length = 0;
        w.__clickWhen = { slot: slot(0, 0), discardAlt: '4 of spade' };
      });
      scriptedDiscardTurn(bob);
      cy.get(logLine).should('have.text', 'You matched card 1 (4 of heart)');
      cy.get(`[data-test="slot-empty-0-0"]`).should('exist');
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '4 of heart');
      cy.get('[data-test="motion-card"]').should('not.exist');
      cy.get('[data-test="discard-pile"] img').should('have.css', 'opacity', '1');

      cy.window().then((win) => {
        const w = win as unknown as Spy;
        expect(w.__clickedInFlight, 'the match was clicked while a card was flying').to.eq(true);
        const bobs = w.__seen.filter((s) => s.arrows > 0);
        expect(bobs.length, "the opponent's moves came with a faint arrow").to.be.greaterThan(0);
        for (const s of w.__seen) {
          expect(s.layerPointerEvents, 'the layer takes no pointer event').to.eq('none');
          expect(s.layerHiddenFromAT, 'the layer is hidden from assistive technology').to.eq('true');
          expect(s.left, 'starts inside the screen').to.be.at.least(0);
          expect(s.right, 'starts inside the screen').to.be.at.most(win.innerWidth);
        }
        expect(w.__timings.length).to.be.greaterThan(0);
        for (const t of w.__timings) expect(t.delay + t.duration, 'a movement lasts under 700 ms').to.be.lessThan(MAX_MS);
      });
      cy.document().then((doc) => {
        expect(doc.documentElement.scrollWidth, 'page width').to.be.at.most(doc.documentElement.clientWidth);
      });
    });
  });

  it('moves nothing under reduced motion, and the score sheet rises without blocking its buttons', () => {
    cy.viewport(375, 667);
    // Alice holds KH KD AH AD (2 points) and announces Kobo on her first turn; Bob plays his last turn.
    const alice = ['KH', 'KD', 'AH', 'AD'];
    const bobs = DECK_CODES.filter((c) => !alice.includes(c) && !['2C', '3C'].includes(c) && !c.endsWith('C')).slice(0, 4);
    startKoboTable(2, [...alice, ...bobs, '2C', '3C']).then(({ guests }) => {
      const bob = guests[0] as typeof guests[0] & { seat: number };
      emulateReducedMotion(true);
      spyOnMotion();
      memoriseBoth(bob);

      // The state is shown at once and nothing flies.
      cy.get('[data-test="draw-pile"]').click();
      cy.get('[data-test="drawn-zone"] img').should('have.attr', 'alt', '2 of club').and('be.visible');
      cy.get('[data-test="discard-pile"]').click();
      cy.get('[data-test="discard-pile"] img').should('have.attr', 'alt', '2 of club');
      cy.get('[data-test="action-announce"]').click();
      scriptedDiscardTurn(bob);
      awaitState(bob, 'the gift step', (s) => s.phase === 'GIFT');
      cy.get(sheet).should('be.visible');
      cy.window().then((win) => {
        expect((win as unknown as Spy).__seen, 'no card flew under reduced motion').to.have.length(0);
        expect((win as unknown as Spy).__timings, 'no animation was started by the layer').to.have.length(0);
      });
      cy.get(sheet).should('have.css', 'animation-name', 'none');

      // Motion allowed again: the sheet rises (about 250 ms, soft easing) and its buttons answer at once.
      emulateReducedMotion(false);
      cy.get(sheet).should(($s) => {
        const style = getComputedStyle($s[0]);
        expect(style.animationName).to.contain('sheet-rise');
        expect(style.animationDuration).to.eq('0.25s');
      });
      cy.get('[data-test^="give-"]').first().click({ force: true });
      cy.get('[data-test="action-next-round"]').should('exist');
      cy.get('[data-test="sheet-title"]').should('be.visible');
    });
  });
});
