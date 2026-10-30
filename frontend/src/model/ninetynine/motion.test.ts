import { describe, it, expect } from 'vitest';
import { planMovements, type NinetyNineAnchor } from './motion';
import { toLogEntry } from './eventLog';
import { NINETY_NINE_TEMPO, MOTION_MAX_MS } from './tempo';
import type { NinetyNineEventData } from './NinetyNineState';
import {
  buildNinetyNineBust, buildNinetyNineCard, buildNinetyNineFourSeatRound, buildNinetyNineQuickEnd,
  buildNinetyNineTwelveSeatRound,
} from '../fixtures/ninetynine';

const empty: NinetyNineEventData = {
  seat: null, card: null, aceValue: null, totalAfter: null, reversed: null, hand: null,
  livesLeft: null, place: null, stockSize: null,
};
const seven = buildNinetyNineCard('7', 'HEART');
const stock: NinetyNineAnchor = { kind: 'stock' };
const pile: NinetyNineAnchor = { kind: 'pile' };

function entryOf(type: string, data: Partial<NinetyNineEventData>, before = buildNinetyNineFourSeatRound()) {
  return toLogEntry(1, type, { ...empty, ...data }, before)!;
}

describe('planMovements', () => {
  describe('PLAY', () => {
    it('flies my card from my hand to the pile, then the replacement from the stock to my hand', () => {
      const moves = planMovements(entryOf('PLAY', { seat: 0, card: seven, totalAfter: 49 }), 0);
      expect(moves).toHaveLength(2);
      const [play, draw] = moves;
      expect(play).toMatchObject({
        kind: 'travel', from: { kind: 'hand' }, to: pile, card: seven, flip: false, byOpponent: false, actor: 0,
        restsOn: buildNinetyNineFourSeatRound().pileTop, marks: [pile],
        delayMs: NINETY_NINE_TEMPO.own.holdBeforeMs, durationMs: NINETY_NINE_TEMPO.own.flightMs,
      });
      expect(draw).toMatchObject({ kind: 'travel', from: stock, to: { kind: 'hand' }, card: null, flip: false });
      expect(draw.delayMs).toBeGreaterThan(play.delayMs);
    });

    it('carries the slot of my played card on the play and on the replacement', () => {
      const entry = { ...entryOf('PLAY', { seat: 0, card: seven }), handSlot: 1 };
      const [play, draw] = planMovements(entry, 0);
      expect(play.from).toEqual({ kind: 'hand', slot: 1 });
      expect(draw.to).toEqual({ kind: 'hand', slot: 1 });
    });

    it('keeps the whole hand when the slot is unknown, and never gives a slot to an opponent', () => {
      const [play] = planMovements({ ...entryOf('PLAY', { seat: 0, card: seven }), handSlot: null }, 0);
      expect(play.from).toEqual({ kind: 'hand' });
      const [opp] = planMovements({ ...entryOf('PLAY', { seat: 2, card: seven }), handSlot: 1 }, 0);
      expect(opp.from).toEqual({ kind: 'seat', seat: 2 });
    });

    it('flies an opponent card from his seat, face down and turning up, at the slower tempo', () => {
      const [play, draw] = planMovements(entryOf('PLAY', { seat: 2, card: seven, totalAfter: 49 }), 0);
      expect(play).toMatchObject({
        from: { kind: 'seat', seat: 2 }, to: pile, flip: true, byOpponent: true, actor: 2,
        delayMs: NINETY_NINE_TEMPO.opponent.holdBeforeMs, durationMs: NINETY_NINE_TEMPO.opponent.flightMs,
      });
      expect(draw).toMatchObject({ from: stock, to: { kind: 'seat', seat: 2 } });
    });

    it('plays nothing when the card is unknown', () => {
      expect(planMovements(entryOf('PLAY', { seat: 2 }), 0)).toEqual([]);
    });
  });

  describe('RESHUFFLE', () => {
    it('sends the pile back to the stock as one card back, without an actor', () => {
      const moves = planMovements(entryOf('RESHUFFLE', { stockSize: 30 }), 0);
      expect(moves).toHaveLength(1);
      expect(moves[0]).toMatchObject({ kind: 'travel', from: pile, to: stock, card: null, byOpponent: false, marks: [stock] });
    });
  });

  describe('BUST', () => {
    it('rings the seat that is stuck, moving no card', () => {
      const [ring] = planMovements(entryOf('BUST', { seat: 2, hand: [seven], livesLeft: 2 }, buildNinetyNineBust()), 0);
      expect(ring).toMatchObject({
        kind: 'ring', from: { kind: 'seat', seat: 2 }, to: { kind: 'seat', seat: 2 }, byOpponent: true,
        marks: [{ kind: 'seat', seat: 2 }],
      });
    });

    it('names my own place a hand, not a seat', () => {
      const [ring] = planMovements(entryOf('BUST', { seat: 0 }, buildNinetyNineBust()), 0);
      expect(ring.marks).toEqual([{ kind: 'hand' }]);
      expect(ring.byOpponent).toBe(false);
    });
  });

  describe('ROUND_START', () => {
    it('deals from the stock to every live seat, one after the other', () => {
      const moves = planMovements(entryOf('ROUND_START', {}), 0, { liveSeats: [0, 1, 3] });
      expect(moves.map(m => m.to)).toEqual([{ kind: 'hand' }, { kind: 'seat', seat: 1 }, { kind: 'seat', seat: 3 }]);
      expect(moves.every(m => m.from.kind === 'stock' && m.card === null)).toBe(true);
      expect(moves.map(m => m.delayMs)).toEqual([0, 1, 2].map(i => NINETY_NINE_TEMPO.deal.holdBeforeMs + i * NINETY_NINE_TEMPO.deal.staggerMs));
    });

    it('deals nothing when the live seats are unknown', () => {
      expect(planMovements(entryOf('ROUND_START', {}), 0)).toEqual([]);
    });

    it('stays within the movement bound on a 12-seat table', () => {
      const liveSeats = buildNinetyNineTwelveSeatRound().players.filter(p => !p.eliminated).map(p => p.seat);
      const moves = planMovements(entryOf('ROUND_START', {}), 0, { liveSeats });
      expect(moves).toHaveLength(11);
      for (const m of moves) expect(m.delayMs + m.durationMs + m.holdAfterMs).toBeLessThanOrEqual(MOTION_MAX_MS);
    });
  });

  it.each(['ELIMINATED', 'GAME_OVER'])('%s moves no card', (type) => {
    expect(planMovements(entryOf(type, { seat: 2, place: 3 }, buildNinetyNineQuickEnd()), 0)).toEqual([]);
  });

  it('keeps every play inside the movement bound', () => {
    for (const mySeat of [0, 2]) {
      for (const m of planMovements(entryOf('PLAY', { seat: 2, card: seven }), mySeat)) {
        expect(m.delayMs + m.durationMs + m.holdAfterMs).toBeLessThanOrEqual(MOTION_MAX_MS);
      }
    }
  });
});
