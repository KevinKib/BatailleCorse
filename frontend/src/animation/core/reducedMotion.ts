/** The `prefers-reduced-motion` media query, guarded (no `matchMedia`, or one that throws: false). */
export function prefersReducedMotion(): boolean {
  try {
    return typeof window.matchMedia === 'function'
      && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  } catch {
    return false;
  }
}

/** Whether a picture may move at all: the device does not ask for reduced motion and Web Animations exist. */
export function canAnimate(): boolean {
  return !prefersReducedMotion() && typeof Element.prototype.animate === 'function';
}
