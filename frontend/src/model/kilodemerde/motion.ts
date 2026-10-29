import type { TableMovement } from '../../animation';
import type Card from '../Card';
import type { KiloDeMerdeEventData, KiloDeMerdeReceived } from './KiloDeMerdeState';
import { KILO_DE_MERDE_TEMPO as TEMPO } from './tempo';

/** A place of the Kilo de merde table, as the screen names it (it turns it into a rectangle). */
export type KiloDeMerdeAnchor =
  /** Another seat's plate. */
  | { kind: 'seat'; seat: number }
  /** My own hand: the card in that slot (0-based, left to right), else the whole zone. */
  | { kind: 'hand'; slot?: number }
  /** The kilo pile in the centre. */
  | { kind: 'pile' };

export type KiloDeMerdeMovement = TableMovement<KiloDeMerdeAnchor>;

/** One event at the table with what the table showed when it happened, to draw it without teleporting a card. */
export interface KiloDeMerdeTableEvent {
  /** Increases with every entry of the session, so two equal events are two entries. */
  id: number;
  eventType: string;
  data: KiloDeMerdeEventData | null;
  /** The seats still at the table, in seat order. */
  activeSeats: number[];
  /** The card I received at the last resolved step (the state that came with the event). */
  received: KiloDeMerdeReceived | null;
  /**
   * The slot of my hand holding that card when the event came. The server swaps the passed card for the received one in
   * place, so it is also the slot the passed card left; null when unknown.
   */
  receivedSlot?: number | null;
}

const PILE: KiloDeMerdeAnchor = { kind: 'pile' };

/** My seat is my hand zone, any other seat is its plate. */
const placeOf = (seat: number, mySeat: number, slot: number | null = null): KiloDeMerdeAnchor =>
  seat === mySeat ? (slot === null ? { kind: 'hand' } : { kind: 'hand', slot }) : { kind: 'seat', seat };

function travel(
  from: KiloDeMerdeAnchor, to: KiloDeMerdeAnchor, actor: number, mySeat: number,
  timing: { delayMs: number; durationMs: number; holdAfterMs: number },
  card: Card | null = null, flip = false,
): KiloDeMerdeMovement {
  return { kind: 'travel', from, to, card, flip, turnDown: false, actor, byOpponent: actor !== mySeat, ...timing, marks: [to] };
}

/**
 * The movements an event stands for, from its public data and the table around it. A step sends one face-down card
 * from every seat to the next active seat (the one I receive lands face up in my hand); a draw flies the kilo card
 * from the pile to the drawer, face up; a flush sends the drawer's cards back to the pile; a round start deals a
 * pack to every seat. Everything else moves nothing. Pure.
 */
export function planMovements(event: KiloDeMerdeTableEvent, mySeat: number): KiloDeMerdeMovement[] {
  switch (event.eventType) {
    case 'STEP': return stepOf(event, mySeat);
    case 'DRAW': return drawOf(event, mySeat);
    case 'FLUSH': return flushOf(event, mySeat);
    case 'ROUND_START': return dealOf(event, mySeat);
    default: return [];
  }
}

function stepOf(event: KiloDeMerdeTableEvent, mySeat: number): KiloDeMerdeMovement[] {
  const seats = event.activeSeats;
  if (seats.length < 2) return [];
  const timing = { delayMs: TEMPO.step.holdBeforeMs, durationMs: TEMPO.step.flightMs, holdAfterMs: TEMPO.step.holdAfterMs };
  const slot = event.received !== null ? event.receivedSlot ?? null : null;
  return seats.map((seat, i) => {
    const to = seats[(i + 1) % seats.length];
    const mine = to === mySeat;
    const move = travel(placeOf(seat, mySeat, slot), placeOf(to, mySeat, slot), seat, mySeat, timing, mine ? event.received?.card ?? null : null, mine && event.received !== null);
    move.marks = mine ? [move.to] : [];
    return move;
  });
}

function drawOf(event: KiloDeMerdeTableEvent, mySeat: number): KiloDeMerdeMovement[] {
  const seat = event.data?.seat ?? null;
  const card = event.data?.card ?? null;
  if (seat === null || !card) return [];
  const { holdBeforeMs, flightMs, holdAfterMs } = TEMPO.draw;
  return [travel(PILE, placeOf(seat, mySeat), seat, mySeat,
    { delayMs: holdBeforeMs, durationMs: flightMs, holdAfterMs }, card, true)];
}

function flushOf(event: KiloDeMerdeTableEvent, mySeat: number): KiloDeMerdeMovement[] {
  const seat = event.data?.seat ?? null;
  const returnedCount = event.data?.returnedCount ?? 0;
  if (seat === null || returnedCount <= 0) return [];
  const { holdBeforeMs, flightMs, holdAfterMs, staggerMs, maxFlights } = TEMPO.flush;
  const from = placeOf(seat, mySeat);
  return Array.from({ length: Math.min(returnedCount, maxFlights) }, (_, i) =>
    travel(from, PILE, seat, mySeat, { delayMs: holdBeforeMs + i * staggerMs, durationMs: flightMs, holdAfterMs }));
}

function dealOf(event: KiloDeMerdeTableEvent, mySeat: number): KiloDeMerdeMovement[] {
  const { holdBeforeMs, flightMs, holdAfterMs, staggerMs } = TEMPO.deal;
  return event.activeSeats.map((seat, i) => ({
    ...travel(PILE, placeOf(seat, mySeat), -1, mySeat,
      { delayMs: holdBeforeMs + i * staggerMs, durationMs: flightMs, holdAfterMs }),
    byOpponent: false, marks: [],
  }));
}
