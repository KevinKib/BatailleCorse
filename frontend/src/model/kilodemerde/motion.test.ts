import { describe, it, expect } from 'vitest';
import { planMovements, type KiloDeMerdeAnchor, type KiloDeMerdeTableEvent } from './motion';
import { KILO_DE_MERDE_TEMPO as TEMPO, MOTION_MAX_MS } from './tempo';
import { kiloCard, kiloDeMerdeRaceState, kiloDeMerdeStepState } from './fixtures';
import type { KiloDeMerdeEventData } from './KiloDeMerdeState';

const empty: KiloDeMerdeEventData = {
  seat: null, round: null, step: null, card: null, kilosAfter: null, rank: null, returnedCount: null,
};
const pile: KiloDeMerdeAnchor = { kind: 'pile' };
const hand: KiloDeMerdeAnchor = { kind: 'hand' };
const seat = (n: number): KiloDeMerdeAnchor => ({ kind: 'seat', seat: n });

function event(
  eventType: string, data: Partial<KiloDeMerdeEventData>, activeSeats: number[], received = kiloDeMerdeStepState.lastReceived, receivedSlot: number | null = null,
): KiloDeMerdeTableEvent {
  return { id: 1, eventType, data: { ...empty, ...data }, activeSeats, received, receivedSlot };
}
const range = (n: number) => Array.from({ length: n }, (_, i) => i);

