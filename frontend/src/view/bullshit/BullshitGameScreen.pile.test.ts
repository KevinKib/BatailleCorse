import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { nextTick } from 'vue';
import { mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import PrimeVue from 'primevue/config';

// The layer is a stub that records what the screen asks it to play; the real keyframes are covered by
// BullshitGameScreen.flights.test.ts.
const layerPlay = vi.fn((..._a: unknown[]) => ({ endsInMs: 0 }));
const play = vi.fn();
vi.mock('../../animation', async (orig) => {
  const { defineComponent, h } = await import('vue');
  return {
    ...(await orig<typeof import('../../animation')>()),
    CardMotionLayer: defineComponent({
      props: ['resolve', 'zIndex'],
      setup(_, { expose }) {
        expose({ play: (...a: unknown[]) => layerPlay(...a) });
        return () => h('div', { 'data-test': 'motion-layer' });
      },
    }),
  };
});
vi.mock('../../composables/useCardSound', () => ({ cardSound: { play: (n: number) => play(n) } }));

import BullshitGameScreen from './BullshitGameScreen.vue';
import { useBullshitStore } from '../../state/Bullshit.store';
import type { BullshitState } from '../../model/bullshit/BullshitState';

const hand = [
  { rank: 'ACE', suit: 'HEART', name: 'HEART_ACE' },
  { rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' },
];
function state(pile: number): BullshitState {
  return {
    started: true, id: 'g1', gameType: 'bullshit', myHand: hand, availableActions: ['DISCARD'],
    players: [{ id: '0', handCount: 2, isCurrentPlayer: true }, { id: '1', handCount: 3, isCurrentPlayer: false }],
    currentTarget: { label: 'ACE' }, discardPileSize: pile, table: { state: 'NO_CLAIM' },
    pendingWinner: { state: 'NONE' }, outcome: { status: 'ONGOING' },
  } as BullshitState;
}
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', name: 'games', component: { template: '<div/>' } }] });

function setup() {
  const store = useBullshitStore();
  store.applyEvent({ type: 'seat-change', seat: 0 });
  store.applyEvent({ type: 'state-update', state: state(0) });
  const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] }, attachTo: document.body });
  // The places on screen (happy-dom measures nothing).
  const root = wrapper.element as HTMLElement;
  const at = (el: Element | null, r: Partial<DOMRect>) => vi.spyOn(el as Element, 'getBoundingClientRect').mockReturnValue(r as DOMRect);
  at(root.querySelector('[data-test="hand-card-1"]'), { left: 100, top: 500, width: 60, height: 90 });
  at(root.querySelector('.pile-well > .playing_card'), { left: 200, top: 100, width: 90, height: 130 });
  at(root.querySelector('.my-zone'), { left: 300, top: 600, width: 200, height: 120 });
  at(root.querySelector('[data-seat="1"] .seat-card'), { left: 500, top: 20, width: 40, height: 60 });
  return { store, wrapper };
}

