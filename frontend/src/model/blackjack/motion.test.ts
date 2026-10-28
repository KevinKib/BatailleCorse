import { describe, it, expect } from 'vitest';
import { planMovements, handAnchor, type BlackjackTableEvent } from './motion';
import { BLACKJACK_TEMPO as TEMPO, MOTION_MAX_MS } from './tempo';
import { card, blackjackBettingState, blackjackPlayingState } from './fixtures';
import type { BlackjackEventData } from './BlackjackState';

const empty: BlackjackEventData = {
  seat: null, handIndex: null, card: null, value: null, cards: null, holeCard: null, handNumber: null,
  button: null, bets: null, nets: null, stacks: null, bestSeats: null,
};
const event = (eventType: string, data: Partial<BlackjackEventData> = {}): BlackjackTableEvent =>
  ({ id: 1, eventType, data: { ...empty, ...data } });

describe('handAnchor', () => {
  it('names a seat hand slot', () => {
    expect(handAnchor(2, 1)).toBe('seat-2-hand-1');
  });
});

describe('planMovements', () => {
  describe('DEAL (the order of the spec 3.4)', () => {
    // Bets by seat 0..3, the button on seat 2: seat 2, 3, 0, 1.
    const bets = { 0: 10, 1: 20, 2: 10, 3: 10 };
    const deal = () => planMovements(event('DEAL', { handNumber: 3, button: 2, bets }), 0);

    it('deals a card to each seat from the button, the dealer up card, a second round, the hole card', () => {
      const moves = deal();
      expect(moves.map(m => [m.from, m.to])).toEqual([
        ['shoe', 'seat-2-hand-0'], ['shoe', 'seat-3-hand-0'], ['shoe', 'seat-0-hand-0'], ['shoe', 'seat-1-hand-0'],
        ['shoe', 'dealer'],
        ['shoe', 'seat-2-hand-0'], ['shoe', 'seat-3-hand-0'], ['shoe', 'seat-0-hand-0'], ['shoe', 'seat-1-hand-0'],
        ['shoe', 'dealer'],
      ]);
    });

    it('staggers the cards one after the other with the deal tempo', () => {
      const moves = deal();
      moves.forEach((m, i) => {
        expect(m.kind).toBe('travel');
        expect(m.delayMs).toBe(TEMPO.deal.holdBeforeMs + i * TEMPO.deal.staggerMs);
        expect(m.durationMs).toBe(TEMPO.deal.flightMs);
        expect(m.holdAfterMs).toBe(TEMPO.deal.holdAfterMs);
        expect(m.byOpponent).toBe(false);
        expect(m.turnDown).toBe(false);
      });
    });

    it('leaves a card back in flight (the faces are in the state)', () => {
      for (const m of deal()) expect(m.card).toBeNull();
    });

    it('skips a seat that did not bet (eliminated or gone)', () => {
      const moves = planMovements(event('DEAL', { button: 0, bets: { 0: 5, 2: 5 } }), 0);
      expect(moves.map(m => m.to)).toEqual([
        'seat-0-hand-0', 'seat-2-hand-0', 'dealer', 'seat-0-hand-0', 'seat-2-hand-0', 'dealer',
      ]);
    });

    it('starts at the next bettor when the button is not one', () => {
      const moves = planMovements(event('DEAL', { button: 1, bets: { 0: 5, 2: 5 } }), 0);
      expect(moves.slice(0, 2).map(m => m.to)).toEqual(['seat-2-hand-0', 'seat-0-hand-0']);
    });

    it('moves nothing without bets', () => {
      expect(planMovements(event('DEAL', { button: 0 }), 0)).toEqual([]);
    });

    it('over the P0 betting fixture seats', () => {
      const seats = blackjackBettingState.players.map(p => p.seat);
      const bet = Object.fromEntries(seats.map(s => [s, 10]));
      expect(planMovements(event('DEAL', { button: blackjackBettingState.button, bets: bet }), 0)).toHaveLength(10);
    });
  });

  describe('HIT and DOUBLE', () => {
    it('flies the drawn card from the shoe to the hand it joins, face up', () => {
      const [m, ...rest] = planMovements(event('HIT', { seat: 1, handIndex: 0, card: card('SEVEN'), value: 18 }), 0);
      expect(rest).toEqual([]);
      expect(m).toMatchObject({
        kind: 'travel', from: 'shoe', to: 'seat-1-hand-0', card: card('SEVEN'), flip: false, byOpponent: true, actor: 1,
        delayMs: TEMPO.hit.holdBeforeMs, durationMs: TEMPO.hit.flightMs, holdAfterMs: TEMPO.hit.holdAfterMs,
      });
    });

    it('is not an opponent move when it is mine, and targets the second hand after a split', () => {
      const [m] = planMovements(event('HIT', { seat: 0, handIndex: 1, card: card('TWO') }), 0);
      expect(m).toMatchObject({ to: 'seat-0-hand-1', byOpponent: false, actor: 0 });
    });

    it('defaults to the first hand', () => {
      const [m] = planMovements(event('HIT', { seat: 2, card: card('TWO') }), 0);
      expect(m.to).toBe('seat-2-hand-0');
    });

    it('a double draws its single card the same way', () => {
      const [m] = planMovements(event('DOUBLE', { seat: 0, handIndex: 0, card: card('NINE'), value: 20 }), 0);
      expect(m).toMatchObject({ from: 'shoe', to: 'seat-0-hand-0', card: card('NINE') });
    });

    it('moves nothing without a seat', () => {
      expect(planMovements(event('HIT', { card: card('TWO') }), 0)).toEqual([]);
    });
  });

  describe('SPLIT', () => {
    it('moves the second card to a new hand, then deals the first hand its new card', () => {
      const moves = planMovements(event('SPLIT', { seat: 0, cards: [card('THREE', 'HEART')] }), 0);
      expect(moves.map(m => [m.from, m.to])).toEqual([['seat-0-hand-0', 'seat-0-hand-1'], ['shoe', 'seat-0-hand-0']]);
      expect(moves[0].delayMs).toBe(TEMPO.split.holdBeforeMs);
      expect(moves[1].delayMs).toBe(TEMPO.split.holdBeforeMs + TEMPO.split.flightMs);
      expect(moves[1].card).toEqual(card('THREE', 'HEART'));
      expect(moves.every(m => m.byOpponent === false)).toBe(true);
    });

    it('is an opponent move for another seat', () => {
      const moves = planMovements(event('SPLIT', { seat: 2 }), 0);
      expect(moves.every(m => m.byOpponent && m.actor === 2)).toBe(true);
      expect(moves[1].card).toBeNull();
    });
  });

  describe('DEALER_PLAY and DEALER_BLACKJACK (the reveal comes first)', () => {
    it('turns the hole card over, then draws the dealer cards one after the other', () => {
      const moves = planMovements(
        event('DEALER_PLAY', { holeCard: card('TEN', 'CLUB'), cards: [card('FOUR'), card('TWO', 'HEART')], value: 22 }), 0);
      expect(moves.map(m => m.kind)).toEqual(['reveal', 'travel', 'travel']);
      expect(moves[0]).toMatchObject({ from: 'dealer', to: 'dealer', card: card('TEN', 'CLUB'), delayMs: TEMPO.reveal.holdBeforeMs });
      expect(moves[1]).toMatchObject({ from: 'shoe', to: 'dealer', card: card('FOUR'), byOpponent: false });
      expect(moves[1].delayMs).toBe(TEMPO.reveal.holdBeforeMs + TEMPO.reveal.flightMs + TEMPO.dealerDraw.holdBeforeMs);
      expect(moves[2].delayMs).toBe(moves[1].delayMs + TEMPO.dealerDraw.staggerMs);
    });

    it('reveals only when the dealer draws nothing', () => {
      const moves = planMovements(event('DEALER_PLAY', { holeCard: card('KING'), cards: [], value: 20 }), 0);
      expect(moves.map(m => m.kind)).toEqual(['reveal']);
    });

    it('a dealer blackjack is the reveal alone', () => {
      const moves = planMovements(event('DEALER_BLACKJACK', { holeCard: card('KING', 'DIAMOND') }), 0);
      expect(moves).toHaveLength(1);
      expect(moves[0]).toMatchObject({ kind: 'reveal', from: 'dealer', to: 'dealer', card: card('KING', 'DIAMOND') });
    });
  });

  it('moves nothing for the other events', () => {
    for (const type of ['START', 'BET', 'STAND', 'BUST', 'SETTLE', 'ELIMINATED', 'GAME_OVER', 'NOPE']) {
      expect(planMovements(event(type, { seat: 1 }), 0)).toEqual([]);
    }
  });

  it('never lasts longer than the longest allowed movement (hold + flight + hold)', () => {
    const all = [
      ...planMovements(event('DEAL', { button: 0, bets: { 0: 1, 1: 1, 2: 1, 3: 1, 4: 1, 5: 1 } }), 0),
      ...planMovements(event('SPLIT', { seat: 0 }), 0),
      ...planMovements(event('DEALER_PLAY', { holeCard: card('TEN'), cards: [card('TWO'), card('THREE'), card('FOUR')] }), 0),
    ];
    for (const m of all) expect(m.durationMs + m.holdAfterMs).toBeLessThanOrEqual(MOTION_MAX_MS);
  });

  it('is pure: the same event twice gives the same plan', () => {
    const e = event('HIT', { seat: blackjackPlayingState.currentSeat, card: card('FIVE') });
    expect(planMovements(e, 0)).toEqual(planMovements(e, 0));
  });
});
