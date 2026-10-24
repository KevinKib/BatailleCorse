import { eventSpanMs, scaleMovements, type MotionRect, type TableMovement } from '../../animation';
import type { PresidentLogEntry } from './eventLog';
import { PRESIDENT_TEMPO } from './tempo';

/** A place of the President table, as the screen names it (it turns it into a rectangle). */
export type PresidentAnchor =
  /** A seat: an opponent's plate, or my hand zone for me. */
  | { kind: 'seat'; seat: number }
  /** The back of the card on top of an opponent's fan: where the cards they play leave from. */
  | { kind: 'fanTop'; seat: number }
  /** The whole top play on the pile. */
  | { kind: 'pile' }
  /** One card of the top play on the pile. */
  | { kind: 'pileCard'; index: number }
  /** A card-sized place in the middle of the pile: where the plays under the top one are (they are not drawn). */
  | { kind: 'pileSlot' }
  /** A rectangle measured earlier (the pile as a closed trick showed it), kept when the pile has moved on. */
  | { kind: 'rect'; left: number; top: number; width: number; height: number };

export type PresidentMovement = TableMovement<PresidentAnchor>;

/** A bubble over a seat: what a move said that a card cannot show. */
export interface SeatBubble {
  seat: number;
  kind: 'pass' | 'outForbidden' | 'completed' | 'out';
  /** The place a seat went out in, for an `out` bubble. */
  place?: number;
}

/** Where each card of my play was on screen when I played it, by card name. */
export type PlaySources = Record<string, MotionRect>;
/** What I measured at the click, waiting for the play to come back in the log. */
export interface PendingSources { rects: PlaySources; at: number }
/** How long a measure stays valid: the play comes back in a moment, a later one is another play. */
const SOURCES_LIFE_MS = 15_000;

/** The measured places of the cards of my play, when the click that made it was measured; undefined otherwise. Pure. */
export function sourcesFor(
  entry: PresidentLogEntry, mySeat: number, pending: PendingSources | null, now: number,
): PlaySources | undefined {
  if (!pending || entry.seat !== mySeat || entry.action !== 'PLAY' || entry.cards.length === 0) return undefined;
  if (now - pending.at > SOURCES_LIFE_MS) return undefined;
  if (!entry.cards.every(card => pending.rects[card.name])) return undefined;
  return pending.rects;
}

/**
 * The movements of a move, from its log entry (and the places `sources` measured at my click): each card played
 * flies from its exact place (the top back of the fan for an opponent) to its place on the pile, with a ring on both
 * ends (an arrow too for an opponent). A trick it closed ends with an empty `ring` movement (no mark: the badge says
 * who leads) that keeps the sweep going when nothing else moved. A pass moves nothing (its bubble says it). `factor` scales every
 * duration (see `speedFactor`; the screen plays nothing at 0 but still rings). Pure.
 */
export function planMovements(
  entry: PresidentLogEntry, mySeat: number, factor = 1, sources?: PlaySources,
): PresidentMovement[] {
  const byOpponent = entry.seat !== mySeat;
  const leg = byOpponent ? PRESIDENT_TEMPO.opponent : PRESIDENT_TEMPO.own;
  // Each card leaves its exact place: the one measured when I played it, the top back of an opponent's fan, or (when
  // nothing was measured) my hand zone. Only the cards that moved are ringed: where they left and where they landed.
  const fallback: PresidentAnchor = byOpponent ? { kind: 'fanTop', seat: entry.seat } : { kind: 'seat', seat: entry.seat };
  const moves: PresidentMovement[] = entry.cards.map((card, index) => {
    const measured = byOpponent ? undefined : sources?.[card.name];
    const from: PresidentAnchor = measured ? { kind: 'rect', ...measured } : fallback;
    const to: PresidentAnchor = { kind: 'pileCard', index };
    return {
      kind: 'travel', from, to, card, flip: false, turnDown: false,
      actor: entry.seat, byOpponent,
      delayMs: leg.holdBeforeMs + index * PRESIDENT_TEMPO.cardStaggerMs, durationMs: leg.flightMs, holdAfterMs: leg.holdAfterMs,
      marks: [from, to],
    };
  });
  if (entry.closure) {
    const winner: PresidentAnchor = { kind: 'seat', seat: entry.closure.winner };
    moves.push({
      kind: 'ring', from: { kind: 'pile' }, to: winner, card: null, flip: false, turnDown: false, actor: entry.seat,
      byOpponent: false, delayMs: eventSpanMs(moves), durationMs: 0, holdAfterMs: leg.holdAfterMs, marks: [],
    });
  }
  return factor > 0 ? scaleMovements(moves, factor) : moves;
}

