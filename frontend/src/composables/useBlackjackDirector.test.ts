import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick, ref, shallowRef } from 'vue';
import { useBlackjackDirector } from './useBlackjackDirector';
import { blackjackBettingState, blackjackPlayingState, card } from '../model/blackjack/fixtures';
import { BLACKJACK_TEMPO as TEMPO } from '../model/blackjack/tempo';
import { DEALER_KEY, handKey } from '../model/blackjack/shown';
import type { BlackjackEventData, BlackjackState } from '../model/blackjack/BlackjackState';
import type { BlackjackTableEvent } from '../model/blackjack/motion';
import type { AnimationSpeed } from '../animation';

const empty: BlackjackEventData = {
  seat: null, handIndex: null, card: null, value: null, cards: null, holeCard: null, handNumber: null,
  button: null, bets: null, nets: null, stacks: null, bestSeats: null,
};
let entryId = 0;
const entry = (eventType: string, data: Partial<BlackjackEventData> = {}): BlackjackTableEvent =>
  ({ id: ++entryId, eventType, data: { ...empty, ...data } });

// The table as the store holds it: the latest state and the events, newest first. A new version of both lands in one go.
function setup(initial: BlackjackState, options: { still?: boolean; speed?: AnimationSpeed } = {}) {
  const game = shallowRef<BlackjackState>(initial);
  const events = shallowRef<BlackjackTableEvent[]>([]);
  const play = vi.fn();
  const layer = ref({ play });
  const speed = ref<AnimationSpeed>(options.speed ?? 'normal');
  const director = useBlackjackDirector({
    game: () => game.value, events: () => events.value, mySeat: () => 0, speed, layer,
    still: () => options.still ?? false,
  });
  const receive = (state: BlackjackState, ...fresh: BlackjackTableEvent[]) => {
    game.value = state;
    events.value = [...fresh.reverse(), ...events.value];
  };
  return { director, game, events, play, receive, speed };
}

const bets = { 0: 10, 1: 10, 2: 10, 3: 10 };
const dealEvent = () => entry('DEAL', { handNumber: 4, button: 3, bets });
const dealStart = TEMPO.bets.revealMs + TEMPO.bets.clearMs;

