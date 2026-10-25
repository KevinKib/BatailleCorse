import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { nextTick } from 'vue';
import { mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import PrimeVue from 'primevue/config';

// The layer is a stub that records what the screen asks it to play; the real keyframes are covered by
// BullshitGameScreen.flights.test.ts.
const take = vi.fn((..._a: unknown[]) => ({ endsInMs: 0 }));
let reduced = false;
vi.mock('../../animation', async (orig) => {
  const { defineComponent, h } = await import('vue');
  return {
    ...(await orig<typeof import('../../animation')>()),
    CardMotionLayer: defineComponent({
      props: ['resolve', 'zIndex'],
      setup(_, { expose }) {
        expose({ play: (...a: unknown[]) => take(...a) });
        return () => h('div', { 'data-test': 'motion-layer' });
      },
    }),
  };
});
vi.mock('../../composables/useCardSound', () => ({ cardSound: { play: vi.fn() } }));

import BullshitGameScreen from './BullshitGameScreen.vue';
import { useBullshitStore, REVEAL_HOLD_MS } from '../../state/Bullshit.store';
import { END_SCREEN_DELAY_MS } from '../../composables/useEndScreen';
import type { BullshitState } from '../../model/bullshit/BullshitState';

const hand = [{ rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' }];
function state(winner: string | null, extra: Partial<BullshitState> = {}): BullshitState {
  return {
    started: true, id: 'g1', gameType: 'bullshit', myHand: hand, availableActions: [],
    players: [
      { id: '0', handCount: 1, isCurrentPlayer: false, name: 'Alice' },
      { id: '1', handCount: 0, isCurrentPlayer: false, bot: true },
    ],
    currentTarget: { label: 'ACE' }, discardPileSize: 3, table: { state: 'NO_CLAIM' },
    pendingWinner: { state: 'NONE' },
    outcome: winner === null ? { status: 'ONGOING' } : { status: 'FINISHED', winnerId: winner },
    ...extra,
  } as BullshitState;
}
const truthfulCall = {
  type: 'event' as const, eventType: 'CALL_BULLSHIT', message: '',
  eventData: { callerSeat: 0, claimantSeat: 1, truthful: true, pickerSeat: 0, revealedCards: hand },
};
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'games', component: { template: '<div/>' } }] });

let measured = true;
function setup(initial: BullshitState) {
  const store = useBullshitStore();
  store.applyEvent({ type: 'seat-change', seat: 0 });
  store.applyEvent({ type: 'state-update', state: initial });
  const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] } });
  if (measured) {
    // The pile and the taker have a place on screen (happy-dom measures nothing).
    const rect = (left: number, top: number, width: number, height: number) => ({ left, top, width, height }) as DOMRect;
    const pile = wrapper.element.querySelector('.pile-well > .playing_card');
    if (pile) vi.spyOn(pile, 'getBoundingClientRect').mockReturnValue(rect(200, 100, 90, 130));
    const zone = wrapper.element.querySelector('.my-zone');
    if (zone) vi.spyOn(zone, 'getBoundingClientRect').mockReturnValue(rect(300, 600, 200, 120));
  }
  return { store, wrapper };
}
const tick = async (ms: number) => { vi.advanceTimersByTime(ms); await nextTick(); };
const original = Element.prototype.animate;

describe('Bullshit end screen waits for the last turn to play out', () => {
  beforeEach(() => {
    setActivePinia(createPinia()); localStorage.clear(); take.mockClear(); reduced = false; measured = true; vi.useFakeTimers();
    window.matchMedia = ((q: string) => ({ matches: reduced, media: q, addEventListener() {}, removeEventListener() {} })) as typeof window.matchMedia;
    Element.prototype.animate = (() => ({ finished: new Promise(() => {}), cancel() {} })) as unknown as typeof Element.prototype.animate;
  });
  afterEach(() => { vi.useRealTimers(); Element.prototype.animate = original; });

  it('keeps the table, the verdict and the pile flight before the end screen', async () => {
    const { store, wrapper } = setup(state(null, { pendingWinner: { state: 'PENDING', playerId: '1' } }));
    // The winning truthful call: the finished state and the verdict arrive together.
    store.applyEvent({ type: 'state-update', state: state('1') });
    store.applyEvent(truthfulCall);
    await nextTick();
    expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="reveal"]').exists()).toBe(true);

    await tick(REVEAL_HOLD_MS);                   // the verdict goes away, the pile flies to its taker
    expect(take).toHaveBeenCalledTimes(1);
    expect((take.mock.calls[0][0] as unknown[]).length).toBe(1);
    await tick(459);                              // the one copy (460 ms) has not landed yet
    expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
    await tick(1);                                // it lands
    await tick(END_SCREEN_DELAY_MS - 1);
    expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
    await tick(1);
    expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('shows the end screen right after the verdict when nothing flies (reduced motion)', async () => {
    reduced = true;
    const { store, wrapper } = setup(state(null, { pendingWinner: { state: 'PENDING', playerId: '1' } }));
    store.applyEvent({ type: 'state-update', state: state('1') });
    store.applyEvent(truthfulCall);
    await nextTick();
    await tick(REVEAL_HOLD_MS);
    expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
    await tick(END_SCREEN_DELAY_MS);
    expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
    expect(take).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('does not wait for a flight when the pile has no place on screen', async () => {
    measured = false;
    const { store, wrapper } = setup(state(null));
    store.applyEvent({ type: 'state-update', state: state('1') });
    store.applyEvent(truthfulCall);
    await nextTick();
    await tick(REVEAL_HOLD_MS);
    await tick(END_SCREEN_DELAY_MS);
    expect(take).not.toHaveBeenCalled();
    expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('shows the end screen after a short beat when the game ends without a verdict', async () => {
    const { store, wrapper } = setup(state(null));
    store.applyEvent({ type: 'state-update', state: state('1') });
    await nextTick();
    expect(wrapper.find('[data-test="end"]').exists()).toBe(false);
    await tick(END_SCREEN_DELAY_MS);
    expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('shows at once a game that was already over when the page opened', async () => {
    const { wrapper } = setup(state('1'));
    expect(wrapper.find('[data-test="end"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('names a bot winner, and me by my own name', async () => {
    const bot = setup(state('1'));
    expect(bot.wrapper.get('[data-test="end-winner"]').text()).toBe('Winner: Bot 1');
    bot.wrapper.unmount();
    setActivePinia(createPinia());
    const me = setup(state('0'));
    expect(me.wrapper.get('[data-test="end-winner"]').text()).toBe('Winner: Alice');
    me.wrapper.unmount();
  });
});

describe('Bullshit game duration timer', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); take.mockClear(); reduced = false; vi.useFakeTimers(); });
  afterEach(() => vi.useRealTimers());

  it('counts from the start of the game and freezes at game over, while the last verdict is still on the table', async () => {
    const { store, wrapper } = setup(state(null));
    const timer = () => wrapper.get('[data-cy="game-timer"]').text();
    expect(timer()).toBe('00:00');
    await tick(65_000);
    expect(timer()).toBe('01:05');

    store.applyEvent({ type: 'state-update', state: state('1') });
    store.applyEvent(truthfulCall);
    await nextTick();
    await tick(2_000);
    expect(wrapper.find('[data-test="reveal"]').exists()).toBe(true);
    expect(timer()).toBe('01:05');
    wrapper.unmount();
  });
});
