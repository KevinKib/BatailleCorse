import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { defineComponent, h, ref } from 'vue';
import type Card from '../model/Card';
// A game imports the barrel only (`../../animation` from its own folder); this file sits inside the module.
import {
  AnimationSpeedButton, CardMotionLayer, defineAnchors, leg, scalePlan, useAnimationSpeed, useTableMotion,
  type CardMotionLayerPlay, type LegTempo, type MotionPlan, type TableMovement,
} from './index';

// The example of README.md ("Plugging a table in"), kept compiling and working: a tiny "Crazy Eights" table whose log
// entries are a card played from a seat to the pile. If this file has to change, the README changes with it.

// --- model/crazyeights/motion.ts: the game's only animation logic, pure and unit-tested ---------------------------
type CeEntry = { id: number; seat: number; card: Card };
type CeAnchor = { kind: 'seat'; seat: number } | { kind: 'pile' };
const CE_TEMPO = {
  opponent: { holdBeforeMs: 180, flightMs: 700, holdAfterMs: 250 } satisfies LegTempo,
  own: { holdBeforeMs: 100, flightMs: 480, holdAfterMs: 250 } satisfies LegTempo,
  queue: { maxQueued: 4, maxLagMs: 3500 },
};
function planMove(e: CeEntry, mySeat: number): MotionPlan<CeAnchor> {
  const t = leg(e.seat !== mySeat, CE_TEMPO);
  const from: CeAnchor = { kind: 'seat', seat: e.seat }, to: CeAnchor = { kind: 'pile' };
  const move: TableMovement<CeAnchor> = { kind: 'travel', from, to, card: e.card, flip: false, turnDown: false,
    actor: e.seat, byOpponent: e.seat !== mySeat, delayMs: t.holdBeforeMs, durationMs: t.flightMs,
    holdAfterMs: t.holdAfterMs, marks: [from, to] };
  return { movements: [move] };
}

// --- view/crazyeights/CrazyEightsGameScreen.vue: what its <script setup> and <template> hold -----------------------
const store = { mySeat: 0, events: ref<CeEntry[]>([]) };
const CrazyEightsTable = defineComponent(() => {
  const screenEl = ref<HTMLElement | null>(null);
  const layer = ref<{ play: CardMotionLayerPlay<CeAnchor> } | null>(null);
  const { speed, cycle } = useAnimationSpeed();
  const resolve = defineAnchors<CeAnchor>(() => screenEl.value, {
    seat: (a, q) => q.cardIn(q.find(`[data-seat="${a.seat}"]`)),
    pile: (_, q) => q.cardIn(q.find('[data-test="pile"]')),
  });
  useTableMotion({ layer, speed, bounds: CE_TEMPO.queue, log: () => store.events.value,
    plan: (e: CeEntry, { factor }) => scalePlan(planMove(e, store.mySeat), factor) });
  // <div ref="screenEl"> the seats and the pile </div>
  // <CardMotionLayer ref="layer" :resolve="resolve" mark-style="soft" />
  // <AnimationSpeedButton :speed="speed" :names="ui.speed.names" :label="ui.speed.label" @cycle="cycle" />
  return () => h('div', [
    h('div', { ref: screenEl }, [
      h('div', { 'data-seat': '0' }), h('div', { 'data-seat': '1' }), h('div', { 'data-test': 'pile' }),
    ]),
    h(CardMotionLayer<CeAnchor>, { ref: layer, resolve, markStyle: 'soft' }),
    h(AnimationSpeedButton, {
      speed: speed.value, names: { normal: 'Normal', fast: 'Fast', instant: 'Instant' }, label: 'Speed: {speed}', onCycle: cycle,
    }),
  ]);
});

// --- The example at work, on a fake table --------------------------------------------------------------------------
const card: Card = { rank: '8', suit: 'heart', name: 'heart_8' };
const entry = (id: number, seat: number): CeEntry => ({ id, seat, card });

describe('the README example (a Crazy Eights table)', () => {
  const original = Element.prototype.animate;
  const keyframes: Keyframe[][] = [];
  beforeEach(() => {
    window.matchMedia = ((q: string) => ({ matches: false, media: q, addEventListener() {}, removeEventListener() {} })) as unknown as typeof window.matchMedia;
    window.localStorage.clear();
    store.events.value = [entry(1, 1)];
    keyframes.length = 0;
    Element.prototype.animate = function (frames: Keyframe[]) {
      keyframes.push(frames);
      return { finished: new Promise(() => {}), cancel: vi.fn() } as unknown as Animation;
    } as typeof Element.prototype.animate;
    // Each place of the fake table is 40 x 58 px, 100 px apart (happy-dom lays nothing out); anything else has no size.
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      const place = this.dataset.seat ?? (this.dataset.test === 'pile' ? '2' : null);
      const left = place === null ? 0 : Number(place) * 100;
      const size = place === null ? 0 : 1;
      return { left, top: 10, width: 40 * size, height: 58 * size, right: left + 40, bottom: 68, x: left, y: 10, toJSON() {} } as DOMRect;
    });
  });
  afterEach(() => { Element.prototype.animate = original; vi.restoreAllMocks(); });

  it('givenAnOpponentsMove_thenItsPlanUsesTheOpponentLegAndRingsBothPlaces', () => {
    const [move] = planMove(entry(2, 1), 0).movements;
    expect([move.delayMs, move.durationMs, move.holdAfterMs, move.byOpponent]).toEqual([180, 700, 250, true]);
    expect(move.marks).toEqual([{ kind: 'seat', seat: 1 }, { kind: 'pile' }]);
    expect(planMove(entry(3, 0), 0).movements[0].durationMs).toBe(480);
  });

  it('givenTheLogAtMount_thenNothingFlies', async () => {
    const w = mount(CrazyEightsTable, { attachTo: document.body });
    await flushPromises();
    expect(w.findAll('[data-test="motion-card"]')).toHaveLength(0);
    w.unmount();
  });

  it('givenANewMove_thenItsCardFliesFromTheSeatToThePileWithItsRingsAndAnArrow', async () => {
    const w = mount(CrazyEightsTable, { attachTo: document.body });
    store.events.value = [entry(2, 1), ...store.events.value];
    await flushPromises();
    expect(w.findAll('[data-test="motion-card"]')).toHaveLength(1);
    expect(w.findAll('[data-test="motion-mark"]').map(m => m.attributes('data-role'))).toEqual(['from', 'to']);
    expect(w.findAll('[data-test="motion-arrow"]')).toHaveLength(1);
    expect(keyframes.length).toBeGreaterThan(0);
    w.unmount();
  });

  it('givenTheSpeedButtonSetToInstant_thenTheMoveOnlyRingsItsPlacesAndNothingFlies', async () => {
    const w = mount(CrazyEightsTable, { attachTo: document.body });
    await w.get('[data-test="speed-button"]').trigger('click'); // Fast
    await w.get('[data-test="speed-button"]').trigger('click'); // Instant
    store.events.value = [entry(2, 1), ...store.events.value];
    await flushPromises();
    expect(w.findAll('[data-test="motion-card"]')).toHaveLength(0);
    const marks = w.findAll('[data-test="motion-mark"]');
    expect(marks).toHaveLength(2);
    expect(marks.every(m => m.attributes('data-still') === 'true')).toBe(true);
    w.unmount();
  });
});
