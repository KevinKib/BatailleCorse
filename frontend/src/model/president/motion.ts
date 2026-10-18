import type { AnimationSpeed } from '../../composables/useAnimationSpeed';
import { eventSpanMs, scaleMovements, type TableMovement } from '../table/motion';
import type { PresidentLogEntry } from './eventLog';
import { PRESIDENT_TEMPO } from './tempo';

/** A place of the President table, as the screen names it (it turns it into a rectangle). */
export type PresidentAnchor =
  /** A seat: an opponent's plate, or my hand zone for me. */
  | { kind: 'seat'; seat: number }
  /** The whole top play on the pile. */
  | { kind: 'pile' }
  /** One card of the top play on the pile. */
  | { kind: 'pileCard'; index: number };

export type PresidentMovement = TableMovement<PresidentAnchor>;

/** A bubble over a seat: what a move said that a card cannot show. */
export interface SeatBubble {
  seat: number;
  kind: 'pass' | 'outForbidden' | 'completed';
}

/** How long the animations last at a speed, as a share of Normal (0: nothing moves). */
export function speedFactor(speed: AnimationSpeed): number {
  return speed === 'normal' ? 1 : speed === 'fast' ? 0.5 : 0;
}

/**
 * The movements of a move, from its log entry only: each card played flies from the seat (my hand zone for me) to
 * its place on the pile, with rings on both ends (an arrow too for an opponent), and a trick it closed rings the
 * winner's seat once the cards have landed. A pass moves nothing (its bubble says it). `factor` scales every
 * duration (see `speedFactor`; the screen plays nothing at 0 but still rings). Pure.
 */
export function planMovements(entry: PresidentLogEntry, mySeat: number, factor = 1): PresidentMovement[] {
  const byOpponent = entry.seat !== mySeat;
  const leg = byOpponent ? PRESIDENT_TEMPO.opponent : PRESIDENT_TEMPO.own;
  const seat: PresidentAnchor = { kind: 'seat', seat: entry.seat };
  const moves: PresidentMovement[] = entry.cards.map((card, index) => ({
    kind: 'travel', from: seat, to: { kind: 'pileCard', index }, card, flip: false, turnDown: false,
    actor: entry.seat, byOpponent,
    delayMs: leg.holdBeforeMs + index * PRESIDENT_TEMPO.cardStaggerMs, durationMs: leg.flightMs, holdAfterMs: leg.holdAfterMs,
    marks: [seat, { kind: 'pile' }],
  }));
  if (entry.closure) {
    const winner: PresidentAnchor = { kind: 'seat', seat: entry.closure.winner };
    moves.push({
      kind: 'ring', from: { kind: 'pile' }, to: winner, card: null, flip: false, turnDown: false, actor: entry.seat,
      byOpponent: false, delayMs: eventSpanMs(moves), durationMs: 0, holdAfterMs: leg.holdAfterMs, marks: [winner],
    });
  }
  return factor > 0 ? scaleMovements(moves, factor) : moves;
}

/** The bubbles of a move: a pass, a forbidden finish ("Out on a 2"), a four completed out of turn. Pure. */
export function planBubbles(entry: PresidentLogEntry): SeatBubble[] {
  const bubbles: SeatBubble[] = [];
  if (entry.action === 'PASS') bubbles.push({ seat: entry.seat, kind: 'pass' });
  if (entry.outOfTurn) bubbles.push({ seat: entry.seat, kind: 'completed' });
  if (entry.outForbidden) bubbles.push({ seat: entry.seat, kind: 'outForbidden' });
  return bubbles;
}
