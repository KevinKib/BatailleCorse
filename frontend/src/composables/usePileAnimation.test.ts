import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { flyCardsToPile, prefersReducedMotion } from './usePileAnimation';

const rect = (left: number, top: number, width = 50, height = 70) =>
  ({ left, top, width, height, right: left + width, bottom: top + height, x: left, y: top }) as DOMRect;

function fakeSource(left: number): HTMLElement {
  const el = document.createElement('button');
  el.innerHTML = '<div class="playing_card"></div>';
  el.getBoundingClientRect = () => rect(left, 400);
  document.body.appendChild(el);
  return el;
}

describe('flyCardsToPile', () => {
  let animate: ReturnType<typeof vi.fn>;
  let finishers: Array<() => void>;

  beforeEach(() => {
    finishers = [];
    animate = vi.fn(function (this: HTMLElement) {
      return { finished: new Promise<void>(r => { finishers.push(r); }), cancel() {} };
    });
    HTMLElement.prototype.animate = animate as unknown as typeof HTMLElement.prototype.animate;
  });
  afterEach(() => { document.body.innerHTML = ''; });

  const target = rect(200, 100, 90, 130);

  it('flies one ghost per card, staggered, and removes them when done', async () => {
    const ghosts = flyCardsToPile([fakeSource(10), fakeSource(40)], target, { reduceMotion: false });
    expect(ghosts).toBe(2);
    expect(document.querySelectorAll('[data-test="flying-card"]')).toHaveLength(2);
    const delays = animate.mock.calls.map(c => c[1].delay);
    expect(delays[1]).toBeGreaterThan(delays[0]);
    finishers.forEach(f => f());
    await Promise.resolve(); await Promise.resolve();
    expect(document.querySelectorAll('[data-test="flying-card"]')).toHaveLength(0);
  });

  it('moves from the hand card position to the pile', () => {
    flyCardsToPile([fakeSource(10)], target, { reduceMotion: false });
    const [from, to] = animate.mock.calls[0][0];
    expect(from.transform).toContain('translate(10px, 400px)');
    expect(to.transform).toContain('translate(200px, 100px)');
    expect(to.transform).toContain('scale(1.8');
  });

  it('does nothing when reduced motion is requested', () => {
    expect(flyCardsToPile([fakeSource(10)], target, { reduceMotion: true })).toBe(0);
    expect(document.querySelectorAll('[data-test="flying-card"]')).toHaveLength(0);
    expect(animate).not.toHaveBeenCalled();
  });

  it('does nothing without a pile target or without animation support', () => {
    expect(flyCardsToPile([fakeSource(10)], null, { reduceMotion: false })).toBe(0);
    // @ts-expect-error simulate an engine without the Web Animations API
    delete HTMLElement.prototype.animate;
    expect(flyCardsToPile([fakeSource(10)], target, { reduceMotion: false })).toBe(0);
  });
});

describe('prefersReducedMotion', () => {
  it('reads the media query and tolerates a missing matchMedia', () => {
    const original = window.matchMedia;
    window.matchMedia = ((q: string) => ({ matches: q.includes('reduce') })) as typeof window.matchMedia;
    expect(prefersReducedMotion()).toBe(true);
    // @ts-expect-error no matchMedia
    window.matchMedia = undefined;
    expect(prefersReducedMotion()).toBe(false);
    window.matchMedia = original;
  });
});
