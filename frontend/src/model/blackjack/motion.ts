import type { TableMovement } from '../../animation';
import type Card from '../Card';
import type { BlackjackEventData } from './BlackjackState';
import { BLACKJACK_TEMPO as TEMPO } from './tempo';

/**
 * A place of the Blackjack table, as the screen names it (it turns it into a rectangle): the shoe, the dealer's
 * hand, or one hand slot of a seat (`seat-<n>-hand-<i>`; a split seat has two).
 */
export type BlackjackAnchor = 'shoe' | 'dealer' | `seat-${number}-hand-${number}`;

export type BlackjackMovement = TableMovement<BlackjackAnchor>;

/** One event at the table, for the pictures (flights) and the live region. Empty after a first state. */
export interface BlackjackTableEvent {
  /** Increases with every entry of the session, so two equal events are two entries. */
  id: number;
  eventType: string;
  data: BlackjackEventData | null;
}

export const handAnchor = (seat: number, handIndex = 0): BlackjackAnchor => `seat-${seat}-hand-${handIndex}`;

interface Timing { delayMs: number; durationMs: number; holdAfterMs: number }

function travel(
  from: BlackjackAnchor, to: BlackjackAnchor, actor: number, mySeat: number, timing: Timing, card: Card | null = null,
): BlackjackMovement {
  return {
    kind: 'travel', from, to, card, flip: false, turnDown: false, actor, byOpponent: actor >= 0 && actor !== mySeat,
    ...timing, marks: [],
  };
}

/**
 * The movements an event stands for, from its public data. A deal flies two cards to each bettor (from the button),
 * the dealer's up card between the rounds and the hole card last; a hit or a double flies one card from the shoe; a
 * split moves the second card to a new hand then deals the first hand a card; the dealer's play turns the hole card
 * over first, then draws. Everything else moves nothing. Pure.
 */
export function planMovements(event: BlackjackTableEvent, mySeat: number): BlackjackMovement[] {
  const data = event.data;
  if (!data) return [];
  switch (event.eventType) {
    case 'DEAL': return dealOf(data, mySeat);
    case 'HIT': return drawOf(data, mySeat, TEMPO.hit);
    case 'DOUBLE': return drawOf(data, mySeat, TEMPO.double);
    case 'SPLIT': return splitOf(data, mySeat);
    case 'DEALER_PLAY': return dealerOf(data, mySeat, data.cards ?? []);
    case 'DEALER_BLACKJACK': return dealerOf(data, mySeat, []);
    default: return [];
  }
}

function dealOf(data: BlackjackEventData, mySeat: number): BlackjackMovement[] {
  const bettors = Object.keys(data.bets ?? {}).map(Number).sort((a, b) => a - b);
  if (bettors.length === 0) return [];
  const button = data.button ?? 0;
  const first = bettors.findIndex(s => s >= button);
  const order = first < 0 ? bettors : [...bettors.slice(first), ...bettors.slice(0, first)];
  const places: BlackjackAnchor[] = [...order.map(s => handAnchor(s)), 'dealer'];
  const { holdBeforeMs, flightMs, holdAfterMs, staggerMs } = TEMPO.deal;
  return [...places, ...places].map((to, i) =>
    travel('shoe', to, -1, mySeat, { delayMs: holdBeforeMs + i * staggerMs, durationMs: flightMs, holdAfterMs }));
}

function drawOf(data: BlackjackEventData, mySeat: number, leg: { holdBeforeMs: number; flightMs: number; holdAfterMs: number }): BlackjackMovement[] {
  if (data.seat === null) return [];
  return [travel('shoe', handAnchor(data.seat, data.handIndex ?? 0), data.seat, mySeat,
    { delayMs: leg.holdBeforeMs, durationMs: leg.flightMs, holdAfterMs: leg.holdAfterMs }, data.card)];
}

function splitOf(data: BlackjackEventData, mySeat: number): BlackjackMovement[] {
  if (data.seat === null) return [];
  const { holdBeforeMs, flightMs, holdAfterMs } = TEMPO.split;
  return [
    travel(handAnchor(data.seat, 0), handAnchor(data.seat, 1), data.seat, mySeat,
      { delayMs: holdBeforeMs, durationMs: flightMs, holdAfterMs }),
    travel('shoe', handAnchor(data.seat, 0), data.seat, mySeat,
      { delayMs: holdBeforeMs + flightMs, durationMs: flightMs, holdAfterMs }, data.cards?.[0] ?? null),
  ];
}

function dealerOf(data: BlackjackEventData, mySeat: number, drawn: Card[]): BlackjackMovement[] {
  const { holdBeforeMs, flightMs, holdAfterMs } = TEMPO.reveal;
  const reveal: BlackjackMovement = {
    kind: 'reveal', from: 'dealer', to: 'dealer', card: data.holeCard, flip: false, turnDown: false, actor: -1,
    byOpponent: false, delayMs: holdBeforeMs, durationMs: flightMs, holdAfterMs, marks: [],
  };
  const start = holdBeforeMs + flightMs + TEMPO.dealerDraw.holdBeforeMs;
  const draws = drawn.map((card, i) => travel('shoe', 'dealer', -1, mySeat,
    { delayMs: start + i * TEMPO.dealerDraw.staggerMs, durationMs: TEMPO.dealerDraw.flightMs, holdAfterMs: TEMPO.dealerDraw.holdAfterMs },
    card));
  return [reveal, ...draws];
}
