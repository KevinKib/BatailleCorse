// Spacing checks for any screen: the clear gap between two elements and the absence of overlap, measured on their
// bounding boxes. The geometry is pure (unit-tested in spacing.test.ts); the Cypress wrappers below only read the boxes.
// How and when to use them: .claude/skills/frontend-spacing/SKILL.md.

export interface Rect { left: number; top: number; right: number; bottom: number }

/** The two sizes every spacing check runs at: a small phone and a short laptop. */
export const SPACING_VIEWPORTS: ReadonlyArray<readonly [number, number]> = [[375, 667], [1280, 720]];

/** Signed distance between the projections of two boxes on each axis (negative: the projections overlap). */
const axisGaps = (a: Rect, b: Rect) => ({
  dx: Math.max(a.left, b.left) - Math.min(a.right, b.right),
  dy: Math.max(a.top, b.top) - Math.min(a.bottom, b.bottom),
});

/**
 * Clear space between two boxes, in px: the distance along the axis that separates them (the larger one when they
 * are apart on both axes), 0 when they touch, negative by the overlap depth when they overlap.
 */
export function gapBetween(a: Rect, b: Rect): number {
  const { dx, dy } = axisGaps(a, b);
  return Math.max(dx, dy);
}

/** True when the boxes share an area deeper than `tolerance` px on both axes (1 px absorbs sub-pixel rounding). */
export function overlaps(a: Rect, b: Rect, tolerance = 1): boolean {
  const { dx, dy } = axisGaps(a, b);
  return dx < -tolerance && dy < -tolerance;
}

const rectOf = (el: Element): Rect => {
  const r = el.getBoundingClientRect();
  return { left: r.left, top: r.top, right: r.right, bottom: r.bottom };
};

/** The first visible element a selector matches (Cypress retries until there is one). */
const visible = (selector: string) => cy.get(selector).filter(':visible').first();

/** Asserts at least `minPx` of clear space between the first visible matches of two selectors. */
export function expectGap(selectorA: string, selectorB: string, minPx: number) {
  visible(selectorA).then(($a) => {
    visible(selectorB).then(($b) => {
      const gap = gapBetween(rectOf($a[0]), rectOf($b[0]));
      // Half a pixel of slack: layout boxes are fractional, a designed 8 px gap can measure 7.98.
      expect(gap + 0.5, `gap between ${selectorA} and ${selectorB} (${gap.toFixed(1)} px)`).to.be.at.least(minPx);
    });
  });
}

/** Asserts that the first visible matches of two selectors do not overlap. */
export function expectNoOverlap(selectorA: string, selectorB: string) {
  visible(selectorA).then(($a) => {
    visible(selectorB).then(($b) => {
      const a = rectOf($a[0]);
      const b = rectOf($b[0]);
      expect(overlaps(a, b), `${selectorA} overlaps ${selectorB} (gap ${gapBetween(a, b).toFixed(1)} px)`).to.eq(false);
    });
  });
}

/** Runs `check` at each spacing viewport (375x667, then 1280x720), after the layout has settled at that size. */
export function atSpacingViewports(check: (width: number, height: number) => void) {
  for (const [width, height] of SPACING_VIEWPORTS) {
    cy.viewport(width, height);
    check(width, height);
  }
}
