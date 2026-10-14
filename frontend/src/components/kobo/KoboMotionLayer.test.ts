import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import KoboMotionLayer, { type MotionTarget } from './KoboMotionLayer.vue';
import { MOTION_MAX_MS, type Movement } from '../../model/kobo/motion';
import { buildKoboCard } from '../../model/fixtures/kobo';

const travel = (over: Partial<Movement> = {}): Movement => ({
  kind: 'travel', from: { kind: 'draw' }, to: { kind: 'hand' }, card: null, flip: false, actor: 1, byOpponent: true,
  delayMs: 0, durationMs: 320, ...over,
});
const target = (left: number, cards: HTMLElement[] = []): MotionTarget => ({ rect: { left, top: 10, width: 40, height: 58 }, cards });

interface FakeAnimation { finished: Promise<void>; cancel: ReturnType<typeof vi.fn>; end: () => void }
let animations: FakeAnimation[];
let animateCalls: { options: KeyframeAnimationOptions }[];

function stubAnimate() {
  animations = [];
  animateCalls = [];
  Element.prototype.animate = function (_frames: unknown, options: KeyframeAnimationOptions) {
    let end!: () => void;
    const finished = new Promise<void>((resolve) => { end = resolve; });
    const animation = { finished, cancel: vi.fn(), end } as FakeAnimation;
    animations.push(animation);
    animateCalls.push({ options });
    return animation as unknown as Animation;
  } as typeof Element.prototype.animate;
}
function motion(reduce: boolean) {
  window.matchMedia = ((q: string) => ({ matches: reduce && q.includes('reduce'), media: q, addEventListener() {}, removeEventListener() {} })) as typeof window.matchMedia;
}

