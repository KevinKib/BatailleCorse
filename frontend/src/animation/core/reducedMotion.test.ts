import { afterEach, describe, expect, it } from 'vitest';
import { canAnimate, prefersReducedMotion } from './reducedMotion';

describe('prefersReducedMotion', () => {
  const original = window.matchMedia;
  afterEach(() => { window.matchMedia = original; });

  it('reads the media query and tolerates a missing matchMedia', () => {
    window.matchMedia = ((q: string) => ({ matches: q.includes('reduce') })) as typeof window.matchMedia;
    expect(prefersReducedMotion()).toBe(true);
    // @ts-expect-error no matchMedia
    window.matchMedia = undefined;
    expect(prefersReducedMotion()).toBe(false);
  });

  it('givenAMatchMediaThatThrows_thenFalse', () => {
    window.matchMedia = (() => { throw new Error('no'); }) as unknown as typeof window.matchMedia;
    expect(prefersReducedMotion()).toBe(false);
  });
});

describe('canAnimate', () => {
  const originalMedia = window.matchMedia;
  const originalAnimate = Element.prototype.animate;
  afterEach(() => { window.matchMedia = originalMedia; Element.prototype.animate = originalAnimate; });
  const media = (reduce: boolean) => {
    window.matchMedia = ((q: string) => ({ matches: reduce && q.includes('reduce') })) as typeof window.matchMedia;
  };

  it('givenMotionAndWebAnimations_thenTrue', () => {
    media(false);
    Element.prototype.animate = (() => ({})) as unknown as typeof Element.prototype.animate;
    expect(canAnimate()).toBe(true);
  });

  it('givenReducedMotion_thenFalse', () => {
    media(true);
    Element.prototype.animate = (() => ({})) as unknown as typeof Element.prototype.animate;
    expect(canAnimate()).toBe(false);
  });

  it('givenNoWebAnimations_thenFalse', () => {
    media(false);
    // @ts-expect-error not supported
    Element.prototype.animate = undefined;
    expect(canAnimate()).toBe(false);
  });
});