/**
 * When the sweep of the trick that `entry` closed begins, counted from the start of the entry's own movements: once
 * its cards have landed on the pile and the closing badge has had its moment. Pure.
 */
export function sweepStartMs(entry: PresidentLogEntry, mySeat: number, factor = 1): number {
  const leg = entry.seat !== mySeat ? PRESIDENT_TEMPO.opponent : PRESIDENT_TEMPO.own;
  const flights = entry.cards.length > 0
    ? leg.holdBeforeMs + (entry.cards.length - 1) * PRESIDENT_TEMPO.cardStaggerMs + leg.flightMs + leg.holdAfterMs
    : 0;
  return Math.round((flights + PRESIDENT_TEMPO.sweep.badgeHoldMs) * factor);
}

/**
 * The sweep of a closed trick: its cards fly one after the other from the pile to the winner's seat (the closing
 * play first, from its own cards on the pile; the plays under it, which are not drawn, from the middle of the pile),
 * then an empty `ring` movement ends it (the seat is not ringed). Delays count from the start of the sweep (`sweepStartMs`). Nothing when the entry closed
 * no trick. `factor` scales every duration. Pure.
 */
export function planSweep(entry: PresidentLogEntry, mySeat: number, factor = 1): PresidentMovement[] {
  const closure = entry.closure;
  if (!closure) return [];
  const sweep = PRESIDENT_TEMPO.sweep;
  const winner: PresidentAnchor = { kind: 'seat', seat: closure.winner };
  const moves: PresidentMovement[] = closure.trickCards.slice(0, sweep.maxCards).map((card, index) => ({
    kind: 'travel', from: index < closure.cards.length ? { kind: 'pileCard', index } : { kind: 'pileSlot' }, to: winner,
    card, flip: false, turnDown: false, actor: closure.winner, byOpponent: false,
    delayMs: index * sweep.staggerMs, durationMs: sweep.flightMs, holdAfterMs: sweep.landHoldMs, marks: [],
  }));
  if (moves.length > 0) {
    moves.push({
      kind: 'ring', from: { kind: 'pile' }, to: winner, card: null, flip: false, turnDown: false, actor: closure.winner,
      byOpponent: false, delayMs: eventSpanMs(moves), durationMs: 0, holdAfterMs: PRESIDENT_TEMPO.own.holdAfterMs, marks: [],
    });
  }
  return factor > 0 ? scaleMovements(moves, factor) : moves;
}

/**
 * The bubbles of a move: a pass, a forbidden finish ("Out on a 2"), a finish ("Out · 1st"), a four completed out of
 * turn. A move that completes the four and empties the hand says "Out" only (the closing badge says the four: two
 * bubbles on one seat would replace each other). Pure.
 */
export function planBubbles(entry: PresidentLogEntry): SeatBubble[] {
  const bubbles: SeatBubble[] = [];
  const finished = entry.outPlace !== null && !entry.outForbidden;
  if (entry.action === 'PASS') bubbles.push({ seat: entry.seat, kind: 'pass' });
  if (finished) bubbles.push({ seat: entry.seat, kind: 'out', place: entry.outPlace as number });
  else if (entry.outOfTurn) bubbles.push({ seat: entry.seat, kind: 'completed' });
  if (entry.outForbidden) bubbles.push({ seat: entry.seat, kind: 'outForbidden' });
  return bubbles;
}
