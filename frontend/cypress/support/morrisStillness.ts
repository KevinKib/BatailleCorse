// The stillness probe of the Morris table (same rule as the President table): a text, a button or a panel that comes and
// goes never moves or resizes the board, the player tags, the draw button or the Back row, nor the screen itself.
// Boxes are read from the live DOM. A region absent before or after (the draw button during the placement phase) is not
// compared: only what is on screen on both sides must hold still.

export interface Box { left: number; top: number; width: number; height: number }
export type Snapshot = Record<string, Box>;

type Dim = keyof Box;
/** `dims` limits what is compared: a tag is as wide as its content (the piece counts), centred on the same point. */
const REGIONS: Array<{ name: string; selector: string; dims?: Dim[] }> = [
  { name: 'screen', selector: '.morris-screen' },
  { name: 'table', selector: '.morris-screen .table' },
  { name: 'board', selector: '[data-test="morris-board"]' },
  { name: 'tag.white', selector: '[data-test="player-tag"][data-colour="white"]', dims: ['top', 'height'] },
  { name: 'tag.black', selector: '[data-test="player-tag"][data-colour="black"]', dims: ['top', 'height'] },
  { name: 'draw-offer', selector: '[data-test="draw-offer"]' },
  { name: 'footer', selector: '[data-test="table-footer"]' },
];

function boxOf(el: Element | null): Box | null {
  if (!el) return null;
  const r = el.getBoundingClientRect();
  return r.width > 0 || r.height > 0 ? { left: r.left, top: r.top, width: r.width, height: r.height } : null;
}

/** The boxes of the table right now. */
export function snapshot(): Cypress.Chainable<Snapshot> {
  return cy.document({ log: false }).then((doc) => {
    const out: Snapshot = {};
    for (const region of REGIONS) {
      const box = boxOf(doc.querySelector(region.selector));
      if (box) out[region.name] = box;
    }
    return out;
  });
}

/** What moved or was resized by more than `tolerance` px between two snapshots, one line each. */
export function shifts(before: Snapshot, after: Snapshot, tolerance = 1): string[] {
  const lines: string[] = [];
  for (const name of Object.keys(before)) {
    const a = after[name];
    if (!a) continue;
    const dims = REGIONS.find((r) => r.name === name)?.dims ?? ['left', 'top', 'width', 'height'];
    for (const dim of dims) {
      const delta = a[dim] - before[name][dim];
      if (Math.abs(delta) > tolerance) {
        lines.push(`${name}.${dim} ${delta > 0 ? '+' : ''}${delta.toFixed(1)}px (${before[name][dim].toFixed(1)} to ${a[dim].toFixed(1)})`);
      }
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

/** Waits until two snapshots a few frames apart are the same. */
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

/** Runs `change`, waits for `settled` (assertions that say the change is on screen), then records what moved. */
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

/**
 * Records (like a move) whatever is cut off. Nothing may be cut off: the screen never scrolls, so what does not fit is clipped. Every compared region must be inside the
 * screen box, the hint lines inside the table, and the board clear of the top bar.
 */
export function checkFits(label: string) {
  cy.document({ log: false }).then((doc) => {
    const screen = doc.querySelector('.morris-screen')!.getBoundingClientRect();
    const problems: string[] = [];
    for (const region of REGIONS) {
      const el = doc.querySelector(region.selector);
      const r = el?.getBoundingClientRect();
      if (!r || (r.width === 0 && r.height === 0)) continue;
      if (r.bottom > screen.bottom + 1 || r.top < screen.top - 1) problems.push(`${region.name} is outside the screen (${r.top.toFixed(1)} to ${r.bottom.toFixed(1)}, screen ${screen.top.toFixed(1)} to ${screen.bottom.toFixed(1)})`);
    }
    const table = doc.querySelector('.morris-screen .table')!;
    if (table.scrollHeight > table.clientHeight + 1) problems.push(`the table content overflows (${table.scrollHeight} > ${table.clientHeight})`);
    const notes = doc.querySelector('[data-test="notes"]');
    if (notes && notes.scrollHeight > notes.clientHeight + 1) problems.push(`the notes overflow their slot (${notes.scrollHeight} > ${notes.clientHeight})`);
    const footer = doc.querySelector('[data-test="table-footer"]')!.getBoundingClientRect();
    const tableBox = table.getBoundingClientRect();
    if (tableBox.bottom > footer.top + 1) problems.push('the table runs into the Back row');
    if (problems.length > 0) moves.push(`${label}: ${problems.join(', ')}`);
  });
}

/** Ends a test: nothing may have moved at any of its changes (all of them are listed when something did). */
export function expectNothingMoved() {
  cy.then(() => {
    const found = moves;
    moves = [];
    expect(found, 'what moved, by change of state').to.deep.eq([]);
  });
}
