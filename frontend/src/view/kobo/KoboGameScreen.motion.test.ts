import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createMemoryHistory, RouterView } from 'vue-router';
import { defineComponent, h } from 'vue';
import PrimeVue from 'primevue/config';
import { createAppRouter } from '../../router';
import { useKoboStore } from '../../state/Kobo.store';
import { buildKoboCard, buildKoboPlayer, buildKoboSlot, buildKoboState } from '../../model/fixtures/kobo';
import { useKoboSound } from '../../composables/useKoboSound';

// Characterisation of what Kobo's screen asks of the card layer. A scripted log is driven through the real screen
// with the shared layer replaced by a recorder; every `play(movements, options)` call, the place each anchor measures
// to (the recorder asks the screen's resolver), the `holdDisplayed` arguments and the sounds are written down in
// order. The recording (the snapshot file next to this test) was taken on the hand-written loop of the screen, before
// it moved to the shared `useTableMotion`; the migration must leave it unchanged.
//
// The `still` option the shared orchestrator also hands to the layer is not recorded: the layer takes the same value
// from the reduced-motion media query on its own, so what is on screen is the same.

const trace = vi.hoisted(() => ({ entries: [] as unknown[] }));

vi.mock('../../animation/vue/CardMotionLayer.vue', async () => {
  const { defineComponent, h } = await import('vue');
  type Rect = { left: number; top: number; width: number; height: number };
  type Target = { rect: Rect; cards: unknown[] } | null;
  const describeTarget = (t: Target) => (t ? { rect: t.rect, cards: t.cards.length } : null);
  return {
    default: defineComponent({
      name: 'CardMotionLayerRecorder',
      props: ['resolve', 'markMs', 'markReducedMs', 'markStyle', 'arrowId', 'zIndex'],
      setup(props: { resolve: (a: unknown) => Target } & Record<string, unknown>, { expose }) {
        trace.entries.push({ layer: {
          markMs: props.markMs, markReducedMs: props.markReducedMs, markStyle: props.markStyle ?? 'glow',
          arrowId: props.arrowId,
        } });
        expose({
          play(movements: { from: unknown; to: unknown; marks: unknown[] }[], options: { marksOnly?: boolean; startInMs?: number } = {}) {
            trace.entries.push({
              play: {
                options: { marksOnly: options.marksOnly === true, startInMs: options.startInMs ?? 0 },
                movements,
                places: movements.map(m => ({
                  from: describeTarget(props.resolve(m.from)),
                  to: describeTarget(props.resolve(m.to)),
                  marks: m.marks.map(a => describeTarget(props.resolve(a))),
                })),
              },
            });
            return { endsInMs: 0 };
          },
        });
        return () => h('div', { 'data-test': 'motion-layer', 'aria-hidden': 'true' });
      },
    }),
  };
});

const EPOCH = 1_700_000_000_000;
const QUEEN = buildKoboCard('QUEEN', 'CLUB');
const SEVEN = buildKoboCard('7', 'SPADE');
const FOUR = buildKoboCard('4', 'HEART');
const NINE = buildKoboCard('9', 'SPADE');
const TWO = buildKoboCard('2', 'CLUB');

const slotsWithRevisions = (base: number, first = base) =>
  [0, 1, 2, 3].map(s => buildKoboSlot(s, { revision: s === 0 ? first : base + s }));
const table = (version: number, overrides = {}, myFirstRevision = 1) => buildKoboState({
  phase: 'DRAW', currentSeat: 1, version, discardTop: FOUR, availableActions: ['MATCH_DISCARD'], ...overrides,
  players: [
    buildKoboPlayer(0, { slots: slotsWithRevisions(1, myFirstRevision) }),
    buildKoboPlayer(1, { current: true, slots: slotsWithRevisions(10) }),
    buildKoboPlayer(2, { slots: slotsWithRevisions(20) }),
  ],
});
const data = (overrides: Record<string, unknown>) => ({
  seat: 1, action: 'DRAW', slot: null, targetSeat: null, targetSlot: null, card: null,
  givenSlot: null, success: null, barrierOpened: null, ...overrides,
});

// Each element measures differently, from its own test id (or seat), so a wrong place shows in the recording.
const hash = (text: string) => [...text].reduce((acc, ch) => (acc * 31 + ch.charCodeAt(0)) % 997, 7);
function measure(this: Element) {
  const key = this.getAttribute('data-test') ?? `${this.className}|${this.getAttribute('data-seat') ?? ''}`;
  const h = hash(key);
  return { left: h, top: h + 1, width: 40 + (h % 7), height: 58 + (h % 5), right: 0, bottom: 0, x: 0, y: 0, toJSON() {} };
}

