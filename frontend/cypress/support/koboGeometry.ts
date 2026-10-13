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
      box('my zone', doc.querySelector('[data-test="my-zone"]')!),
      box('footer', doc.querySelector('[data-test="table-footer"]')!),
    ].filter((z) => z.right > z.left && z.bottom > z.top);
    for (let i = 0; i < zones.length; i++) {
      for (let j = i + 1; j < zones.length; j++) {
        expect(overlap(zones[i], zones[j]), `${zones[i].name} / ${zones[j].name}`).to.eq(false);
      }
    }
    // The table frame itself stays clear of the zones around it.
    for (const other of ['.top-bar', '[data-test="my-zone"]', '[data-test="table-footer"]']) {
      expect(overlap(frame, box(other, doc.querySelector(other)!)), `table / ${other}`).to.eq(false);
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
