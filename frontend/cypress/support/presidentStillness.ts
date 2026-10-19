// The stillness probe of the President table: the boxes of everything that must not move, before and after a change
// of state (a selection, a turn, a pass, a closing badge, a phase). A text, a button or a panel that comes and goes
// is a change of content; the felt, the seats, the pile, my plate, my hand and the buttons keep their places.
//
// Boxes are read from the live DOM. A region that is absent before or after (a seat's fan once the seat is out, the
// pile before the first play) is not compared: only what is on screen on both sides must hold still.

export interface Box { left: number; top: number; width: number; height: number }
export type Snapshot = Record<string, Box>;
type Dim = keyof Box;

/** Regions with one box each. `dims` limits what is compared (a pile of two cards is wider than a pile of one). */
const REGIONS: Array<{ name: string; selector: string; dims?: Dim[] }> = [
  { name: 'felt', selector: '[data-test="president-table"]' },
  { name: 'zone', selector: '[data-test="my-zone"]' },
  { name: 'me.plate', selector: '[data-test="my-zone"] [data-test="seat-label"]' },
  { name: 'hand', selector: '[data-test="hand"]' },
  { name: 'play', selector: '[data-test="play"]' },
  { name: 'pass', selector: '[data-test="pass"]' },
  { name: 'clear', selector: '[data-test="clear"]' },
  { name: 'pile.cards', selector: '[data-test="trick-cards"]', dims: ['top', 'height'] },
  { name: 'pile.card', selector: '[data-test="trick-cards"] .playing_card', dims: ['top', 'height', 'width'] },
];

const PER_SEAT: Array<{ name: string; selector: string }> = [
  { name: 'slot', selector: '' },
  { name: 'label', selector: '[data-test="seat-label"]' },
  { name: 'count', selector: '[data-test="seat-cards"]' },
];

function boxOf(el: Element | null): Box | null {
  if (!el) return null;
  const r = el.getBoundingClientRect();
  return r.width > 0 || r.height > 0 ? { left: r.left, top: r.top, width: r.width, height: r.height } : null;
}

/**
 * What is compared by region. A seat's box is as wide as its widest part (a fan of three backs is narrower than a fan of
 * seven, "1 card" narrower than "12 cards"), centred on the same point: only its height and top are held to 1 px, its
 * name plate is held on every side.
 */
const VERTICAL: Dim[] = ['top', 'height'];
const dimsOf = (name: string): Dim[] => {
  if (name === 'slot' || name === 'count') return VERTICAL;
  return REGIONS.find((r) => r.name === name)?.dims ?? ['left', 'top', 'width', 'height'];
};

/** The boxes of the table right now. */
export function snapshot(): Cypress.Chainable<Snapshot> {
  return cy.document({ log: false }).then((doc) => {
    const out: Snapshot = {};
    for (const region of REGIONS) {
      const box = boxOf(doc.querySelector(region.selector));
      if (box) out[region.name] = box;
    }
    doc.querySelectorAll('[data-test="opponent-seat"]').forEach((slot) => {
      const seat = slot.getAttribute('data-seat');
      for (const part of PER_SEAT) {
        const box = boxOf(part.selector ? slot.querySelector(part.selector) : slot);
        if (box) out[`seat${seat}.${part.name}`] = box;
      }
    });
    return out;
  });
}

/** What moved by more than `tolerance` px between two snapshots, one line each. */
export function shifts(before: Snapshot, after: Snapshot, tolerance = 1): string[] {
  const lines: string[] = [];
  for (const name of Object.keys(before)) {
    const a = after[name];
    if (!a) continue;
    const base = name.replace(/^seat\d+\./, '');
    const dims = dimsOf(base);
    for (const dim of dims) {
      const delta = a[dim] - before[name][dim];
      if (Math.abs(delta) > tolerance) lines.push(`${name}.${dim} ${delta > 0 ? '+' : ''}${delta.toFixed(1)}px (${before[name][dim].toFixed(1)} to ${a[dim].toFixed(1)})`);
    }
  }
  return lines;
}

/** Frames, so that a layout the change triggered has settled. */
function frames(): Cypress.Chainable<unknown> {
  return cy.window({ log: false }).then((win) => new Promise<void>((resolve) => {
    win.requestAnimationFrame(() => win.requestAnimationFrame(() => win.requestAnimationFrame(() => resolve())));
  }));
}

/** Waits until two snapshots a few frames apart are the same (the hand measures itself once it is on screen). */
export function waitStill(tries = 30): Cypress.Chainable<unknown> {
  const poll = (previous: string | null, left: number): Cypress.Chainable<unknown> =>
    snapshot().then((snap) => {
      const key = JSON.stringify(snap);
      if (key === previous || left <= 0) return undefined;
      return frames().then(() => poll(key, left - 1));
    });
  return poll(null, tries);
}

/** Every change of the running test that moved something, so that one run reports them all. */
let moves: string[] = [];

/**
 * Runs `change` (which queues Cypress commands), waits for `settled` (assertions that say the change is on screen),
 * then records what the probe watches moved by more than 1 px. `expectNothingMoved` ends the test.
 */
export function stillAcross(label: string, change: () => void, settled: () => void, tolerance = 1) {
  waitStill();
  snapshot().then((before) => {
    change();
    settled();
    waitStill();
    snapshot().then((after) => {
      const moved = shifts(before, after, tolerance);
      if (moved.length > 0) moves.push(`${label}: ${moved.join(', ')}`);
    });
  });
}

/** Starts a test with nothing recorded. */
export function resetMoves() { moves = []; }

/** Ends a test: nothing may have moved at any of its changes (all of them are listed when something did). */
export function expectNothingMoved() {
  cy.then(() => {
    const found = moves;
    moves = [];
    expect(found, 'what moved, by change of state').to.deep.eq([]);
  });
}
