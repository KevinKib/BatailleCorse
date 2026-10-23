import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import CardMotionLayer from './CardMotionLayer.vue';
import type { MotionTarget } from '../dom/rect';
import type { TableMovement } from '../core/movement';
import type Card from '../../model/Card';

// The flights of the shared layer, as Kobo's table first drove them (these cases came from the test of Kobo's thin
// wrapper of the layer, which is gone): the copy, its arrow, what it hides and shows under it, the queue wait, the
// rings and their durations (the layer's defaults), reduced motion, a browser without animations, cleaning up.
// Places are strings: 'draw' and 'hand' are the two ends of a flight, 'slotA' and 'slotB' are ringable places.
type Move = TableMovement<string>;
const MOTION_MAX_MS = 2600;
const MARK_MS = 2000;
const MARK_REDUCED_MS = 1500;
const card = (rank: string, suit: string): Card => ({ rank, suit, name: `${suit}_${rank}` });

const travel = (over: Partial<Move> = {}): Move => ({
  kind: 'travel', from: 'draw', to: 'hand', card: null, flip: false, actor: 1, byOpponent: true,
  turnDown: false, delayMs: 150, durationMs: 700, holdAfterMs: 300, marks: [], ...over,
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
type Play = (m: Move[], o?: { marksOnly?: boolean; startInMs?: number }) => void;
const mountLayer = (resolve: (a: string) => MotionTarget | null) => mount(CardMotionLayer<string>, { props: { resolve } });
const playOn = (wrapper: ReturnType<typeof mount>) => (wrapper.vm as unknown as { play: Play }).play;
const at = (a: string) => target(a === 'draw' ? 0 : 100);

describe('CardMotionLayer flights', () => {
  const original = Element.prototype.animate;
  beforeEach(() => { stubAnimate(); motion(false); });
  afterEach(() => { Element.prototype.animate = original; vi.restoreAllMocks(); });

  it('isHiddenFromAssistiveTechnologyAndTakesNoPointerEvent', () => {
    const wrapper = mountLayer(() => null);
    expect(wrapper.get('[data-test="motion-layer"]').attributes('aria-hidden')).toBe('true');
  });

  it('givenAMovement_thenACardFliesWithinTheLimitAndIsRemovedWhenLanded', async () => {
    const wrapper = mountLayer(a => target(a === 'draw' ? 0 : 100));
    playOn(wrapper)([travel()]);
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(1);
    const options = animateCalls[0].options;
    expect((options.duration as number) + (options.delay as number)).toBeLessThan(MOTION_MAX_MS);
    animations[0].end();
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
  });

  it('givenAnOpponentsMove_thenAFaintArrowShowsFromWhereToWhere', async () => {
    const wrapper = mountLayer(a => target(a === 'draw' ? 0 : 200));
    playOn(wrapper)([travel()]);
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-arrow"]')).toHaveLength(1);
  });

  it('givenMyOwnMove_thenThereIsNoArrow', async () => {
    const wrapper = mountLayer(a => target(a === 'draw' ? 0 : 200));
    playOn(wrapper)([travel({ byOpponent: false })]);
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-arrow"]')).toHaveLength(0);
  });

  it('givenAFaceUpMovement_thenTheGhostShowsThatCard', async () => {
    const wrapper = mountLayer(a => target(a === 'draw' ? 0 : 200));
    playOn(wrapper)([travel({ card: card('7', 'SPADE') })]);
    await flushPromises();
    expect(wrapper.get('[data-test="motion-card"] img').attributes('src')).not.toBe(mountLayer(() => null).html());
  });

  it('givenReducedMotion_thenNothingMovesAndNothingIsAnimated', async () => {
    motion(true);
    const wrapper = mountLayer(() => target(0));
    playOn(wrapper)([travel(), travel({ kind: 'reveal', from: 'table' })]);
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
    expect(animations).toHaveLength(0);
  });

  it('givenAPlaceThatCannotBeFound_thenTheMovementIsSkippedWithoutError', async () => {
    const wrapper = mountLayer(a => (a === 'draw' ? target(0) : null));
    expect(() => playOn(wrapper)([travel()])).not.toThrow();
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
  });

  it('givenABrowserWithoutAnimations_thenNothingHappensAndNothingThrows', async () => {
    // @ts-expect-error simulating a browser without the Web Animations API
    delete Element.prototype.animate;
    const wrapper = mountLayer(() => target(0));
    playOn(wrapper)([travel()]);
    await flushPromises();
    expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
  });

  it('hidesTheRealCardUnderALandingCopyThenShowsItAgainHoweverTheAnimationEnds', async () => {
    const real = document.createElement('img');
    const wrapper = mountLayer(a => target(a === 'draw' ? 0 : 100, a === 'hand' ? [real] : []));
    playOn(wrapper)([travel()]);
    await flushPromises();
    expect(real.style.opacity).toBe('0');
    animations[0].end();
    await flushPromises();
    expect(real.style.opacity).toBe('');
  });

  describe('a movement queued behind another one', () => {
    it('hidesTheLandingPlaceAtOnceNotWhenItsTurnComes_andLeavesTheCopyInvisibleUntilThen', async () => {
      const real = document.createElement('img');
      const wrapper = mountLayer(a => target(a === 'draw' ? 0 : 100, a === 'hand' ? [real] : []));
      playOn(wrapper)([travel()], { startInMs: 2000 });
      await flushPromises();
      expect(real.style.opacity).toBe('0');
      expect(animateCalls[0].options.delay).toBe(2000 + 150);
      // Another animation keeps the copy invisible while it waits for its turn.
      const wait = animateCalls.find(c => c.options.delay === 0 && Number(c.options.duration) >= 2000);
      expect(wait).toBeDefined();
    });

    it('showsWhatTheLandingPlaceShowedBeforeUntilTheCopyLands', async () => {
      const real = document.createElement('img');
      const wrapper = mountLayer(a => target(a === 'draw' ? 0 : 100, a === 'hand' ? [real] : []));
      playOn(wrapper)([travel({ restsOn: card('QUEEN', 'CLUB') })], { startInMs: 2000 });
      await flushPromises();
      expect(wrapper.findAll('[data-test="motion-under"]')).toHaveLength(1);
      animations[0].end();
      await flushPromises();
      expect(wrapper.findAll('[data-test="motion-under"]')).toHaveLength(0);
    });

    it('showsABackOnASourceSlotUntilItsCardLeaves', async () => {
      vi.useFakeTimers();
      try {
        const wrapper = mountLayer(at);
        playOn(wrapper)([travel({ leavesBack: true })], { startInMs: 1000 });
        await vi.advanceTimersByTimeAsync(0);
        expect(wrapper.findAll('[data-test="motion-under"]')).toHaveLength(1);
        await vi.advanceTimersByTimeAsync(1000 + 150 + 100);
        expect(wrapper.findAll('[data-test="motion-under"]')).toHaveLength(0);
      } finally {
        vi.useRealTimers();
      }
    });

    it('givenNoWait_thenNoExtraAnimationAndNothingUnderTheCopy', async () => {
      const wrapper = mountLayer(at);
      playOn(wrapper)([travel({ byOpponent: false })]);
      await flushPromises();
      expect(animateCalls).toHaveLength(1);
      expect(wrapper.findAll('[data-test="motion-under"]')).toHaveLength(0);
    });

    it('givenAFaceUpCardThatTurnsDownOnArrival_thenTheCopyCarriesBothFacesAndTurnsAtTheEnd', async () => {
      const wrapper = mountLayer(at);
      playOn(wrapper)([travel({ card: card('QUEEN', 'CLUB'), turnDown: true, byOpponent: false })]);
      await flushPromises();
      expect(wrapper.find('[data-test="motion-card"] [data-face="front"]').exists()).toBe(true);
      expect(wrapper.find('[data-test="motion-card"] [data-face="back"]').exists()).toBe(true);
    });
  });

  it('showsTheRealCardAgainWhenTheLayerGoesAwayMidFlight', async () => {
    const real = document.createElement('img');
    const wrapper = mountLayer(a => target(a === 'draw' ? 0 : 100, a === 'hand' ? [real] : []));
    playOn(wrapper)([travel()]);
    await flushPromises();
    wrapper.unmount();
    expect(real.style.opacity).toBe('');
  });

  it('showsTheRealCardAgainEvenIfTheAnimationNeverEnds', async () => {
    vi.useFakeTimers();
    try {
      const real = document.createElement('img');
      const wrapper = mountLayer(a => target(a === 'draw' ? 0 : 100, a === 'hand' ? [real] : []));
      playOn(wrapper)([travel()]);
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
    const wrapper = mountLayer(() => target(0, cards));
    playOn(wrapper)([travel({ kind: 'reveal', from: 'table', to: 'table', durationMs: 360, delayMs: 0, holdAfterMs: 0 })]);
    await flushPromises();
    expect(animations).toHaveLength(2);
    expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
  });

  describe('tempo and markers', () => {
    const marks = (wrapper: ReturnType<typeof mount>) => wrapper.findAll('[data-test="motion-mark"]');
    const resolve = (a: string) => target(a === 'draw' ? 0 : 100);

    it('holdsTheBeforeAndAfterOfAFlightInTheSameAnimation', async () => {
      const wrapper = mountLayer(resolve);
      playOn(wrapper)([travel({ delayMs: 250, durationMs: 1000, holdAfterMs: 450 })]);
      await flushPromises();
      const { options } = animateCalls[0];
      expect(options.delay).toBe(250);
      expect(options.duration).toBe(1450);
    });

    it('ringsEverySlotAMoveTouchesAndRemovesTheRingsAfterTheMarkDuration', async () => {
      vi.useFakeTimers();
      try {
        const wrapper = mountLayer(a => target(a.startsWith('slot') ? 100 + a.length : 0));
        playOn(wrapper)([travel({ from: 'slotA', to: 'slotB', marks: ['slotA', 'slotB'] })]);
        await vi.advanceTimersByTimeAsync(0);
        expect(marks(wrapper)).toHaveLength(2);
        expect(marks(wrapper).map(m => m.attributes('data-role')).sort()).toEqual(['from', 'to']);
        await vi.advanceTimersByTimeAsync(MARK_MS - 100);
        expect(marks(wrapper)).toHaveLength(2);
        await vi.advanceTimersByTimeAsync(200);
        expect(marks(wrapper)).toHaveLength(0);
      } finally {
        vi.useRealTimers();
      }
    });

    it('keepsTheRingsLongerThanAMoveThatLastsLongerThanTheMarkDuration', async () => {
      vi.useFakeTimers();
      try {
        const wrapper = mountLayer(resolve);
        playOn(wrapper)([travel({ marks: ['slotA'], delayMs: 500, durationMs: 1500, holdAfterMs: 500 })]);
        await vi.advanceTimersByTimeAsync(MARK_MS + 100);
        expect(marks(wrapper)).toHaveLength(1);
        await vi.advanceTimersByTimeAsync(600);
        expect(marks(wrapper)).toHaveLength(0);
      } finally {
        vi.useRealTimers();
      }
    });

    it('givenReducedMotion_thenTheRingsStayAsStillOutlinesForTheShorterDuration', async () => {
      vi.useFakeTimers();
      try {
        motion(true);
        const wrapper = mountLayer(resolve);
        playOn(wrapper)([travel({ marks: ['slotA', 'slotB'] })]);
        await vi.advanceTimersByTimeAsync(0);
        expect(marks(wrapper)).toHaveLength(2);
        expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
        expect(animations).toHaveLength(0);
        expect(marks(wrapper)[0].attributes('data-still')).toBe('true');
        await vi.advanceTimersByTimeAsync(MARK_REDUCED_MS - 50);
        expect(marks(wrapper)).toHaveLength(2);
        await vi.advanceTimersByTimeAsync(100);
        expect(marks(wrapper)).toHaveLength(0);
      } finally {
        vi.useRealTimers();
      }
    });

    it('givenMarksOnly_thenNoCardFliesButTheRingsShowAtOnce', async () => {
      const wrapper = mountLayer(resolve);
      playOn(wrapper)([travel({ marks: ['slotA'] })], { marksOnly: true });
      await flushPromises();
      expect(marks(wrapper)).toHaveLength(1);
      expect(wrapper.findAll('[data-test="motion-card"]')).toHaveLength(0);
      expect(animations).toHaveLength(0);
    });

    it('givenABrowserWithoutAnimations_thenTheRingsStillShow', async () => {
      // @ts-expect-error simulating a browser without the Web Animations API
      delete Element.prototype.animate;
      const wrapper = mountLayer(resolve);
      playOn(wrapper)([travel({ marks: ['slotA'] })]);
      await flushPromises();
      expect(marks(wrapper)).toHaveLength(1);
    });

    it('skipsAMarkWhosePlaceCannotBeFound', async () => {
      const wrapper = mountLayer(() => null);
      expect(() => playOn(wrapper)([travel({ marks: ['slotA'] })])).not.toThrow();
      await flushPromises();
      expect(marks(wrapper)).toHaveLength(0);
    });

    it('takesNoPointerEventAndStaysHiddenFromAssistiveTechnology', async () => {
      const wrapper = mountLayer(resolve);
      playOn(wrapper)([travel({ marks: ['slotA'] })]);
      await flushPromises();
      expect(marks(wrapper)[0].element.closest('[aria-hidden="true"]')).not.toBeNull();
    });

    it('removesTheRingsWhenTheLayerGoesAway', async () => {
      vi.useFakeTimers();
      try {
        const wrapper = mountLayer(resolve);
        playOn(wrapper)([travel({ marks: ['slotA'] })]);
        await vi.advanceTimersByTimeAsync(0);
        wrapper.unmount();
        await vi.advanceTimersByTimeAsync(MARK_MS + 100);
        expect(vi.getTimerCount()).toBe(0);
      } finally {
        vi.useRealTimers();
      }
    });
  });
});
