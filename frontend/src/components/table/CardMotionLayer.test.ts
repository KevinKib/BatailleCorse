import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import CardMotionLayer from './CardMotionLayer.vue';
import type { MotionTarget } from './motionTarget';
import type { TableMovement } from '../../model/table/motion';

// The shared layer as President uses it (Kobo's use is covered by KoboMotionLayer.test.ts): string places, a ring
// without a flight, still rings, ring durations given by the table.
type Move = TableMovement<string>;
const move = (over: Partial<Move> = {}): Move => ({
  kind: 'travel', from: 'seat', to: 'pile', card: null, flip: false, turnDown: false, actor: 1, byOpponent: true,
  delayMs: 100, durationMs: 400, holdAfterMs: 100, marks: ['seat', 'pile'], ...over,
});
const at = (left: number): MotionTarget => ({ rect: { left, top: 10, width: 40, height: 58 }, cards: [] });
const resolve = (a: string) => at(a === 'seat' ? 0 : a === 'pile' ? 200 : 400);

function motion(reduce: boolean) {
  window.matchMedia = ((q: string) => ({ matches: reduce && q.includes('reduce'), media: q, addEventListener() {}, removeEventListener() {} })) as typeof window.matchMedia;
}
type Layer = { play: (m: Move[], o?: { marksOnly?: boolean; startInMs?: number; still?: boolean }) => void };
const play = (w: ReturnType<typeof mount>, m: Move[], o = {}) => (w.vm as unknown as Layer).play(m, o);

describe('CardMotionLayer', () => {
  const original = Element.prototype.animate;
  beforeEach(() => {
    motion(false);
    Element.prototype.animate = function () {
      return { finished: new Promise(() => {}), cancel: vi.fn() } as unknown as Animation;
    } as typeof Element.prototype.animate;
  });
  afterEach(() => { Element.prototype.animate = original; vi.useRealTimers(); });

  it('givenARingMovement_thenItsPlacesAreRingedAndNoCardFlies', async () => {
    const w = mount(CardMotionLayer<string>, { props: { resolve } });
    play(w, [move({ kind: 'ring', from: 'pile', to: 'winner', marks: ['winner'] })]);
    await flushPromises();
    expect(w.findAll('[data-test="motion-card"]')).toHaveLength(0);
    const marks = w.findAll('[data-test="motion-mark"]');
    expect(marks).toHaveLength(1);
    expect(marks[0].attributes('data-role')).toBe('to');
  });

  it('givenATravel_thenTheSourceIsDashedTheDestinationPlainAndAnArrowShows', async () => {
    const w = mount(CardMotionLayer<string>, { props: { resolve } });
    play(w, [move()]);
    await flushPromises();
    expect(w.findAll('[data-test="motion-card"]')).toHaveLength(1);
    expect(w.findAll('[data-test="motion-mark"]').map(m => m.attributes('data-role'))).toEqual(['from', 'to']);
    expect(w.findAll('[data-test="motion-arrow"]')).toHaveLength(1);
  });

  it('givenAStillPlay_thenTheRingsDoNotAnimateAndLastTheTablesStillDuration', async () => {
    vi.useFakeTimers();
    const w = mount(CardMotionLayer<string>, { props: { resolve, markReducedMs: 900 } });
    play(w, [move()], { marksOnly: true, still: true });
    await flushPromises();
    expect(w.findAll('[data-test="motion-card"]')).toHaveLength(0);
    expect(w.findAll('[data-test="motion-mark"]').every(m => m.attributes('data-still') === 'true')).toBe(true);
    await vi.advanceTimersByTimeAsync(950);
    expect(w.findAll('[data-test="motion-mark"]')).toHaveLength(0);
  });

  it('givenTheArrowIdOfTheTable_thenTheArrowUsesIt', async () => {
    const w = mount(CardMotionLayer<string>, { props: { resolve, arrowId: 'president-arrow-head' } });
    play(w, [move()]);
    await flushPromises();
    expect(w.find('marker').attributes('id')).toBe('president-arrow-head');
    expect(w.get('[data-test="motion-arrow"]').attributes('marker-end')).toBe('url(#president-arrow-head)');
  });

  it('givenTheSoftStyle_thenTheRingHugsTheCardWithoutHaloOrPop', async () => {
    const w = mount(CardMotionLayer<string>, { props: { resolve, markStyle: 'soft' } });
    play(w, [move()]);
    await flushPromises();
    const marks = w.findAll('[data-test="motion-mark"]');
    expect(marks.every(m => m.attributes('data-style') === 'soft')).toBe(true);
    // The card is 40 x 58 at (0, 10): the ring is 2 px around it.
    expect(marks[0].attributes('style')).toContain('left: -2px');
    expect(marks[0].attributes('style')).toContain('width: 44px');
    expect(marks[0].attributes('style')).toContain('height: 62px');
  });

  it('givenNoStyle_thenTheRingIsKobosOneWith4pxAround', async () => {
    const w = mount(CardMotionLayer<string>, { props: { resolve } });
    play(w, [move()]);
    await flushPromises();
    const mark = w.get('[data-test="motion-mark"]');
    expect(mark.attributes('data-style')).toBeUndefined();
    expect(mark.attributes('style')).toContain('width: 48px');
  });
});
