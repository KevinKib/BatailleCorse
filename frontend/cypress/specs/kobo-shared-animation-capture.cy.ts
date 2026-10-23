// Before/after captures of Kobo's travelling cards for the PR that moved the screen onto the shared animation module
// (docs/screenshots/kobo-shared-animation/). Skipped in a normal run:
//   npx cypress run --env capture=before --spec cypress/specs/kobo-shared-animation-capture.cy.ts \
//     --config screenshotsFolder=../docs/screenshots/kobo-shared-animation/before
// Each moment (my draw, my swap, a bot's draw, a bot's discard) is frozen mid-flight: every animation of the layer is
// paused 40% into its run, then the layer's DOM, every animation's keyframes and timing, the computed transform and
// opacity of every copy, and a screenshot are written down. Running it on `main` then on the branch and diffing the
// JSON files shows whether anything visible changed. The table has no light or dark theme, so there is one set.
import { awaitState, startKoboTable } from '../support/koboRoom';

const phase = Cypress.env('capture') as string | undefined;
const describeIf = phase ? describe : describe.skip;
const OUT = '../docs/screenshots/kobo-shared-animation';
const layer = '[data-test="motion-layer"]';

interface Frozen { layerHtml: string; animations: unknown[]; computed: unknown[]; marks: unknown[]; arrows: number }
interface CaptureWindow extends Window {
  __captures: Record<string, Frozen>;
  __paused: Animation[];
  __label?: string;
}

/** Freezes the layer mid-flight, the first time a copy is added after `__label` is set. */
function installCapture() {
  cy.window({ log: false }).then((win) => {
    const w = win as unknown as CaptureWindow;
    w.__captures = {};
    w.__paused = [];
    const capture = (label: string) => {
      const doc = win.document;
      const layerEl = doc.querySelector(layer) as HTMLElement;
      const animations = doc.getAnimations().filter((a) => {
        const target = (a.effect as KeyframeEffect | null)?.target;
        return target && layerEl.contains(target);
      });
      const summary = (a: Animation) => {
        const effect = a.effect as KeyframeEffect;
        const timing = effect.getTiming();
        const el = effect.target as HTMLElement;
        return {
          target: el.dataset?.test ?? el.tagName,
          timing: { delay: timing.delay, duration: timing.duration, fill: timing.fill, easing: timing.easing },
          keyframes: effect.getKeyframes(),
        };
      };
      const info = animations.map(summary);
      animations.forEach((a) => {
        const timing = (a.effect as KeyframeEffect).getTiming();
        a.pause();
        a.currentTime = Number(timing.delay) + 0.4 * Number(timing.duration);
      });
      w.__paused = animations;
      w.__captures[label] = {
        layerHtml: layerEl.outerHTML,
        animations: info,
        computed: animations.map((a) => {
          const el = (a.effect as KeyframeEffect).target as HTMLElement;
          const style = win.getComputedStyle(el);
          return { target: el.dataset?.test ?? el.tagName, transform: style.transform, opacity: style.opacity };
        }),
        marks: Array.from(doc.querySelectorAll('[data-test="motion-mark"]')).map((m) => {
          const r = m.getBoundingClientRect();
          return { role: (m as HTMLElement).dataset.role, still: (m as HTMLElement).dataset.still, left: r.left, top: r.top, width: r.width, height: r.height };
        }),
        arrows: doc.querySelectorAll('[data-test="motion-arrow"]').length,
      };
    };
    const observer = new win.MutationObserver((records) => {
      for (const record of records) {
        record.addedNodes.forEach((node) => {
          if (!(node instanceof win.HTMLElement) || node.dataset.test !== 'motion-card' || !w.__label) return;
          const label = w.__label;
          w.__label = undefined;
          // After the layer has launched its animations (they start in the tick that follows the patch).
          win.requestAnimationFrame(() => win.requestAnimationFrame(() => capture(label)));
        });
      }
    });
    observer.observe(win.document.body, { childList: true, subtree: true });
  });
}

function frozen(name: string, label: string, act: () => void) {
  cy.window({ log: false }).then((win) => { (win as unknown as CaptureWindow).__label = label; });
  act();
  cy.window().should((win) => expect((win as unknown as CaptureWindow).__captures[label], `${label} frozen`).to.exist);
  cy.screenshot(`${name}-${label}`, { capture: 'viewport', overwrite: true });
  cy.window({ log: false }).then((win) => {
    const w = win as unknown as CaptureWindow;
    cy.writeFile(`${OUT}/${phase}/${name}-${label}.json`, w.__captures[label]);
    w.__paused.forEach((a) => a.finish());
  });
  // The discard pile may be empty, so only the copies are waited for.
  cy.get('[data-test="motion-card"]').should('not.exist');
}

describeIf('Kobo shared animation captures', () => {
  [{ width: 375, height: 667 }, { width: 1280, height: 800 }].forEach(({ width, height }) => {
    it(`freezes my draw, my swap and a bot's two moves at ${width}x${height}`, () => {
      cy.viewport(width, height);
      const name = `${width}x${height}`;
      // I hold 4H QH JH 10H, Bob 9H 5H 8H 6H, Carol 7H 3H 2H AH. I draw 2S, then Bob draws 4S.
      const deal = ['4H', 'QH', 'JH', '10H', '9H', '5H', '8H', '6H', '7H', '3H', '2H', 'AH', '2S', '4S'];
      startKoboTable(3, deal).then(({ guests }) => {
        const bob = guests[0];
        installCapture();
        cy.get('[data-test="action-ready"]').click();
        cy.then(() => guests.forEach((g) => g.publish('/app/kobo/ready', {})));
        cy.get('[data-test="draw-pile"]').should('match', 'button').and('not.be.disabled');

        frozen(name, 'my-draw', () => { cy.get('[data-test="draw-pile"]').click(); });
        cy.get('[data-test="drawn-zone"] img').should('have.attr', 'alt', '2 of spade');
        frozen(name, 'my-swap', () => { cy.get('[data-test="slot-0-1"]').click(); });
        cy.get('[data-test="action-endTurn"]').click();

        awaitState(bob, 'bob draw step', (s) => s.currentSeat === bob.seat && s.phase === 'DRAW');
        frozen(name, 'bob-draw', () => { cy.then(() => bob.publish('/app/kobo/draw', {})); });
        awaitState(bob, 'the drawn card', (s) => s.phase === 'DECISION' && s.drawnCard !== null);
        frozen(name, 'bob-discard', () => { cy.then(() => bob.publish('/app/kobo/discardDrawn', {})); });
      });
    });
  });
});
