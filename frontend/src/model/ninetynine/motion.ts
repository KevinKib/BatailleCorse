import type { TableMovement } from '../../animation';
import type { NinetyNineLogEntry } from './eventLog';
import { NINETY_NINE_TEMPO as TEMPO } from './tempo';

/** A place of the 99 table, as the screen names it (it turns it into a rectangle). */
export type NinetyNineAnchor =
  /** An opponent's plate. */
  | { kind: 'seat'; seat: number }
  /** My own hand zone. */
  | { kind: 'hand' }
  | { kind: 'pile' }
  | { kind: 'stock' };

export type NinetyNineMovement = TableMovement<NinetyNineAnchor>;

/** What the table knew before the event, to draw it without teleporting a card. */
export interface MotionContext {
  /** The seats that hold cards, for the deal of a round start (unknown: nothing is dealt). */
  liveSeats?: number[];
}

/** My seat is my hand zone, any other seat is its plate. */
const placeOf = (seat: number, mySeat: number): NinetyNineAnchor =>
  seat === mySeat ? { kind: 'hand' } : { kind: 'seat', seat };

const STOCK: NinetyNineAnchor = { kind: 'stock' };
const PILE: NinetyNineAnchor = { kind: 'pile' };

/**
 * The movements an event stands for, from its public data only: a play flies to the pile and the replacement card
 * comes from the stock; a reshuffle sends the pile back; a bust rings the stuck seat; a round start deals to every
 * live seat. Pure.
 */
export function planMovements(entry: NinetyNineLogEntry, mySeat: number, context: MotionContext = {}): NinetyNineMovement[] {
  switch (entry.eventType) {
    case 'PLAY': return playOf(entry, mySeat);
    case 'RESHUFFLE':
      return [{
        kind: 'travel', from: PILE, to: STOCK, card: null, flip: false, turnDown: false, actor: -1, byOpponent: false,
        delayMs: TEMPO.reshuffle.holdBeforeMs, durationMs: TEMPO.reshuffle.flightMs,
        holdAfterMs: TEMPO.reshuffle.holdAfterMs, marks: [STOCK],
      }];
    case 'BUST': {
      if (entry.seat === null) return [];
      const place = placeOf(entry.seat, mySeat);
      return [{
        kind: 'ring', from: place, to: place, card: null, flip: false, turnDown: false, actor: entry.seat,
        byOpponent: entry.seat !== mySeat, delayMs: TEMPO.bust.holdBeforeMs, durationMs: TEMPO.bust.flightMs,
        holdAfterMs: TEMPO.bust.holdAfterMs, marks: [place],
      }];
    }
    case 'ROUND_START':
      return (context.liveSeats ?? []).map((seat, i) => ({
        kind: 'travel', from: STOCK, to: placeOf(seat, mySeat), card: null, flip: false, turnDown: false,
        actor: -1, byOpponent: false, delayMs: TEMPO.deal.holdBeforeMs + i * TEMPO.deal.staggerMs,
        durationMs: TEMPO.deal.flightMs, holdAfterMs: TEMPO.deal.holdAfterMs, marks: [],
      }));
    default: return [];
  }
}

function playOf(entry: NinetyNineLogEntry, mySeat: number): NinetyNineMovement[] {
  if (entry.seat === null || entry.card === null) return [];
  const byOpponent = entry.seat !== mySeat;
  const leg = byOpponent ? TEMPO.opponent : TEMPO.own;
  const source = placeOf(entry.seat, mySeat);
  const play: NinetyNineMovement = {
    kind: 'travel', from: source, to: PILE, card: entry.card, flip: byOpponent, turnDown: false,
    actor: entry.seat, byOpponent, delayMs: leg.holdBeforeMs, durationMs: leg.flightMs, holdAfterMs: leg.holdAfterMs,
    marks: [PILE],
  };
  if (entry.pileTopBefore) play.restsOn = entry.pileTopBefore;
  // The replacement card: a back, from the stock to the seat that played.
  const draw: NinetyNineMovement = {
    kind: 'travel', from: STOCK, to: source, card: null, flip: false, turnDown: false,
    actor: entry.seat, byOpponent, delayMs: leg.holdBeforeMs + TEMPO.drawFollowMs, durationMs: leg.flightMs,
    holdAfterMs: leg.holdAfterMs, marks: [],
  };
  return [play, draw];
}