describe('planMovements', () => {
  describe('the slot of the swapped card', () => {
    it('leaves from and lands in the slot of the card I changed, not in the first one', () => {
      const moves = planMovements(event('STEP', {}, [0, 1, 2], kiloDeMerdeStepState.lastReceived, 2), 0);
      const slot2: KiloDeMerdeAnchor = { kind: 'hand', slot: 2 };
      expect(moves.map(m => [m.from, m.to])).toEqual([[slot2, seat(1)], [seat(1), seat(2)], [seat(2), slot2]]);
      expect(moves[2]).toMatchObject({ card: kiloDeMerdeStepState.lastReceived!.card, flip: true, marks: [slot2] });
    });

    it('keeps the whole hand zone when the slot is unknown', () => {
      const moves = planMovements(event('STEP', {}, [0, 1, 2]), 0);
      expect(moves[0].from).toEqual(hand);
      expect(moves[2].to).toEqual(hand);
    });

    it('shows the face of the card of the state the event came with', () => {
      const card = kiloCard('QUEEN', 'HEART');
      const moves = planMovements(event('STEP', {}, [0, 1], { card, from: 1 }, 1), 0);
      expect(moves[1].card).toEqual(card);
    });
  });

  describe('STEP', () => {
    it('flies one card per seat to its left neighbour: mine from my hand, the one I receive into it face up', () => {
      const moves = planMovements(event('STEP', { round: 2, step: 3 }, [0, 1, 2]), 0);
      expect(moves.map(m => [m.from, m.to])).toEqual([[hand, seat(1)], [seat(1), seat(2)], [seat(2), hand]]);
      const [mine, middle, received] = moves;
      expect(mine).toMatchObject({ kind: 'travel', card: null, flip: false, byOpponent: false, actor: 0 });
      expect(middle).toMatchObject({ card: null, flip: false, byOpponent: true, actor: 1 });
      expect(received).toMatchObject({
        card: kiloDeMerdeStepState.lastReceived!.card, flip: true, byOpponent: true, actor: 2, marks: [hand],
      });
      for (const m of moves) {
        expect(m.durationMs).toBe(TEMPO.step.flightMs);
        expect(m.delayMs).toBe(TEMPO.step.holdBeforeMs);
      }
    });

    it('a 10-seat step gives 10 flights, each to the next seat', () => {
      const moves = planMovements(event('STEP', { step: 6 }, range(10)), 0);
      expect(moves).toHaveLength(10);
      moves.forEach((m, i) => {
        const from = i === 0 ? hand : seat(i);
        const to = i === 9 ? hand : seat(i + 1);
        expect([m.from, m.to]).toEqual([from, to]);
      });
    });

    it('wraps past the last seat and reads my place from my seat, whatever it is', () => {
      const moves = planMovements(event('STEP', {}, [0, 1, 2]), 2);
      expect(moves.map(m => [m.from, m.to])).toEqual([[seat(0), seat(1)], [seat(1), hand], [hand, seat(0)]]);
      // The card I receive comes from my right neighbour, seat 1.
      expect(moves[1].card).toEqual(kiloDeMerdeStepState.lastReceived!.card);
    });

    it('skips a forfeited seat: it is not in the active seats', () => {
      const moves = planMovements(event('STEP', {}, [0, 2, 5]), 0);
      expect(moves.map(m => [m.from, m.to])).toEqual([[hand, seat(2)], [seat(2), seat(5)], [seat(5), hand]]);
    });

    it('shows a card back when the received card is unknown, and nothing for a lone seat', () => {
      const [first] = planMovements(event('STEP', {}, [0, 1], null), 1);
      expect(first.card).toBeNull();
      expect(planMovements(event('STEP', {}, [0]), 0)).toEqual([]);
      expect(planMovements(event('STEP', {}, []), 0)).toEqual([]);
    });

    it('does not move cards when I am not seated', () => {
      const moves = planMovements(event('STEP', {}, [0, 1, 2]), 40);
      expect(moves.map(m => m.to)).toEqual([seat(1), seat(2), seat(0)]);
      expect(moves.every(m => m.card === null)).toBe(true);
    });
  });

  describe('DRAW', () => {
    it('flies the drawn card from the pile to the drawer plate and turns it face up', () => {
      const king = kiloCard('KING', 'SPADE');
      const [draw, ...rest] = planMovements(event('DRAW', { seat: 2, card: king, kilosAfter: 13 }, [0, 1, 2]), 0);
      expect(rest).toEqual([]);
      expect(draw).toMatchObject({
        kind: 'travel', from: pile, to: seat(2), card: king, flip: true, actor: 2, byOpponent: true,
        durationMs: TEMPO.draw.flightMs, delayMs: TEMPO.draw.holdBeforeMs, marks: [seat(2)],
      });
    });

    it('lands in my hand zone when I am the drawer', () => {
      const [draw] = planMovements(event('DRAW', { seat: 0, card: kiloCard('9', 'CLUB') }, [0, 1, 2]), 0);
      expect(draw).toMatchObject({ to: hand, byOpponent: false });
    });

    it('moves nothing when the pile was empty or the drawer unknown', () => {
      expect(planMovements(event('DRAW', { seat: 2, card: null }, [0, 1, 2]), 0)).toEqual([]);
      expect(planMovements(event('DRAW', { seat: null, card: kiloCard('9') }, [0, 1, 2]), 0)).toEqual([]);
    });
  });

  describe('FLUSH', () => {
    it('sends the drawer cards back to the pile, one flight per card, staggered', () => {
      const moves = planMovements(event('FLUSH', { seat: 2, returnedCount: 3 }, [0, 1, 2]), 0);
      expect(moves).toHaveLength(3);
      for (const m of moves) {
        expect(m).toMatchObject({ kind: 'travel', from: seat(2), to: pile, card: null, flip: false, actor: 2 });
        expect(m.durationMs).toBe(TEMPO.flush.flightMs);
      }
      expect(moves.map(m => m.delayMs)).toEqual([0, 1, 2].map(i => TEMPO.flush.holdBeforeMs + i * TEMPO.flush.staggerMs));
    });

    it('caps the flights so a big pile of kilos stays short', () => {
      const moves = planMovements(event('FLUSH', { seat: 4, returnedCount: 30 }, range(10)), 0);
      expect(moves).toHaveLength(TEMPO.flush.maxFlights);
    });

    it('flies from my hand zone when I am the drawer, and moves nothing for no card', () => {
      const [first] = planMovements(event('FLUSH', { seat: 0, returnedCount: 2 }, [0, 1, 2]), 0);
      expect(first.from).toEqual(hand);
      expect(planMovements(event('FLUSH', { seat: 0, returnedCount: 0 }, [0, 1, 2]), 0)).toEqual([]);
      expect(planMovements(event('FLUSH', { seat: null, returnedCount: 3 }, [0, 1, 2]), 0)).toEqual([]);
    });
  });

  describe('ROUND_DEALT', () => {
    it('deals one pack from the centre to every active seat, staggered, mine into my hand', () => {
      const moves = planMovements(event('ROUND_DEALT', { round: 3 }, [0, 1, 2]), 0);
      expect(moves.map(m => [m.from, m.to])).toEqual([[pile, hand], [pile, seat(1)], [pile, seat(2)]]);
      expect(moves.map(m => m.delayMs)).toEqual([0, 1, 2].map(i => TEMPO.deal.holdBeforeMs + i * TEMPO.deal.staggerMs));
      expect(moves.every(m => m.card === null && m.flip === false)).toBe(true);
    });
  });

  describe('other events', () => {
    it.each(['START', 'PASS_CHOSEN', 'RACE_OPENED', 'SLAPPED', 'FALSE_SLAP', 'ROUND_OVER', 'GAME_OVER', 'FORFEIT', 'NOPE'])(
      '%s moves nothing',
      (type) => {
        expect(planMovements(event(type, { seat: 1 }, [0, 1, 2]), 0)).toEqual([]);
      },
    );

    it('tolerates an event without data', () => {
      expect(planMovements({ id: 1, eventType: 'DRAW', data: null, activeSeats: [0, 1], received: null }, 0)).toEqual([]);
    });
  });

  it('no movement lasts longer than the bound, on the busiest events of a 10-seat table', () => {
    const busiest = [
      event('STEP', {}, range(10)),
      event('ROUND_DEALT', {}, range(10)),
      event('FLUSH', { seat: 9, returnedCount: 40 }, range(10)),
      event('DRAW', { seat: 9, card: kiloCard('ACE') }, range(10)),
    ];
    for (const e of busiest) {
      for (const m of planMovements(e, 0)) {
        expect(m.delayMs + m.durationMs + m.holdAfterMs).toBeLessThanOrEqual(MOTION_MAX_MS);
      }
    }
    expect(kiloDeMerdeRaceState.players).toHaveLength(10);
  });
});