describe('useBlackjackDirector', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it('shows the latest state as it is when nothing is playing', () => {
    const { director } = setup(blackjackPlayingState);
    expect(director.shown.value).toBe(blackjackPlayingState);
    expect(director.holding.value).toBe(false);
  });

  it('keeps the cards of the last hand on the table through the next betting', () => {
    const { director } = setup(blackjackBettingState);
    const shown = director.shown.value!;
    expect(shown.dealer.cards).toHaveLength(2);
    expect(shown.players[0].hands[0].cards).toHaveLength(2);
    expect(shown.phase).toBe('BETTING');
  });

  describe('a deal', () => {
    it('keeps the old table (the phase, no action offered) and tells the bets together at once', () => {
      const t = setup(blackjackBettingState);
      t.receive(blackjackPlayingState, dealEvent());
      const shown = t.director.shown.value!;
      expect(t.director.holding.value).toBe(true);
      expect(shown.phase).toBe('BETTING');
      expect(shown.players[1].bet).toBe(10);
      expect(t.director.banner.value?.kind).toBe('bets');
      expect(shown.availableActions).toEqual([]);
    });

    it('tells the bets together, then clears the table and deals the cards in order', async () => {
      const t = setup(blackjackBettingState);
      t.receive(blackjackPlayingState, dealEvent());
            await vi.advanceTimersByTimeAsync(TEMPO.bets.revealMs);
      expect(t.director.shown.value!.dealer.cards).toEqual([]);
      expect(t.director.shown.value!.players[0].hands[0].cards).toEqual([]);
      // The first card (the button is seat 3) has landed on seat 3 after one flight.
      await vi.advanceTimersByTimeAsync(TEMPO.bets.clearMs + TEMPO.deal.flightMs);
      expect(t.director.shown.value!.players[3].hands[0].cards).toHaveLength(1);
      expect(t.director.shown.value!.players[0].hands[0].cards).toEqual([]);
      expect(t.director.hold.value!.counts[handKey(3, 0)]).toBe(1);
    });

    it('asks the layer to fly each card from the shoe to its place when it leaves', async () => {
      const t = setup(blackjackBettingState);
      t.receive(blackjackPlayingState, dealEvent());
      await vi.advanceTimersByTimeAsync(dealStart);
      expect(t.play).toHaveBeenCalledTimes(1);
      const [movements] = t.play.mock.calls[0];
      expect(movements).toHaveLength(1);
      expect(movements[0]).toMatchObject({ from: 'shoe', to: 's3h0:0' });
      await vi.advanceTimersByTimeAsync(TEMPO.deal.staggerMs);
      expect(t.play).toHaveBeenCalledTimes(2);
      expect(t.play.mock.calls[1][0][0]).toMatchObject({ from: 'shoe', to: 's0h0:0' });
    });

    it('lets go of the table, which then shows the live state, once everything is told', async () => {
      const t = setup(blackjackBettingState);
      t.receive(blackjackPlayingState, dealEvent());
      await vi.advanceTimersByTimeAsync(20000);
      expect(t.director.holding.value).toBe(false);
      expect(t.director.shown.value).toBe(blackjackPlayingState);
    });

    it('keeps the old cards on the table until the deal clears them, then deals the new ones', async () => {
      const t = setup(blackjackBettingState);
      t.receive(blackjackPlayingState, dealEvent());
      // While the bets are told, the previous hand is still there, the dealer's included.
      expect(t.director.shown.value!.dealer.cards).toEqual(blackjackBettingState.lastHand!.dealer.cards);
      expect(t.director.shown.value!.players[0].hands[0].cards).toEqual(blackjackBettingState.lastHand!.seats[0].hands[0].cards);
      await vi.advanceTimersByTimeAsync(TEMPO.bets.revealMs - 10);
      expect(t.director.shown.value!.dealer.cards).toHaveLength(2);
      await vi.advanceTimersByTimeAsync(10);
      expect(t.director.shown.value!.dealer.cards).toEqual([]);
      expect(t.director.shown.value!.players[0].hands[0].cards).toEqual([]);
    });

    it('slows down and speeds up with the animation speed', async () => {
      const t = setup(blackjackBettingState, { speed: 'fast' });
      t.receive(blackjackPlayingState, dealEvent());
      await vi.advanceTimersByTimeAsync(dealStart * 0.5 + 1);
      expect(t.play).toHaveBeenCalled();
    });
  });

  describe('an action of a seat', () => {
    const base: BlackjackState = { ...blackjackPlayingState, currentSeat: 1, activeHandIndex: 0 };
    const hit = () => entry('HIT', { seat: 1, handIndex: 0, card: card('TWO'), value: 20 });
    const afterHit: BlackjackState = {
      ...base, version: base.version + 1,
      players: base.players.map(p => p.seat === 1
        ? { ...p, hands: [{ ...p.hands[0], cards: [...p.hands[0].cards, card('TWO')], total: 20 }] } : p),
    };

    it('puts a tag over the seat, flies the card, then shows it', async () => {
      const t = setup(base);
      t.receive(afterHit, hit());
      expect(t.director.tags.value[1]?.kind).toBe('hit');
      expect(t.director.shown.value!.players[1].hands[0].cards).toHaveLength(2);
      expect(t.director.shown.value!.players[1].hands[0].pending).toBe(true);
      await vi.advanceTimersByTimeAsync(1);
      expect(t.director.shown.value!.players[1].hands[0].cards).toHaveLength(2);
      await vi.advanceTimersByTimeAsync(TEMPO.draw.tagLeadMs);
      expect(t.play.mock.calls[0][0][0]).toMatchObject({ from: 'shoe', to: 's1h0:2' });
      await vi.advanceTimersByTimeAsync(TEMPO.draw.flightMs);
      expect(t.director.shown.value!.players[1].hands[0].cards).toHaveLength(3);
      expect(t.director.shown.value!.players[1].hands[0].pending).toBeFalsy();
    });

    it('hides the new card (and the total) while it has not landed', async () => {
      const t = setup(base);
      t.receive(afterHit, hit());
      await vi.advanceTimersByTimeAsync(10);
      const hand = t.director.shown.value!.players[1].hands[0];
      expect(hand.cards).toHaveLength(2);
      expect(hand.pending).toBe(true);
    });

    it('lets the tag go but remembers the last action of the seat', async () => {
      const t = setup(base);
      t.receive(afterHit, hit());
      await vi.advanceTimersByTimeAsync(TEMPO.tagLifeMs + 10);
      expect(t.director.tags.value[1]).toBeUndefined();
      expect(t.director.lastAction.value[1]).toBe('hit');
    });

    it('queues a second event behind the first', async () => {
      const t = setup(base);
      const stand = entry('STAND', { seat: 1 });
      t.receive(afterHit, hit());
      t.receive({ ...afterHit, version: afterHit.version + 1 }, stand);
      expect(t.director.tags.value[1]?.kind).toBe('hit');
      await vi.advanceTimersByTimeAsync(TEMPO.draw.tagLeadMs + TEMPO.draw.flightMs + TEMPO.draw.endMs + 5);
      expect(t.director.tags.value[1]?.kind).toBe('stand');
    });
  });

  describe('the dealer and the results', () => {
    const base: BlackjackState = { ...blackjackPlayingState, currentSeat: -1 };
    const settled: BlackjackState = {
      ...blackjackBettingState, version: 40, handNumber: 5,
      dealer: { upCard: card('SIX'), cards: [card('SIX'), card('TEN'), card('FIVE')], holeHidden: false, total: 21, soft: false, blackjack: false, bust: false },
      players: blackjackBettingState.players.map(p => ({
        ...p, stack: p.stack + 10,
        hands: [{ cards: [card('TEN'), card('NINE')], total: 19, soft: false, status: 'STOOD' as const, doubled: false, fromSplit: false, net: -10 }],
      })),
    };
    const batch = () => [
      entry('DEALER_PLAY', { holeCard: card('TEN'), cards: [card('FIVE')], value: 21 }),
      entry('SETTLE', { nets: { 0: -10 }, stacks: { 0: 90 } }),
    ];

    it('keeps the hole card face down, then turns it, then draws, then tells the results, before the next betting opens', async () => {
      const t = setup(base);
      t.receive(settled, ...batch());
      const first = t.director.shown.value!;
      expect(first.phase).toBe('PLAYING');
      expect(first.dealer.holeHidden).toBe(true);
      expect(first.players[0].hands[0].net).toBeUndefined();
      expect(t.director.banner.value?.kind).toBe('dealer');
      expect(t.director.stage.value).toBe('dealer');

      await vi.advanceTimersByTimeAsync(TEMPO.dealer.bannerMs + TEMPO.dealer.revealMs);
      expect(t.director.shown.value!.dealer.holeHidden).toBe(false);
      expect(t.director.dealerLine.value?.kind).toBe('reveals');

      await vi.advanceTimersByTimeAsync(TEMPO.dealer.afterRevealMs);
      expect(t.director.dealerLine.value?.kind).toBe('draws');
      await vi.advanceTimersByTimeAsync(TEMPO.dealer.flightMs + 400);
      expect(t.director.shown.value!.dealer.cards).toHaveLength(3);
      expect(t.director.dealerLine.value).toMatchObject({ kind: 'stands', total: 21 });

      // The dealer plays: still no results, still the play phase, no betting.
      expect(t.director.shown.value!.phase).toBe('PLAYING');
      await vi.advanceTimersByTimeAsync(TEMPO.dealer.endMs + 5);
      expect(t.director.shown.value!.players[0].hands[0].net).toBe(-10);
      expect(t.director.banner.value?.kind).toBe('results');

      // The results stay for a while.
      await vi.advanceTimersByTimeAsync(TEMPO.results.holdMs - 1000);
      expect(t.director.holding.value).toBe(true);
      await vi.advanceTimersByTimeAsync(1500);
      expect(t.director.holding.value).toBe(false);
      expect(t.director.stage.value).toBeNull();
      expect(t.director.shown.value).toBe(settled);
    });

    it('keeps the dealer and my cards on the table when the table lets go on an empty betting', async () => {
      const next: BlackjackState = {
        ...blackjackBettingState, version: 41, handNumber: 5,
        lastHand: { ...blackjackBettingState.lastHand!, handNumber: 4 },
      };
      const t = setup(base);
      t.receive(next, ...batch());
      await vi.advanceTimersByTimeAsync(20000);
      expect(t.director.holding.value).toBe(false);
      const shown = t.director.shown.value!;
      expect(shown.dealer.cards.length).toBeGreaterThan(0);
      expect(shown.players[0].hands[0].cards.length).toBeGreaterThan(0);
    });

    it('keeps the stacks as they were until the results are told', async () => {
      const t = setup(base);
      t.receive(settled, ...batch());
      expect(t.director.shown.value!.players[1].stack).toBe(base.players[1].stack);
      await vi.advanceTimersByTimeAsync(20000);
      expect(t.director.shown.value!.players[1].stack).toBe(settled.players[1].stack);
    });
  });

  describe('without pictures (Instant, reduced motion)', () => {
    it('holds nothing: the table is the live state, the last actions are still known', () => {
      const t = setup(blackjackBettingState, { still: true });
      t.receive(blackjackPlayingState, dealEvent(), entry('HIT', { seat: 2, handIndex: 0, card: card('TWO') }));
      expect(t.director.holding.value).toBe(false);
      expect(t.director.shown.value).toBe(blackjackPlayingState);
      expect(t.director.lastAction.value[2]).toBe('hit');
      expect(t.play).not.toHaveBeenCalled();
    });

    it('forgets the last actions at the next deal', () => {
      const t = setup(blackjackBettingState, { still: true });
      t.receive(blackjackPlayingState, entry('HIT', { seat: 2, handIndex: 0, card: card('TWO') }));
      t.receive({ ...blackjackPlayingState, version: 99 }, dealEvent());
      expect(t.director.lastAction.value).toEqual({});
    });
  });

  describe('flush and cancel', () => {
    it('flush shows the live state at once and stops the timers', async () => {
      const t = setup(blackjackBettingState);
      t.receive(blackjackPlayingState, dealEvent());
      t.director.flush();
      expect(t.director.holding.value).toBe(false);
      await vi.advanceTimersByTimeAsync(20000);
      expect(t.play).not.toHaveBeenCalled();
    });

    it('releases a table that has been held for too long, whatever happened to the pictures', async () => {
      const t = setup(blackjackBettingState);
      t.receive(blackjackPlayingState, dealEvent());
      await vi.advanceTimersByTimeAsync(TEMPO.maxHoldMs + 1);
      expect(t.director.holding.value).toBe(false);
    });

    it('is told about a table that went back to a lobby', async () => {
      const t = setup(blackjackBettingState);
      t.receive(blackjackPlayingState, dealEvent());
      await nextTick();
      (t.game as { value: BlackjackState | null }).value = null;
      expect(t.director.shown.value).toBeNull();
    });
  });

  it('keeps the dealer key in the table of the hold', () => {
    const t = setup(blackjackBettingState);
    t.receive(blackjackPlayingState, dealEvent());
    expect(Object.keys(t.director.hold.value!.counts)).toContain(DEALER_KEY);
  });
});