describe('KoboGameScreen motion (characterisation)', () => {
  const original = { animate: Element.prototype.animate, rect: Element.prototype.getBoundingClientRect, media: window.matchMedia };
  const media = (reduce: boolean) => {
    window.matchMedia = ((q: string) => ({ matches: reduce && q.includes('reduce'), media: q, addEventListener() {}, removeEventListener() {} })) as typeof window.matchMedia;
  };

  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(() => new Promise(() => {})));
    setActivePinia(createPinia());
    localStorage.clear();
    trace.entries.length = 0;
    vi.useFakeTimers({ now: EPOCH });
    Element.prototype.animate = (() => ({ finished: new Promise(() => {}), cancel() {} })) as unknown as typeof Element.prototype.animate;
    Element.prototype.getBoundingClientRect = measure;
    media(false);
    useKoboSound().enabled.value = false;
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    Element.prototype.animate = original.animate;
    Element.prototype.getBoundingClientRect = original.rect;
    window.matchMedia = original.media;
  });

  async function mountTable() {
    const store = useKoboStore();
    localStorage.setItem('kobo:tokens:k1', JSON.stringify({ 0: 'tok' }));
    store.applyEvent({ type: 'seat-change', seat: 0 });
    store.applyEvent({ type: 'state-update', state: table(1) });
    vi.spyOn(store, 'hydrate').mockImplementation(() => {});
    vi.spyOn(store, 'restore').mockImplementation(() => {});
    const realHold = store.holdDisplayed;
    vi.spyOn(store, 'holdDisplayed').mockImplementation(hold => {
      trace.entries.push({
        hold: {
          slots: Object.fromEntries(Object.entries(hold.slots).map(([k, v]) => [k, { revision: v.revision, until: v.until - EPOCH }])),
          discard: hold.discard ? { rank: hold.discard.rank, until: hold.discard.until - EPOCH } : null,
        },
      });
      realHold(hold);
    });
    vi.spyOn(useKoboSound(), 'play').mockImplementation(kind => { trace.entries.push({ sound: kind }); });
    const router = createAppRouter(createMemoryHistory());
    await router.push('/games/kobo/room/k1');
    await router.isReady();
    const wrapper = mount(defineComponent({ render: () => h(RouterView) }), { global: { plugins: [router, PrimeVue] } });
    await flushPromises();
    return { store, wrapper };
  }

  const log = (store: ReturnType<typeof useKoboStore>, eventType: string, overrides: Record<string, unknown> = {}) =>
    store.applyEvent({ type: 'event', eventType, message: '', eventData: data({ action: eventType, ...overrides }) });

  it('plays the scripted log exactly as the hand-written loop did', async () => {
    const { store } = await mountTable();
    // Lets the one-frame wait and the next tick run, then moves the clock far enough for the queue to be empty.
    const step = async (name: string, act: () => void) => {
      trace.entries.push({ step: name });
      act();
      await flushPromises();
      await vi.advanceTimersByTimeAsync(40);
      await vi.advanceTimersByTimeAsync(20_000);
    };
    const state = (s: ReturnType<typeof table>) => store.applyEvent({ type: 'state-update', state: s });

    await step('an opponent draws', () => { state(table(2)); log(store, 'DRAW', { seat: 1 }); });
    await step('I draw (my face comes with the state)', () => {
      state(table(3, { currentSeat: 0, phase: 'DECISION', hasDrawn: true, drawnCard: QUEEN }));
      log(store, 'DRAW', { seat: 0 });
    });
    await step('I swap the drawn card into my slot 0', () => {
      state(table(4, { currentSeat: 0, phase: 'TURN_END', hasDrawn: false, drawnCard: null, discardTop: SEVEN }, 2));
      log(store, 'SWAP', { seat: 0, slot: 0, card: SEVEN });
    });
    await step('a bot discards the card it drew', () => {
      state(table(5, { discardTop: NINE }));
      log(store, 'DISCARD_DRAWN', { seat: 1, card: NINE });
    });
    await step('a bot peeks at another seat', () => log(store, 'PEEK', { seat: 1, targetSeat: 2, targetSlot: 1 }));
    await step('a blind swap with my slot', () => log(store, 'BLIND_SWAP', { seat: 1, slot: 0, targetSeat: 0, targetSlot: 1 }));
    await step('a king swap between two bots', () => log(store, 'KING_SWAP', { seat: 2, slot: 3, targetSeat: 1, targetSlot: 2 }));
    await step('a match of mine on another seat, with a gift', () => {
      state(table(6, { discardTop: TWO }));
      log(store, 'MATCH', { seat: 0, targetSeat: 1, targetSlot: 2, givenSlot: 1, success: true, card: NINE });
    });
    await step('a bot matches my card', () => {
      state(table(7, { discardTop: TWO }, 6));
      log(store, 'MATCH', { seat: 1, targetSeat: 0, targetSlot: 0, givenSlot: 1, success: true, card: NINE });
    });
    await step('a missed match', () => log(store, 'MATCH', { seat: 2, success: false }));
    await step('an event with no picture', () => log(store, 'END_TURN', { seat: 1 }));
    await step('the round ends', () => {
      state(table(8, { phase: 'ROUND_OVER', revealed: true, availableActions: [] }));
      log(store, 'ROUND_END', { seat: 1 });
    });
    await step('a burst of six events', () => {
      state(table(9));
      log(store, 'DRAW', { seat: 1 });
      log(store, 'DISCARD_DRAWN', { seat: 1, card: FOUR });
      log(store, 'DRAW', { seat: 2 });
      log(store, 'DISCARD_DRAWN', { seat: 2, card: NINE });
      log(store, 'DRAW', { seat: 1 });
      log(store, 'DISCARD_DRAWN', { seat: 1, card: SEVEN });
    });
    await step('reduced motion', () => {
      media(true);
      log(store, 'BLIND_SWAP', { seat: 1, slot: 0, targetSeat: 0, targetSlot: 1 });
      log(store, 'MATCH', { seat: 2, success: false });
    });
    await step('a browser without the animation API', () => {
      media(false);
      // @ts-expect-error simulating a browser without the Web Animations API
      delete Element.prototype.animate;
      state(table(10, { discardTop: TWO }, 7));
      log(store, 'MATCH', { seat: 1, targetSeat: 0, targetSlot: 0, givenSlot: 1, success: true, card: NINE });
    });

    expect(trace.entries).toMatchSnapshot();
  });
});