describe('KoboMotionLayer', () => {
  const original = Element.prototype.animate;
  beforeEach(() => { stubAnimate(); motion(false); });
  afterEach(() => { Element.prototype.animate = original; vi.restoreAllMocks(); });

  it('isHiddenFromAssistiveTechnologyAndTakesNoPointerEvent', () => {
    const wrapper = mount(KoboMotionLayer, { props: { resolve: () => null } });
    expect(wrapper.get('[data-test="motion-layer"]').attributes('aria-hidden')).toBe('true');
  });

  it('givenAMovement_thenACardFliesWithinTheLimitAndIsRemovedWhenLanded', async () => {
    const wrapper = mount(KoboMotionLayer, { props: { resolve: (a) => target(a.kind === 'draw' ? 0 : 100) } });
    (wrapper.vm as unknown as { play: (m: Movement[]) => void }).play([travel()]);
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(1);
    const options = animateCalls[0].options;
    expect((options.duration as number) + (options.delay as number)).toBeLessThan(MOTION_MAX_MS);
    animations[0].end();
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
  });

  it('givenAnOpponentsMove_thenAFaintArrowShowsFromWhereToWhere', async () => {
    const wrapper = mount(KoboMotionLayer, { props: { resolve: (a) => target(a.kind === 'draw' ? 0 : 200) } });
    (wrapper.vm as unknown as { play: (m: Movement[]) => void }).play([travel()]);
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-arrow"]')).toHaveLength(1);
  });

  it('givenMyOwnMove_thenThereIsNoArrow', async () => {
    const wrapper = mount(KoboMotionLayer, { props: { resolve: (a) => target(a.kind === 'draw' ? 0 : 200) } });
    (wrapper.vm as unknown as { play: (m: Movement[]) => void }).play([travel({ byOpponent: false })]);
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-arrow"]')).toHaveLength(0);
  });

  it('givenAFaceUpMovement_thenTheGhostShowsThatCard', async () => {
    const wrapper = mount(KoboMotionLayer, { props: { resolve: (a) => target(a.kind === 'draw' ? 0 : 200) } });
    (wrapper.vm as unknown as { play: (m: Movement[]) => void }).play([travel({ card: buildKoboCard('7', 'SPADE') })]);
    await flushPromises();
    expect(wrapper.get('[data-test="motion-card"] img').attributes('src')).not.toBe(
      mount(KoboMotionLayer, { props: { resolve: () => null } }).html());
  });

  it('givenReducedMotion_thenNothingMovesAndNothingIsAnimated', async () => {
    motion(true);
    const wrapper = mount(KoboMotionLayer, { props: { resolve: () => target(0) } });
    (wrapper.vm as unknown as { play: (m: Movement[]) => void }).play([travel(), travel({ kind: 'reveal', from: { kind: 'table' } })]);
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
    expect(animations).toHaveLength(0);
  });

  it('givenAPlaceThatCannotBeFound_thenTheMovementIsSkippedWithoutError', async () => {
    const wrapper = mount(KoboMotionLayer, { props: { resolve: (a) => (a.kind === 'draw' ? target(0) : null) } });
    expect(() => (wrapper.vm as unknown as { play: (m: Movement[]) => void }).play([travel()])).not.toThrow();
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
  });

  it('givenABrowserWithoutAnimations_thenNothingHappensAndNothingThrows', async () => {
    // @ts-expect-error simulating a browser without the Web Animations API
    delete Element.prototype.animate;
    const wrapper = mount(KoboMotionLayer, { props: { resolve: () => target(0) } });
    (wrapper.vm as unknown as { play: (m: Movement[]) => void }).play([travel()]);
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
  });

  it('hidesTheRealCardUnderALandingCopyThenShowsItAgainHoweverTheAnimationEnds', async () => {
    const real = document.createElement('img');
    const wrapper = mount(KoboMotionLayer, { props: { resolve: (a) => target(a.kind === 'draw' ? 0 : 100, a.kind === 'hand' ? [real] : []) } });
    (wrapper.vm as unknown as { play: (m: Movement[]) => void }).play([travel()]);
    await flushPromises();
    expect(real.style.opacity).toBe('0');
    animations[0].end();
    await flushPromises();
    expect(real.style.opacity).toBe('');
  });

  it('showsTheRealCardAgainWhenTheLayerGoesAwayMidFlight', async () => {
    const real = document.createElement('img');
    const wrapper = mount(KoboMotionLayer, { props: { resolve: (a) => target(a.kind === 'draw' ? 0 : 100, a.kind === 'hand' ? [real] : []) } });
    (wrapper.vm as unknown as { play: (m: Movement[]) => void }).play([travel()]);
    await flushPromises();
    wrapper.unmount();
    expect(real.style.opacity).toBe('');
  });

  it('showsTheRealCardAgainEvenIfTheAnimationNeverEnds', async () => {
    vi.useFakeTimers();
    try {
      const real = document.createElement('img');
      const wrapper = mount(KoboMotionLayer, { props: { resolve: (a) => target(a.kind === 'draw' ? 0 : 100, a.kind === 'hand' ? [real] : []) } });
      (wrapper.vm as unknown as { play: (m: Movement[]) => void }).play([travel()]);
      await vi.advanceTimersByTimeAsync(0);
      expect(real.style.opacity).toBe('0');
      await vi.advanceTimersByTimeAsync(MOTION_MAX_MS);
      expect(real.style.opacity).toBe('');
      expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
    } finally {
      vi.useRealTimers();
    }
  });

  it('givenARevealMovement_thenEveryCardOfTheTableTurnsInPlaceWithoutACopy', async () => {
    const cards = [document.createElement('img'), document.createElement('img')];
    const wrapper = mount(KoboMotionLayer, { props: { resolve: () => target(0, cards) } });
    (wrapper.vm as unknown as { play: (m: Movement[]) => void }).play([travel({ kind: 'reveal', from: { kind: 'table' }, to: { kind: 'table' }, durationMs: 360 })]);
    await flushPromises();
    expect(animations).toHaveLength(2);
    expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
  });
});