describe('BullshitGameScreen pile animation and sound', () => {
  const original = Element.prototype.animate;
  beforeEach(() => {
    setActivePinia(createPinia()); localStorage.clear(); layerPlay.mockClear(); play.mockClear();
    window.matchMedia = ((q: string) => ({ matches: false, media: q, addEventListener() {}, removeEventListener() {} })) as typeof window.matchMedia;
    Element.prototype.animate = (() => ({ finished: new Promise(() => {}), cancel() {} })) as unknown as typeof Element.prototype.animate;
  });
  afterEach(() => { Element.prototype.animate = original; });

  it('flies the selected hand cards to the pile on Discard', async () => {
    const { store, wrapper } = setup();
    vi.spyOn(store, 'discard').mockImplementation(() => {});
    await wrapper.get('[data-test="hand-card-1"]').trigger('click');
    await wrapper.get('[data-test="discard"]').trigger('click');
    expect(layerPlay).toHaveBeenCalledTimes(1);
    const [movements, options] = layerPlay.mock.calls[0] as unknown as [{ card: unknown; from: unknown }[], unknown];
    expect(movements).toHaveLength(1);
    expect(movements[0].card).toEqual(hand[1]);
    expect(movements[0].from).toEqual({ kind: 'rect', rect: { left: 100, top: 500, width: 60, height: 90 } });
    expect(options).toEqual({ marksOnly: false, startInMs: 0, still: false });
    expect(store.discard).toHaveBeenCalled();
    wrapper.unmount();
  });

  it('plays the sound when the pile grows, not when it empties', async () => {
    const { store, wrapper } = setup();
    store.applyEvent({ type: 'state-update', state: state(2) });
    await nextTick();
    expect(play).toHaveBeenCalledWith(2);
    play.mockClear();
    store.applyEvent({ type: 'state-update', state: state(0) });
    await nextTick();
    expect(play).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('can be muted from the top bar and remembers the choice', async () => {
    const { store, wrapper } = setup();
    const toggle = wrapper.get('[data-test="sound-toggle"]');
    expect(toggle.attributes('aria-pressed')).toBe('true');
    expect(toggle.attributes('aria-label')).toBe('Mute sound');
    await toggle.trigger('click');
    expect(wrapper.get('[data-test="sound-toggle"]').attributes('aria-label')).toBe('Unmute sound');
    expect(localStorage.getItem('bullshit.sound')).toBe('off');
    store.applyEvent({ type: 'state-update', state: state(1) });
    await nextTick();
    expect(play).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it('shortcuts follow the buttons: no discard when the server does not allow DISCARD', async () => {
    const { store, wrapper } = setup();
    const discard = vi.spyOn(store, 'discard').mockImplementation(() => {});
    const key = (k: string, code: string) =>
      document.dispatchEvent(new KeyboardEvent('keyup', { key: k, code, bubbles: true }));
    await wrapper.get('[data-test="hand-card-0"]').trigger('click');
    await nextTick();
    expect(store.selectedCards).toHaveLength(1);
    store.applyEvent({ type: 'state-update', state: { ...state(0), availableActions: [] } as BullshitState });
    await nextTick();
    key('d', 'KeyD');
    expect(discard).not.toHaveBeenCalled();
    expect(wrapper.get('[data-test="discard"]').attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  describe('pile taken by the contested player', () => {
    const call = (pickerSeat: number) => ({
      type: 'event' as const, eventType: 'CALL_BULLSHIT',
      eventData: { callerSeat: 1, claimantSeat: 0, truthful: pickerSeat === 1, pickerSeat, revealedCards: hand },
    });

    it('flies the pile to the taker seat once the verdict is gone', async () => {
      vi.useFakeTimers();
      const { store, wrapper } = setup();
      store.applyEvent(call(1));
      await nextTick();
      expect(layerPlay).not.toHaveBeenCalled();
      vi.advanceTimersByTime(3000);
      await nextTick();
      expect(layerPlay).toHaveBeenCalledTimes(1);
      const [movements, options] = layerPlay.mock.calls[0] as unknown as [{ card: unknown; from: unknown; to: unknown }[], unknown];
      expect(movements).toHaveLength(2);                // one back per card of the pile, at most three
      expect(movements[0].card).toBeNull();
      expect(movements[0].from).toEqual({ kind: 'rect', rect: { left: 200, top: 100, width: 90, height: 130 } });
      expect(movements[0].to).toEqual({ kind: 'rect', rect: { left: 500, top: 20, width: 40, height: 60 } });
      expect(options).toEqual({ marksOnly: false, startInMs: 0, still: false });
      wrapper.unmount();
      vi.useRealTimers();
    });

    it('flies it to my own zone when I am the taker', async () => {
      vi.useFakeTimers();
      const { store, wrapper } = setup();
      store.applyEvent(call(0));
      await nextTick();
      vi.advanceTimersByTime(3000);
      await nextTick();
      expect(layerPlay).toHaveBeenCalledTimes(1);
      expect((layerPlay.mock.calls[0][0] as { to: unknown }[])[0].to).toEqual({ kind: 'rect', rect: { left: 300, top: 600, width: 200, height: 120 } });
      wrapper.unmount();
      vi.useRealTimers();
    });
  });
});
