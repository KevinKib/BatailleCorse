import { describe, expect, it } from 'vitest';
import { defineAnchors } from './anchors';
import type { MotionTarget } from './rect';

type Anchor = { kind: 'seat'; seat: number } | { kind: 'pile' };

function root(): HTMLElement {
  const el = document.createElement('div');
  el.innerHTML = '<div data-test="pile"></div><div data-seat="2"></div><div data-seat="3"></div>';
  el.querySelectorAll('div').forEach(child => {
    child.getBoundingClientRect = () => ({ left: 1, top: 2, width: 30, height: 40, right: 31, bottom: 42, x: 1, y: 2, toJSON() {} }) as DOMRect;
  });
  return el;
}
const target: MotionTarget = { rect: { left: 0, top: 0, width: 1, height: 1 }, cards: [] };

describe('defineAnchors', () => {
  it('givenAKind_thenItsResolverReceivesTheAnchorAndTheQuery', () => {
    const el = root();
    const resolve = defineAnchors<Anchor>(() => el, {
      seat: (a, q) => ({ rect: q.rect(q.find(`[data-seat="${a.seat}"]`))!, cards: [] }),
      pile: () => target,
    });
    expect(resolve({ kind: 'seat', seat: 2 })?.rect).toEqual({ left: 1, top: 2, width: 30, height: 40 });
    expect(resolve({ kind: 'pile' })).toBe(target);
  });

  it('givenAResolverThatThrows_thenNull', () => {
    const resolve = defineAnchors<Anchor>(() => root(), {
      seat: () => { throw new Error('boom'); },
      pile: () => target,
    });
    expect(resolve({ kind: 'seat', seat: 1 })).toBeNull();
  });

  it('givenAResolverThatFindsNothing_thenNull', () => {
    const resolve = defineAnchors<Anchor>(() => root(), {
      seat: (a, q) => q.cardIn(q.find(`[data-seat="${a.seat}"]`)),
      pile: () => null,
    });
    expect(resolve({ kind: 'seat', seat: 9 })).toBeNull();
    expect(resolve({ kind: 'pile' })).toBeNull();
  });

  it('givenNoRootYet_thenTheQueryFindsNothingAndTheAnchorIsNull', () => {
    const resolve = defineAnchors<Anchor>(() => null, {
      seat: (a, q) => q.cardIn(q.find(`[data-seat="${a.seat}"]`)),
      pile: (_, q) => q.cardIn(q.find('[data-test="pile"]')),
    });
    expect(resolve({ kind: 'pile' })).toBeNull();
  });

  it('givenFindAll_thenEveryMatchInsideTheRoot', () => {
    const el = root();
    let count = -1;
    defineAnchors<Anchor>(() => el, {
      seat: (_, q) => { count = q.findAll('[data-seat]').length; return null; },
      pile: () => null,
    })({ kind: 'seat', seat: 0 });
    expect(count).toBe(2);
  });

  it('givenAnAnchorOfAnUnknownKind_thenNull', () => {
    const resolve = defineAnchors<Anchor>(() => root(), { seat: () => target, pile: () => target });
    expect(resolve({ kind: 'nowhere' } as unknown as Anchor)).toBeNull();
  });
});
