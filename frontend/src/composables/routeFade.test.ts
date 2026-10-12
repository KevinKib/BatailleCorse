import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fadeIn, fadeOut, ROUTE_FADE_MS } from './routeFade';

// A leaving route view must always be removed from the DOM: if its
// transitionend never fires (cancelled transition, interrupted by a fast
// navigation) or is stolen by a bubbling child transition, the old view would
// stay mounted and duplicate every data-test of the new one.
describe('route fade', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => setTimeout(() => cb(0), 0));
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('completes the leave when the transitionend never fires', () => {
    const done = vi.fn();
    fadeOut(document.createElement('div'), done);
    vi.advanceTimersByTime(ROUTE_FADE_MS + 500);
    expect(done).toHaveBeenCalledTimes(1);
  });

  it('completes the enter when the transitionend never fires', () => {
    const done = vi.fn();
    fadeIn(document.createElement('div'), done);
    vi.advanceTimersByTime(ROUTE_FADE_MS + 500);
    expect(done).toHaveBeenCalledTimes(1);
  });

  it('ignores a transitionend bubbling from a child', () => {
    const el = document.createElement('div');
    const child = document.createElement('button');
    el.appendChild(child);
    const done = vi.fn();
    fadeOut(el, done);
    vi.advanceTimersByTime(50);
    child.dispatchEvent(new Event('transitionend', { bubbles: true }));
    expect(done).not.toHaveBeenCalled();
    el.dispatchEvent(new Event('transitionend'));
    expect(done).toHaveBeenCalledTimes(1);
  });

  it('calls done only once', () => {
    const el = document.createElement('div');
    const done = vi.fn();
    fadeOut(el, done);
    vi.advanceTimersByTime(50);
    el.dispatchEvent(new Event('transitionend'));
    vi.advanceTimersByTime(ROUTE_FADE_MS + 500);
    expect(done).toHaveBeenCalledTimes(1);
  });

  // A navigation during a fade: Vue calls leave on the view that was still entering. The cancelled
  // enter must not later reset the styles of the now leaving view (it would flash back in place).
  describe('a navigation during a fade', () => {
    const nextNavigation = () => Promise.resolve();

    it('keeps the view leaving after a cancelled enter, instead of resetting its styles', async () => {
      const el = document.createElement('div');
      const enterDone = vi.fn();
      const leaveDone = vi.fn();
      fadeIn(el, enterDone);
      vi.advanceTimersByTime(100);
      await nextNavigation();
      fadeOut(el, leaveDone);
      vi.advanceTimersByTime(ROUTE_FADE_MS - 100 + 250); // the old enter timeout has passed
      expect(enterDone).not.toHaveBeenCalled();
      expect(el.style.position).toBe('absolute');
      expect(el.style.opacity).toBe('0');
      vi.advanceTimersByTime(ROUTE_FADE_MS);
      expect(leaveDone).toHaveBeenCalledTimes(1);
    });

    it('finishes a view still leaving from the previous navigation right away', async () => {
      const first = document.createElement('div');
      const firstDone = vi.fn();
      fadeOut(first, firstDone);
      vi.advanceTimersByTime(100);
      await nextNavigation();
      fadeIn(document.createElement('div'), vi.fn());
      expect(firstDone).toHaveBeenCalledTimes(1);
    });

    it('lets the view leaving in the same navigation fade normally', () => {
      const leaving = document.createElement('div');
      const leavingDone = vi.fn();
      fadeOut(leaving, leavingDone);
      fadeIn(document.createElement('div'), vi.fn());
      expect(leavingDone).not.toHaveBeenCalled();
      vi.advanceTimersByTime(ROUTE_FADE_MS + 500);
      expect(leavingDone).toHaveBeenCalledTimes(1);
    });
  });
});
