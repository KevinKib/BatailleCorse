import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import CardMotionLayer from './CardMotionLayer.vue';
import type { MotionTarget } from '../dom/rect';
import type { TableMovement } from '../core/movement';

// Characterisation of the layer's keyframes, durations and delays, written on the code as it was before the shared
// module: the layer must keep producing these, byte for byte, when it moves and when it gains optional fields.
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
type Layer = { play: (m: Move[], o?: { marksOnly?: boolean; startInMs?: number; still?: boolean }) => unknown };

interface Call { on: string; frames: Keyframe[]; options: KeyframeAnimationOptions }
const calls: Call[] = [];

describe('CardMotionLayer keyframes (characterisation)', () => {
  const original = Element.prototype.animate;
  beforeEach(() => {
    calls.length = 0;
    window.matchMedia = ((q: string) => ({ matches: false, media: q, addEventListener() {}, removeEventListener() {} })) as typeof window.matchMedia;
    Element.prototype.animate = function (this: Element, frames: Keyframe[], options: KeyframeAnimationOptions) {
      const on = this.getAttribute('data-face') ? `face:${this.getAttribute('data-face')}`
        : this.hasAttribute('data-ghost') ? 'ghost'
          : this.hasAttribute('data-arrow') ? 'arrow'
            : 'other';
      calls.push({ on, frames, options });
      return { finished: new Promise(() => {}), cancel: vi.fn() } as unknown as Animation;
    } as typeof Element.prototype.animate;
  });
  afterEach(() => { Element.prototype.animate = original; });

  async function run(moves: Move[], options = {}) {
    const w = mount(CardMotionLayer<string>, { props: { resolve } });
    (w.vm as unknown as Layer).play(moves, options);
    await flushPromises();
  }

  it('givenATravel_thenOneAnimationHoldsFliesAndHolds', async () => {
    await run([move()]);
    expect(calls).toEqual([{
      on: 'ghost',
      frames: [
        { transform: 'translate(0px, 10px) scale(1)', easing: 'cubic-bezier(.2,.8,.2,1)' },
        { transform: 'translate(200px, 30px) scale(2)', offset: 0.8 },
        { transform: 'translate(200px, 30px) scale(2)' },
      ],
      options: { duration: 500, delay: 100, fill: 'both' },
    }]);
  });

  it('givenAGlance_thenTheCopyGoesOutAndBack', async () => {
    await run([move({ kind: 'glance' })]);
    expect(calls).toEqual([{
      on: 'ghost',
      frames: [
        { transform: 'translate(0px, 10px) scale(1)', easing: 'cubic-bezier(.2,.8,.2,1)' },
        { transform: 'translate(187px, 51.65px) scale(1)', offset: 0.8 * 0.35 },
        { transform: 'translate(187px, 51.65px) scale(1)', offset: 0.8 * 0.65, easing: 'cubic-bezier(.2,.8,.2,1)' },
        { transform: 'translate(0px, 10px) scale(1)', offset: 0.8 },
        { transform: 'translate(0px, 10px) scale(1)' },
      ],
      options: { duration: 500, delay: 100, fill: 'both' },
    }]);
  });

  it('givenAFlippedOpponentMoveBehindAnother_thenTheFacesTheArrowAndTheWaitAreAnimated', async () => {
    const card = { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' } as unknown as Move['card'];
    await run([move({ flip: true, card, byOpponent: true })], { startInMs: 300 });
    expect(calls).toEqual([
      {
        on: 'ghost',
        frames: [
          { transform: 'translate(0px, 10px) scale(1)', easing: 'cubic-bezier(.2,.8,.2,1)' },
          { transform: 'translate(200px, 30px) scale(2)', offset: 0.8 },
          { transform: 'translate(200px, 30px) scale(2)' },
        ],
        options: { duration: 500, delay: 400, fill: 'both' },
      },
      { on: 'ghost', frames: [{ opacity: 0 }, { opacity: 0 }], options: { duration: 300, delay: 0, fill: 'none' } },
      {
        on: 'face:back',
        frames: [{ transform: 'scaleX(1)' }, { transform: 'scaleX(0)', offset: 0.5 }, { transform: 'scaleX(0)' }],
        options: { duration: 400, delay: 400, fill: 'both' },
      },
      {
        on: 'face:front',
        frames: [{ transform: 'scaleX(0)' }, { transform: 'scaleX(0)', offset: 0.5 }, { transform: 'scaleX(1)' }],
        options: { duration: 400, delay: 400, fill: 'both' },
      },
      {
        on: 'arrow',
        frames: [{ opacity: 0 }, { opacity: 0.8, offset: 0.12 }, { opacity: 0.8, offset: 0.85 }, { opacity: 0 }],
        options: { duration: 500, delay: 400, fill: 'both' },
      },
    ]);
  });

  it('givenATurnDown_thenTheFaceClosesAfterLanding', async () => {
    const card = { rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' } as unknown as Move['card'];
    await run([move({ turnDown: true, card, durationMs: 400, holdAfterMs: 400 })]);
    const faces = calls.filter(c => c.on.startsWith('face:'));
    expect(faces).toEqual([
      {
        on: 'face:front',
        frames: [
          { transform: 'scaleX(1)' }, { transform: 'scaleX(1)', offset: 0.5 },
          { transform: 'scaleX(0)', offset: 0.75 }, { transform: 'scaleX(0)' },
        ],
        options: { duration: 800, delay: 100, fill: 'both' },
      },
      {
        on: 'face:back',
        frames: [{ transform: 'scaleX(0)' }, { transform: 'scaleX(0)', offset: 0.75 }, { transform: 'scaleX(1)' }],
        options: { duration: 800, delay: 100, fill: 'both' },
      },
    ]);
  });

  it('givenAReveal_thenEachCardOfThePlaceTurnsOverInPlace', async () => {
    const el = document.createElement('div');
    document.body.appendChild(el);
    places.seat = { ...places.seat, cards: [el] };
    try {
      await run([move({ kind: 'reveal', durationMs: 700 })]);
      expect(calls).toEqual([{
        on: 'other',
        frames: [{ transform: 'scaleX(1)' }, { transform: 'scaleX(0.05)', offset: 0.45 }, { transform: 'scaleX(1)' }],
        options: { duration: 700, easing: 'ease-in-out' },
      }]);
    } finally {
      places.seat = { ...places.seat, cards: [] };
      el.remove();
    }
  });
});
