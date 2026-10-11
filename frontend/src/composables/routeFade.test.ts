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
});
