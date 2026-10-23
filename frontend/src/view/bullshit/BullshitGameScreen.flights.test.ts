import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { setActivePinia, createPinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import PrimeVue from 'primevue/config';

// Characterisation of the two flights of the Bullshit table (my discard to the pile, the pile to its taker): the
// keyframes, durations, delays, easing, level and count of the ghosts that reach the Web Animations API, and when the
// end screen shows. Written on the code as it was before the shared animation module: the migration must keep every
// value below. The screen runs for real; only `Element.animate` and the measured rectangles are faked.
vi.mock('../../composables/useCardSound', () => ({ cardSound: { play: vi.fn() } }));

import BullshitGameScreen from './BullshitGameScreen.vue';
import { useBullshitStore, REVEAL_HOLD_MS } from '../../state/Bullshit.store';
import { END_SCREEN_DELAY_MS } from '../../composables/useEndScreen';
import type { BullshitState } from '../../model/bullshit/BullshitState';

const hand = [
  { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' },
  { rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' },
];
function state(winner: string | null = null, myHand = hand): BullshitState {
  return {
    started: true, id: 'g1', gameType: 'bullshit', myHand, availableActions: ['DISCARD'],
    players: [{ id: '0', handCount: 2, isCurrentPlayer: true, name: 'Alice' }, { id: '1', handCount: 3, isCurrentPlayer: false }],
    currentTarget: { label: 'ACE' }, discardPileSize: 4, table: { state: 'NO_CLAIM' },
    pendingWinner: { state: 'NONE' },
    outcome: winner === null ? { status: 'ONGOING' } : { status: 'FINISHED', winnerId: winner },
  } as BullshitState;
}
const four = [...hand, ...hand];
const call = (pickerSeat: number) => ({
  type: 'event' as const, eventType: 'CALL_BULLSHIT', message: '',
  eventData: { callerSeat: 1, claimantSeat: 0, truthful: pickerSeat === 1, pickerSeat, revealedCards: four },
});
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/games', name: 'games', component: { template: '<div/>' } }] });

const rect = (left: number, top: number, width: number, height: number) =>
  ({ left, top, width, height, right: left + width, bottom: top + height, x: left, y: top }) as DOMRect;
function measure(el: Element | null, r: DOMRect) {
  expect(el).toBeTruthy();
  vi.spyOn(el as Element, 'getBoundingClientRect').mockReturnValue(r);
}

interface Flight { el: HTMLElement; frames: Keyframe[]; options: KeyframeAnimationOptions; finish: () => void }
let flights: Flight[] = [];
let reduced = false;
const original = Element.prototype.animate;
const isGhost = (el: Element) => el.matches('[data-test="flying-card"], [data-test="flying-pile"], [data-ghost]');

// The level a ghost is drawn at: its own, or its layer's.
function levelOf(el: HTMLElement): string {
  for (let node: HTMLElement | null = el; node; node = node.parentElement) {
    if (node.style.zIndex) return node.style.zIndex;
  }
  return '';
}
// Offsets and durations are compared to the micro-second: the same numbers however they are computed.
function normal(f: Flight) {
  return {
    frames: f.frames.map(fr => ({ ...fr, ...(fr.offset === undefined ? {} : { offset: Math.round((fr.offset as number) * 1e6) / 1e6 }) })),
    options: { ...f.options, duration: Math.round((f.options.duration as number) * 1e6) / 1e6 },
  };
}

function setup(initial: BullshitState = state()) {
  const store = useBullshitStore();
  store.applyEvent({ type: 'seat-change', seat: 0 });
  store.applyEvent({ type: 'state-update', state: initial });
  const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] }, attachTo: document.body });
  const root = wrapper.element as HTMLElement;
  measure(root.querySelector('[data-test="hand-card-0"]'), rect(30, 500, 60, 90));
  measure(root.querySelector('[data-test="hand-card-1"]'), rect(100, 500, 60, 90));
  measure(root.querySelector('.pile-well > .playing_card'), rect(200, 100, 90, 130));
  measure(root.querySelector('[data-seat="1"] .seat-card'), rect(500, 20, 40, 60));
  measure(root.querySelector('.my-zone'), rect(300, 600, 200, 120));
  return { store, wrapper };
}
const tick = async (ms: number) => { vi.advanceTimersByTime(ms); await nextTick(); await flushPromises(); };
const finishAll = async () => { flights.forEach(f => f.finish()); await flushPromises(); };

