import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import CardMotionLayer from './CardMotionLayer.vue';
import type { MotionTarget } from '../dom/rect';
import type { TableMovement } from '../core/movement';

// The optional parts of the layer (spec 5.4): what `play` returns, `landing`, `easing` and the `zIndex` prop. The
// defaults are covered by CardMotionLayer.keyframes.test.ts (today's keyframes, unchanged).
type Move = TableMovement<string>;
const move = (over: Partial<Move> = {}): Move => ({
  kind: 'travel', from: 'seat', to: 'pile', card: null, flip: false, turnDown: false, actor: 1, byOpponent: false,
  delayMs: 100, durationMs: 400, holdAfterMs: 100, marks: [], ...over,
});
const places: Record<string, MotionTarget> = {
  seat: { rect: { left: 0, top: 10, width: 40, height: 58 }, cards: [] },
  pile: { rect: { left: 200, top: 30, width: 80, height: 116 }, cards: [] },
};
const resolve = (a: string) => places[a] ?? null;
type PlayOptions = { marksOnly?: boolean; startInMs?: number; still?: boolean };
type Layer = { play: (m: Move[], o?: PlayOptions) => { endsInMs: number } };

const calls: { frames: Keyframe[]; options: KeyframeAnimationOptions }[] = [];

describe('CardMotionLayer options', () => {
  const original = Element.prototype.animate;
  beforeEach(() => {
    calls.length = 0;
    window.matchMedia = ((q: string) => ({ matches: false, media: q, addEventListener() {}, removeEventListener() {} })) as typeof window.matchMedia;
    Element.prototype.animate = function (this: Element, frames: Keyframe[], options: KeyframeAnimationOptions) {
      calls.push({ frames, options });
      return { finished: new Promise(() => {}), cancel: vi.fn() } as unknown as Animation;
    } as typeof Element.prototype.animate;
  });
  afterEach(() => { Element.prototype.animate = original; });

  async function run(moves: Move[], options: PlayOptions = {}, props: Record<string, unknown> = {}) {
    const w = mount(CardMotionLayer<string>, { props: { resolve, ...props } });
    const result = (w.vm as unknown as Layer).play(moves, options);
    await flushPromises();
    return { w, result };
  }

  describe('play', () => {
    it('givenAFlight_thenItReturnsWhenItsHoldAfterLandingIsOver', async () => {
      expect((await run([move()])).result).toEqual({ endsInMs: 600 });
    });

    it('givenAnEventBehindAnother_thenTheWaitCounts', async () => {
      expect((await run([move()], { startInMs: 300 })).result).toEqual({ endsInMs: 900 });
    });

    it('givenSeveralMovements_thenTheLongestOne', async () => {
      const moves = [move(), move({ delayMs: 500, durationMs: 400, holdAfterMs: 300 })];
      expect((await run(moves)).result).toEqual({ endsInMs: 1200 });
    });

    it('givenARevealOnAPlaceWithCards_thenItsTurnCounts', async () => {
      places.seat = { ...places.seat, cards: [document.createElement('div')] };
      try {
        expect((await run([move({ kind: 'reveal', delayMs: 0, durationMs: 700, holdAfterMs: 0 })])).result).toEqual({ endsInMs: 700 });
      } finally {
        places.seat = { ...places.seat, cards: [] };
      }
    });

    it.each([
      ['marksOnly', { marksOnly: true }, [move()]],
      ['aRing', {}, [move({ kind: 'ring', marks: ['pile'] })]],
      ['aPlaceThatCannotBeFound', {}, [move({ to: 'nowhere' })]],
      ['noMovement', {}, []],
    ] as const)('given %s_thenZero', async (_name, options, moves) => {
      expect((await run([...moves], options)).result).toEqual({ endsInMs: 0 });
    });

    it('givenNoWebAnimations_thenZero', async () => {
      // @ts-expect-error not supported
      Element.prototype.animate = undefined;
      expect((await run([move()])).result).toEqual({ endsInMs: 0 });
    });
  });

  describe('landing', () => {
    it('givenATiltAndAFade_thenTheCopyLandsTiltedAndFadesAtTheEnd', async () => {
      await run([move({ landing: { tiltDeg: 5, fade: true } })]);
      expect(calls[0]).toEqual({
        frames: [
          { transform: 'translate(0px, 10px) scale(1) rotate(0deg)', opacity: 1, easing: 'cubic-bezier(.2,.8,.2,1)' },
          { transform: 'translate(200px, 30px) scale(2) rotate(5deg)', opacity: 1, offset: 0.8 },
          { transform: 'translate(200px, 30px) scale(2) rotate(5deg)', opacity: 0 },
        ],
        options: { duration: 500, delay: 100, fill: 'both' },
      });
    });

    it('givenATiltWithoutAFade_thenTheCopyStaysVisible', async () => {
      await run([move({ landing: { tiltDeg: -3, fade: false } })]);
      const frames = calls[0].frames;
      expect(frames.map(f => f.transform)).toEqual([
        'translate(0px, 10px) scale(1) rotate(0deg)',
        'translate(200px, 30px) scale(2) rotate(-3deg)',
        'translate(200px, 30px) scale(2) rotate(-3deg)',
      ]);
      expect(frames.every(f => !('opacity' in f))).toBe(true);
    });

    it('givenScaleToCentre_thenTheCopyShrinksAtMostToAThirdAndIsCentredOnThePlace', async () => {
      await run([move({ landing: { tiltDeg: 0, fade: true, scaleTo: 'centre' } })]);
      // 40 px wide into an 80 px place: never enlarged (scale 1), centred on the place.
      expect(calls[0].frames[1].transform).toBe('translate(220px, 59px) scale(1) rotate(0deg)');
    });

    it('givenScaleToCentreIntoATinyPlace_thenTheScaleStopsAt0.3', async () => {
      places.tiny = { rect: { left: 100, top: 100, width: 4, height: 6 }, cards: [] };
      try {
        await run([move({ to: 'tiny', landing: { tiltDeg: 0, fade: true, scaleTo: 'centre' } })]);
        expect(calls[0].frames[1].transform).toContain('scale(0.3)');
      } finally {
        delete places.tiny;
      }
    });
  });

  describe('easing', () => {
    it('givenAnEasing_thenItReplacesTheLayersDefault', async () => {
      await run([move({ easing: 'cubic-bezier(0.22, 0.8, 0.3, 1)' })]);
      expect(calls[0].frames[0].easing).toBe('cubic-bezier(0.22, 0.8, 0.3, 1)');
    });

    it('givenAGlanceWithAnEasing_thenBothLegsUseIt', async () => {
      await run([move({ kind: 'glance', easing: 'linear' })]);
      expect(calls[0].frames.filter(f => f.easing).map(f => f.easing)).toEqual(['linear', 'linear']);
    });
  });

  describe('zIndex', () => {
    it('givenNoProp_thenTheStyleSheetsValueApplies', async () => {
      const { w } = await run([], {});
      expect(w.get('[data-test="motion-layer"]').attributes('style') ?? '').not.toContain('z-index');
    });

    it('givenTheProp_thenTheLayerIsAtThatLevel', async () => {
      const { w } = await run([], {}, { zIndex: 3000 });
      expect(w.get('[data-test="motion-layer"]').attributes('style')).toContain('z-index: 3000');
    });
  });
});
