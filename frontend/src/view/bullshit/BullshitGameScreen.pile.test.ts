import { describe, it, expect, beforeEach, vi } from 'vitest';
import { nextTick } from 'vue';
import { mount } from '@vue/test-utils';
import { setActivePinia, createPinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import PrimeVue from 'primevue/config';

const fly = vi.fn(() => 1);
const take = vi.fn(() => 1);
const play = vi.fn();
vi.mock('../../composables/usePileAnimation', async (orig) => ({
  ...(await orig<typeof import('../../composables/usePileAnimation')>()),
  flyCardsToPile: (...a: unknown[]) => (fly as (...x: unknown[]) => number)(...a),
  flyPileToTaker: (...a: unknown[]) => (take as (...x: unknown[]) => number)(...a),
  prefersReducedMotion: () => false,
}));
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
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div/>' } }] });

function setup() {
  const store = useBullshitStore();
  store.applyEvent({ type: 'seat-change', seat: 0 });
  store.applyEvent({ type: 'state-update', state: state(0) });
  const wrapper = mount(BullshitGameScreen, { props: { gameId: 'g1' }, global: { plugins: [router, PrimeVue] }, attachTo: document.body });
  return { store, wrapper };
}

describe('BullshitGameScreen pile animation and sound', () => {
  beforeEach(() => { setActivePinia(createPinia()); localStorage.clear(); fly.mockClear(); take.mockClear(); play.mockClear(); });

  it('flies the selected hand cards to the pile on Discard', async () => {
    const { store, wrapper } = setup();
    vi.spyOn(store, 'discard').mockImplementation(() => {});
    await wrapper.get('[data-test="hand-card-1"]').trigger('click');
    await wrapper.get('[data-test="discard"]').trigger('click');
    expect(fly).toHaveBeenCalledTimes(1);
    const [sources, , opts] = fly.mock.calls[0] as unknown as [HTMLElement[], unknown, { reduceMotion: boolean }];
    expect(sources).toEqual([wrapper.get('[data-test="hand-card-1"]').element]);
    expect(opts.reduceMotion).toBe(false);
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
    key('&', 'Digit1');
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
      expect(take).not.toHaveBeenCalled();
      vi.advanceTimersByTime(3000);
      await nextTick();
      expect(take).toHaveBeenCalledTimes(1);
      const [source, target, size, opts] = take.mock.calls[0] as unknown as [HTMLElement, unknown, number, { reduceMotion: boolean }];
      expect(source).toBeTruthy();
      expect(target).toBeTruthy();
      expect(size).toBe(2);
      expect(opts.reduceMotion).toBe(false);
      wrapper.unmount();
      vi.useRealTimers();
    });

    it('flies it to my own zone when I am the taker', async () => {
      vi.useFakeTimers();
      const { store, wrapper } = setup();
      const zone = wrapper.get('.my-zone').element;
      const rectSpy = vi.spyOn(zone, 'getBoundingClientRect');
      store.applyEvent(call(0));
      await nextTick();
      vi.advanceTimersByTime(3000);
      await nextTick();
      expect(take).toHaveBeenCalledTimes(1);
      expect(rectSpy).toHaveBeenCalled();
      wrapper.unmount();
      vi.useRealTimers();
    });
  });
});