describe('Bullshit flights (characterisation)', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    localStorage.clear();
    flights = [];
    reduced = false;
    vi.useFakeTimers();
    window.matchMedia = ((q: string) => ({ matches: reduced, media: q, addEventListener() {}, removeEventListener() {} })) as typeof window.matchMedia;
    Element.prototype.animate = function (this: Element, frames: Keyframe[], options: KeyframeAnimationOptions) {
      let finish!: () => void;
      const finished = new Promise<void>(r => { finish = r; });
      if (isGhost(this)) flights.push({ el: this as HTMLElement, frames, options, finish });
      return { finished, cancel: vi.fn() } as unknown as Animation;
    } as typeof Element.prototype.animate;
    HTMLElement.prototype.animate = Element.prototype.animate;
  });
  afterEach(() => {
    Element.prototype.animate = original;
    HTMLElement.prototype.animate = original;
    vi.useRealTimers();
    document.body.innerHTML = '';
  });

  async function discardBoth() {
    const ctx = setup();
    vi.spyOn(ctx.store, 'discard').mockImplementation(() => {});
    await ctx.wrapper.get('[data-test="hand-card-0"]').trigger('click');
    await ctx.wrapper.get('[data-test="hand-card-1"]').trigger('click');
    await ctx.wrapper.get('[data-test="discard"]').trigger('click');
    await flushPromises();
    return ctx;
  }

  describe('my discard to the pile', () => {
    it('flies one copy per selected card from its hand place to the pile, tilted and fading', async () => {
      const { wrapper } = await discardBoth();
      expect(flights).toHaveLength(2);
      const easing = 'cubic-bezier(0.22, 0.8, 0.3, 1)';
      // The pile card is 90 wide, a hand card 60: the copies grow by 1.5 and land on the pile's top left corner.
      const landing = (tilt: number) => `translate(200px, 100px) scale(1.5) rotate(${tilt}deg)`;
      expect(flights.map(normal)).toEqual([
        {
          frames: [
            { transform: 'translate(30px, 500px) scale(1) rotate(0deg)', opacity: 1 },
            { transform: landing(3), opacity: 1, offset: 0.92 },
            { transform: landing(3), opacity: 0 },
          ],
          options: { duration: 380, delay: 0, easing, fill: 'both' },
        },
        {
          frames: [
            { transform: 'translate(100px, 500px) scale(1) rotate(0deg)', opacity: 1 },
            { transform: landing(-4), opacity: 1, offset: 0.92 },
            { transform: landing(-4), opacity: 0 },
          ],
          options: { duration: 380, delay: 70, easing, fill: 'both' },
        },
      ]);
      wrapper.unmount();
    });

    it('draws each copy as big as its hand place, above the table (level 3000)', async () => {
      const { wrapper } = await discardBoth();
      expect(flights.map(f => [f.el.style.width, f.el.style.height, levelOf(f.el)])).toEqual([
        ['60px', '90px', '3000'], ['60px', '90px', '3000'],
      ]);
      wrapper.unmount();
    });

    // Today the copy is taken from the place `hand-card-<n>` where n is the card's index in the hand as the server
    // sent it, while the places are numbered in the order they are displayed (sorted). With a hand that is not in
    // display order the copy starts from, and shows, the card sitting at that other place. Kept as it is: any
    // change of it is a visible change for the owner to decide.
    it('draws each copy from the place numbered by the card index in the hand, shown or not in that order', async () => {
      const reversed = [hand[1], hand[0]];             // sent as KING, ACE; displayed as ACE (0), KING (1)
      const ctx = setup(state(null, reversed));
      vi.spyOn(ctx.store, 'discard').mockImplementation(() => {});
      await ctx.wrapper.get('[data-test="hand-card-0"]').trigger('click');   // the ace, which is index 1 of the hand
      await ctx.wrapper.get('[data-test="discard"]').trigger('click');
      await flushPromises();
      const root = ctx.wrapper.element as HTMLElement;
      const pictureOf = (el: Element) => (el.matches('img') ? el : el.querySelector('img'))?.getAttribute('src');
      expect(flights).toHaveLength(1);
      expect(flights[0].frames[0].transform).toBe('translate(100px, 500px) scale(1) rotate(0deg)');
      expect(pictureOf(flights[0].el)).toBeTruthy();
      expect(pictureOf(flights[0].el)).toBe(pictureOf(root.querySelector('[data-test="hand-card-1"]')!));
      expect(pictureOf(flights[0].el)).not.toBe(pictureOf(root.querySelector('[data-test="hand-card-0"]')!));
      ctx.wrapper.unmount();
    });

    it('removes the copies once they have landed', async () => {
      const { wrapper } = await discardBoth();
      await finishAll();
      expect(document.querySelectorAll('[data-test="flying-card"], [data-ghost]')).toHaveLength(0);
      wrapper.unmount();
    });

    it('flies nothing under reduced motion', async () => {
      reduced = true;
      const { wrapper } = await discardBoth();
      expect(flights).toHaveLength(0);
      wrapper.unmount();
    });
  });

  describe('the pile to its taker', () => {
    async function take(pickerSeat: number) {
      const ctx = setup();
      ctx.store.applyEvent(call(pickerSeat));
      await nextTick();
      await tick(REVEAL_HOLD_MS);
      return ctx;
    }

    it('flies three face-down copies, staggered, shrinking onto the centre of the taker seat', async () => {
      const { wrapper } = await take(1);
      expect(flights).toHaveLength(3);
      const easing = 'cubic-bezier(0.5, 0, 0.75, 0.3)';
      // 40 / 90 of the size, centred on the seat card: 500 + 20 - 45 = 475, 20 + 30 - 65 = -15.
      const s = 40 / 90;
      const landing = (tilt: number) => `translate(475px, -15px) scale(${s}) rotate(${tilt}deg)`;
      expect(flights.map(normal)).toEqual([0, 1, 2].map(i => ({
        frames: [
          { transform: 'translate(200px, 100px) scale(1) rotate(0deg)', opacity: 1 },
          { transform: landing([0, -6, 12][i]), opacity: 1, offset: 0.9 },
          { transform: landing([0, -6, 12][i]), opacity: 0 },
        ],
        options: { duration: 460, delay: i * 90, easing, fill: 'both' },
      })));
      expect(flights.map(f => [f.el.style.width, f.el.style.height, levelOf(f.el)])).toEqual([
        ['90px', '130px', '3000'], ['90px', '130px', '3000'], ['90px', '130px', '3000'],
      ]);
      wrapper.unmount();
    });

    it('lands on my own zone when I take the pile, never enlarged', async () => {
      const { wrapper } = await take(0);
      expect(flights).toHaveLength(3);
      // The zone is wider than the pile card: scale stops at 1. Centred: 300 + 100 - 45 = 355, 600 + 60 - 65 = 595.
      expect(flights[0].frames[1].transform).toBe('translate(355px, 595px) scale(1) rotate(0deg)');
      wrapper.unmount();
    });

    it('flies nothing under reduced motion', async () => {
      reduced = true;
      const { wrapper } = await take(1);
      expect(flights).toHaveLength(0);
      wrapper.unmount();
    });
  });

  describe('the end screen', () => {
    it('waits for the last copy to land, then the usual beat', async () => {
      const { store, wrapper } = setup();
      store.applyEvent({ type: 'state-update', state: state('1') });
      store.applyEvent(call(1));
      await nextTick();
      await tick(REVEAL_HOLD_MS);                     // the verdict goes away, the pile flies
      expect(flights).toHaveLength(3);
      await tick(639);                                // the last copy (180 ms + 460 ms) has not landed yet
      expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
      await tick(1);
      await finishAll();                              // it lands
      await tick(END_SCREEN_DELAY_MS - 1);
      expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
      await tick(1);
      expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
      wrapper.unmount();
    });

    it('shows after the beat alone when nothing flies (reduced motion)', async () => {
      reduced = true;
      const { store, wrapper } = setup();
      store.applyEvent({ type: 'state-update', state: state('1') });
      store.applyEvent(call(1));
      await nextTick();
      await tick(REVEAL_HOLD_MS);
      expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
      await tick(END_SCREEN_DELAY_MS);
      expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
      wrapper.unmount();
    });
  });
});
