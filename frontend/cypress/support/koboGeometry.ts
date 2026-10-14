// Geometry checks of the Kobo table shared by its specs.
import { noHorizontalScroll } from './koboRoom';

export interface Box { name: string; left: number; top: number; right: number; bottom: number }

const box = (name: string, el: Element, clip?: Box): Box => {
  const r = el.getBoundingClientRect();
  const b = { name, left: r.left, top: r.top, right: r.right, bottom: r.bottom };
  return clip
    ? { ...b, left: Math.max(b.left, clip.left), top: Math.max(b.top, clip.top), right: Math.min(b.right, clip.right), bottom: Math.min(b.bottom, clip.bottom) }
    : b;
};
const overlap = (a: Box, b: Box) =>
  Math.min(a.right, b.right) - Math.max(a.left, b.left) > 1 && Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top) > 1;

/** No two of the main zones may overlap (what a scrolling frame clips is measured clipped). */
export function expectNoOverlap() {
  cy.document().then((doc) => {
    const q = (sel: string) => Array.from(doc.querySelectorAll(sel));
    const frame = box('table', doc.querySelector('[data-test="kobo-table"]')!);
    const zones: Box[] = [
      box('top bar', doc.querySelector('.top-bar')!),
      box('piles', doc.querySelector('[data-test="kobo-piles"]')!, frame),
      ...q('[data-test="opponent-seat"]').map((el, i) => box(`opponent ${i + 1}`, el, frame)),
      // My zone gives way to the score sheet at the round end.
      ...q('[data-test="my-zone"]').map((el) => box('my zone', el)),
      box('footer', doc.querySelector('[data-test="table-footer"]')!),
    ].filter((z) => z.right > z.left && z.bottom > z.top);
    for (let i = 0; i < zones.length; i++) {
      for (let j = i + 1; j < zones.length; j++) {
        expect(overlap(zones[i], zones[j]), `${zones[i].name} / ${zones[j].name}`).to.eq(false);
      }
    }
    // The table frame itself stays clear of the zones around it.
    for (const other of ['.top-bar', '[data-test="my-zone"]', '[data-test="table-footer"]']) {
      const el = doc.querySelector(other);
      if (!el) continue;
      expect(overlap(frame, box(other, el)), `table / ${other}`).to.eq(false);
    }
  });
}

export function everySlotAtLeast44() {
  cy.get('button[data-test^="slot-"]').should('have.length.greaterThan', 0).each(($b) => {
    const r = $b[0].getBoundingClientRect();
    expect(Math.round(r.width), $b.attr('data-test') + ' width').to.be.at.least(44);
    expect(Math.round(r.height), $b.attr('data-test') + ' height').to.be.at.least(44);
  });
}

export function tableDoesNotScrollSideways() {
  cy.get('[data-test="kobo-table"]').then(($t) => {
    expect($t[0].scrollWidth, 'table scrollWidth').to.be.at.most($t[0].clientWidth);
  });
  noHorizontalScroll();
}

export interface TableMetrics {
  label: string;
  viewport: string;
  /** Height of the felt frame that holds the piles and the opponents (the part that scrolls on a phone). */
  frameHeight: number;
  frameScrollHeight: number;
  barHeight: number;
  barBottom: number;
  myZoneHeight: number;
  footerHeight: number;
  /** Opponent seats whose whole tableau is inside the frame without scrolling. */
  opponentsFullyVisible: number;
  opponents: number;
  smallestButton: number;
}

/**
 * What the table shows at this moment, for the space budget of a viewport. When the run is given
 * `KOBO_METRICS_FILE` the numbers are appended there (one JSON per line), so a report can quote them.
 */
export function measureTable(label: string) {
  return cy.document().then((doc) => {
    const win = doc.defaultView!;
    const rect = (sel: string) => doc.querySelector(sel)!.getBoundingClientRect();
    const frameEl = doc.querySelector('[data-test="kobo-table"]') as HTMLElement;
    const frame = frameEl.getBoundingClientRect();
    const seats = Array.from(doc.querySelectorAll('[data-test="opponent-seat"]'));
    const inside = seats.filter((el) => {
      const r = el.getBoundingClientRect();
      return r.top >= frame.top - 1 && r.bottom <= frame.bottom + 1 && r.left >= frame.left - 1 && r.right <= frame.right + 1;
    });
    const buttons = Array.from(doc.querySelectorAll('[data-test="kobo-actions"] button'));
    const metrics: TableMetrics = {
      label,
      viewport: `${win.innerWidth}x${win.innerHeight}`,
      frameHeight: Math.round(frame.height),
      frameScrollHeight: frameEl.scrollHeight,
      barHeight: Math.round(rect('[data-test="kobo-actions"]').height),
      barBottom: Math.round(rect('[data-test="kobo-actions"]').bottom),
      myZoneHeight: Math.round(rect('[data-test="my-zone"]').height),
      footerHeight: Math.round(rect('[data-test="table-footer"]').height),
      opponentsFullyVisible: inside.length,
      opponents: seats.length,
      smallestButton: buttons.length ? Math.round(Math.min(...buttons.map((b) => b.getBoundingClientRect().height))) : 0,
    };
    const file = Cypress.env('KOBO_METRICS_FILE') as string | undefined;
    if (file) return cy.writeFile(file, JSON.stringify(metrics) + '\n', { flag: 'a+' }).then(() => metrics);
    return metrics;
  });
}

/** The action bar is entirely on screen, above the footer, and every button of it is a 44 px target. */
export function actionBarIsReachable() {
  cy.window().then((win) => {
    const bar = win.document.querySelector('[data-test="kobo-actions"]')!.getBoundingClientRect();
    const footer = win.document.querySelector('[data-test="table-footer"]')!.getBoundingClientRect();
    expect(bar.top, 'bar top').to.be.at.least(0);
    expect(bar.bottom, 'bar bottom').to.be.at.most(win.innerHeight);
    expect(bar.bottom, 'bar above the footer').to.be.at.most(footer.top + 1);
  });
  cy.get('[data-test="kobo-actions"] button').each(($b) => {
    const r = $b[0].getBoundingClientRect();
    expect(Math.round(r.height), 'button height').to.be.at.least(44);
    expect(Math.round(r.width), 'button width').to.be.at.least(44);
  });
}
