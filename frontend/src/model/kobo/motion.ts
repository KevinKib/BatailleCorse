import type Card from '../Card';
import type { KoboLogEntry } from './KoboEvents';
import { MOTION_MAX_MS, TEMPO, type LegTempo } from './tempo';

/**
 * Where a card flies from or to, as the table names it (the component turns it into a rectangle). Purely visual:
 * nothing here is read back by the game logic.
 */
export type MotionAnchor =
  | { kind: 'draw' }
  | { kind: 'discard' }
  /** The zone of the drawn card, in the middle of the table. */
  | { kind: 'hand' }
  | { kind: 'slot'; seat: number; slot: number }
  /** The slot a penalty card just opened: the last one of the player's tableau. */
  | { kind: 'newSlot'; seat: number }
  /** A player's whole place (his tableau, or my zone). */
  | { kind: 'seat'; seat: number }
  /** Every card of the table (the reveal at the end of a round). */
  | { kind: 'table' };

export interface Movement {
  /** `travel`: one way. `glance`: out and back (a peek). `reveal`: every card turns over in place. */
  kind: 'travel' | 'glance' | 'reveal';
  from: MotionAnchor;
  to: MotionAnchor;
  /** The face shown in flight; null is a card back (a face-down card never appears in an event). */
  card: Card | null;
  /** Starts face down and turns face up on the way (the card I draw). */
  flip: boolean;
  actor: number;
  /** An opponent's move gets a faint arrow as well: the one place where reading the table is hard. */
  byOpponent: boolean;
  /** The hold on the source before the card leaves (plus the wait behind a previous move of the same event). */
  delayMs: number;
  /** The flight itself. */
  durationMs: number;
  /** The hold on the destination once landed. */
  holdAfterMs: number;
  /** The places to ring while this move plays (slots only: the piles speak for themselves). */
  marks: MotionAnchor[];
}

export { MOTION_MAX_MS };

const legOf = (byOpponent: boolean): LegTempo => (byOpponent ? TEMPO.opponent : TEMPO.own);
const markable = (...anchors: MotionAnchor[]): MotionAnchor[] =>
  anchors.filter(a => a.kind === 'slot' || a.kind === 'newSlot');

/** When the last flight of an event is over (the holds after landing excluded): the next event may start then. */
export function eventSpanMs(movements: Movement[]): number {
  return movements.reduce((span, m) => Math.max(span, m.delayMs + m.durationMs), 0);
}

/**
 * The movements an event stands for, from its public data only. A swap is two crossing moves; a match sends the
 * matched card to the discard and, on another player's card, my gift to the slot it emptied. Pure.
 */
export function planMovements(entry: KoboLogEntry, mySeat: number): Movement[] {
  const moves = entry.data ? ofAction(entry, mySeat) : [];
  if (entry.eventType === 'ROUND_END') {
    moves.push({
      kind: 'reveal', from: { kind: 'table' }, to: { kind: 'table' }, card: null, flip: false,
      actor: entry.data?.seat ?? -1, byOpponent: false, delayMs: TEMPO.reveal.holdBeforeMs,
      durationMs: TEMPO.reveal.flightMs, holdAfterMs: TEMPO.reveal.holdAfterMs, marks: [],
    });
  }
  return moves;
}

function ofAction(entry: KoboLogEntry, mySeat: number): Movement[] {
  const data = entry.data!;
  const actor = data.seat;
  const byOpponent = actor !== mySeat;
  const leg = legOf(byOpponent);
  const base = { actor, byOpponent, flip: false };
  /** `follow`: this move leaves that much later than the others of the event. */
  const travel = (from: MotionAnchor, to: MotionAnchor, card: Card | null = null, extra: Partial<Movement> = {}, follow = 0): Movement =>
    ({
      ...base, kind: 'travel', from, to, card, marks: markable(from, to),
      delayMs: leg.holdBeforeMs + follow, durationMs: leg.flightMs, holdAfterMs: leg.holdAfterMs, ...extra,
    });
  const slot = (seat: number | null, n: number | null): MotionAnchor | null =>
    seat !== null && n !== null ? { kind: 'slot', seat, slot: n } : null;

  switch (data.action) {
    case 'DRAW':
      return data.success === false ? [] : [travel({ kind: 'draw' }, { kind: 'hand' }, null, { flip: actor === mySeat })];
    case 'SWAP': {
      const own = slot(actor, data.slot);
      return own ? [travel({ kind: 'hand' }, own), travel(own, { kind: 'discard' }, data.card)] : [];
    }
    case 'DISCARD_DRAWN':
      return [travel({ kind: 'hand' }, { kind: 'discard' }, data.card)];
    case 'PEEK': {
      const target = slot(data.targetSeat, data.targetSlot);
      if (!target || data.targetSeat === actor) return [];
      return [{
        ...base, kind: 'glance', from: target, to: { kind: 'seat', seat: actor }, card: null, marks: markable(target),
        delayMs: TEMPO.glance.holdBeforeMs, durationMs: TEMPO.glance.flightMs, holdAfterMs: TEMPO.glance.holdAfterMs,
      }];
    }
    case 'BLIND_SWAP':
    case 'KING_SWAP': {
      const own = slot(actor, data.slot);
      const other = slot(data.targetSeat, data.targetSlot);
      return own && other ? [travel(own, other), travel(other, own)] : [];
    }
    case 'MATCH': {
      if (data.success === false) return [travel({ kind: 'draw' }, { kind: 'newSlot', seat: actor })];
      const target = slot(data.targetSeat, data.targetSlot);
      if (!target) return [];
      const moves = [travel(target, { kind: 'discard' }, data.card)];
      const given = slot(actor, data.givenSlot);
      if (given && data.targetSeat !== actor) moves.push(travel(given, target, null, {}, TEMPO.followMs));
      return moves;
    }
    default: return [];
  }
}
