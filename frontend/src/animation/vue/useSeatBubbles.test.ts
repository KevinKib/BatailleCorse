import { afterEach, beforeEach, describe, expect, expectTypeOf, it, vi } from 'vitest';
import { useSeatBubbles } from './useSeatBubbles';

describe('useSeatBubbles', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it('givenAGamesKinds_thenTheBubblesAreTypedOnThem', () => {
    const bubbles = useSeatBubbles<'pass' | 'completed'>({ gapMs: 200 });
    bubbles.show({ seat: 2, kind: 'completed' }, 1000);
    expectTypeOf(bubbles.bySeat.value[2].kind).toEqualTypeOf<'pass' | 'completed'>();
    expect(bubbles.bySeat.value[2].kind).toBe('completed');
    // @ts-expect-error a kind the game does not have
    bubbles.cancel(); bubbles.show({ seat: 2, kind: 'nope' }, 1000);
  });

  it('givenABubble_thenItShowsAtOnceOnItsSeatAndGoesAfterItsLife', () => {
    const bubbles = useSeatBubbles({ gapMs: 200 });
    bubbles.show({ seat: 1, kind: 'pass' }, 1000);
    expect(bubbles.bySeat.value[1]?.kind).toBe('pass');
    vi.advanceTimersByTime(999);
    expect(bubbles.bySeat.value[1]).toBeDefined();
    vi.advanceTimersByTime(2);
    expect(bubbles.bySeat.value[1]).toBeUndefined();
  });

  it('givenABurst_thenTheBubblesPopTheGapApart', () => {
    const bubbles = useSeatBubbles({ gapMs: 200 });
    bubbles.show({ seat: 1, kind: 'pass' }, 1000);
    bubbles.show({ seat: 2, kind: 'pass' }, 1000);
    bubbles.show({ seat: 3, kind: 'pass' }, 1000);
    expect(Object.keys(bubbles.bySeat.value)).toEqual(['1']);
    vi.advanceTimersByTime(200);
    expect(Object.keys(bubbles.bySeat.value)).toEqual(['1', '2']);
    vi.advanceTimersByTime(200);
    expect(Object.keys(bubbles.bySeat.value)).toEqual(['1', '2', '3']);
  });

  it('givenANewBubbleOnTheSameSeat_thenItReplacesTheOldOneWithANewKey', () => {
    const bubbles = useSeatBubbles({ gapMs: 0 });
    bubbles.show({ seat: 1, kind: 'pass' }, 1000);
    const first = bubbles.bySeat.value[1]!.key;
    vi.advanceTimersByTime(500);
    bubbles.show({ seat: 1, kind: 'outForbidden' }, 1000);
    expect(bubbles.bySeat.value[1]).toMatchObject({ kind: 'outForbidden' });
    expect(bubbles.bySeat.value[1]!.key).not.toBe(first);
    vi.advanceTimersByTime(600);
    expect(bubbles.bySeat.value[1]?.kind).toBe('outForbidden');
  });

  it('givenCancel_thenEveryBubbleAndTimerGoes', () => {
    const bubbles = useSeatBubbles({ gapMs: 200 });
    bubbles.show({ seat: 1, kind: 'pass' }, 1000);
    bubbles.show({ seat: 2, kind: 'pass' }, 1000);
    bubbles.cancel();
    vi.advanceTimersByTime(500);
    expect(bubbles.bySeat.value).toEqual({});
  });
});
