// Geometry checks of the Kobo score sheet (the bottom sheet of the round end).

/**
 * The score sheet is entirely on screen, no wider than the screen, its action zone is reachable, and every button
 * of it is a 44 px target. A sheet taller than its room scrolls inside itself.
 */
export function sheetFitsTheScreen() {
  cy.window().then((win) => {
    const sheet = win.document.querySelector('[data-test="score-sheet"]')!.getBoundingClientRect();
    expect(sheet.top, 'sheet top').to.be.at.least(0);
    expect(sheet.bottom, 'sheet bottom').to.be.at.most(win.innerHeight + 1);
    expect(sheet.left, 'sheet left').to.be.at.least(0);
    expect(sheet.right, 'sheet right').to.be.at.most(win.innerWidth + 1);
    const actions = win.document.querySelector('[data-test="sheet-actions"]')!.getBoundingClientRect();
    expect(actions.bottom, 'actions bottom').to.be.at.most(win.innerHeight + 1);
    expect(actions.top, 'actions top').to.be.at.least(sheet.top - 1);
  });
  cy.get('[data-test="score-sheet"] button').each(($b) => {
    const r = $b[0].getBoundingClientRect();
    expect(Math.round(r.height), 'button height').to.be.at.least(44);
    expect(Math.round(r.width), 'button width').to.be.at.least(44);
  });
}

export interface SheetMetrics {
  label: string;
  viewport: string;
  sheetWidth: number;
  sheetHeight: number;
  /** The sheet's body scrolls inside itself when its content is taller than its room. */
  bodyScrolls: boolean;
  /** Height of the table frame above the sheet that is still on screen, and how many opponent seats show whole. */
  tableVisibleHeight: number;
  opponentsFullyVisible: number;
  opponents: number;
}

/** What the score sheet takes and leaves at this moment; appended to `KOBO_METRICS_FILE` like `measureTable`. */
export function measureSheet(label: string) {
  return cy.document().then((doc) => {
    const win = doc.defaultView!;
    const sheet = doc.querySelector('[data-test="score-sheet"]')!.getBoundingClientRect();
    const body = doc.querySelector('[data-test="sheet-body"]') as HTMLElement;
    const frame = doc.querySelector('[data-test="kobo-table"]')!.getBoundingClientRect();
    const visibleBottom = Math.min(frame.bottom, sheet.top);
    const seats = Array.from(doc.querySelectorAll('[data-test="opponent-seat"]'));
    const whole = seats.filter((el) => {
      const r = el.getBoundingClientRect();
      return r.top >= frame.top - 1 && r.bottom <= visibleBottom + 1 && r.left >= frame.left - 1 && r.right <= frame.right + 1;
    });
    const metrics: SheetMetrics = {
      label,
      viewport: `${win.innerWidth}x${win.innerHeight}`,
      sheetWidth: Math.round(sheet.width),
      sheetHeight: Math.round(sheet.height),
      bodyScrolls: body.scrollHeight > body.clientHeight + 1,
      tableVisibleHeight: Math.max(0, Math.round(visibleBottom - frame.top)),
      opponentsFullyVisible: whole.length,
      opponents: seats.length,
    };
    const file = Cypress.env('KOBO_METRICS_FILE') as string | undefined;
    if (file) return cy.writeFile(file, JSON.stringify(metrics) + '\n', { flag: 'a+' }).then(() => metrics);
    return metrics;
  });
}
