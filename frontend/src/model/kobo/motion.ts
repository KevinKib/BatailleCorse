import type Card from '../Card';
import type { KoboLogEntry } from './KoboEvents';
import { MOTION_MAX_MS, TEMPO } from './tempo';
import { eventSpanMs, type LegTempo, type TableMovement } from '../../animation';

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

/**
 * A Kobo movement: the shared table movement (`animation/core/movement.ts`) between Kobo's places. Kobo uses `travel`
 * (the flip of the card I draw, the turn face down of the card I saw), `glance` (a peek) and `reveal` (the end of a
 * round); its rings are on slots only (the piles speak for themselves).
 */
export type Movement = TableMovement<MotionAnchor>;

export { MOTION_MAX_MS, eventSpanMs };

/** What the table knew before the event, to draw it without teleporting a card. */
export interface MotionContext {
  /** The face of the card I hold in the drawn zone (never another seat's: the server does not send it). */
  drawnFace?: Card | null;
  /** The top of the discard pile before the event; null when it was empty. */
  discardBefore?: Card | null;
}

const legOf = (byOpponent: boolean): LegTempo => (byOpponent ? TEMPO.opponent : TEMPO.own);
const markable = (...anchors: MotionAnchor[]): MotionAnchor[] =>
  anchors.filter(a => a.kind === 'slot' || a.kind === 'newSlot');

/**
 * The movements an event stands for, from its public data only. A swap is two crossing moves; a match sends the
 * matched card to the discard and, on another player's card, my gift to the slot it emptied. Pure.
 */
export function planMovements(entry: KoboLogEntry, mySeat: number, context: MotionContext = {}): Movement[] {
  const moves = entry.data ? ofAction(entry, mySeat, context) : [];
  if (entry.eventType === 'ROUND_END') {
    moves.push({
      kind: 'reveal', from: { kind: 'table' }, to: { kind: 'table' }, card: null, flip: false,
      actor: entry.data?.seat ?? -1, byOpponent: false, turnDown: false, delayMs: TEMPO.reveal.holdBeforeMs,
      durationMs: TEMPO.reveal.flightMs, holdAfterMs: TEMPO.reveal.holdAfterMs, marks: [],
    });
  }
  return moves;
}

function ofAction(entry: KoboLogEntry, mySeat: number, context: MotionContext): Movement[] {
  const data = entry.data!;
  const actor = data.seat;
  const byOpponent = actor !== mySeat;
  const leg = legOf(byOpponent);
  const base = { actor, byOpponent, flip: false, turnDown: false };
  /** The discard top this card is about to cover (nothing when the pile was empty or unknown). */
  const coversDiscard = (): Partial<Movement> => (context.discardBefore ? { restsOn: context.discardBefore } : {});
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
      if (!own) return [];
      // The card I drew was seen face up: it goes to the slot as that same card, then turns face down there. A
      // bot's drawn card is never sent to me, so it can only travel as a back. The replaced card (face down in the
      // slot) turns face up on its way to the discard.
      const seen = actor === mySeat ? context.drawnFace ?? null : null;
      return [
        travel({ kind: 'hand' }, own, seen, { turnDown: seen !== null, restsOn: null }),
        travel(own, { kind: 'discard' }, data.card, { flip: true, leavesBack: true, ...coversDiscard() }),
      ];
    }
    case 'DISCARD_DRAWN':
      return [travel({ kind: 'hand' }, { kind: 'discard' }, data.card, coversDiscard())];
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
      return own && other ? [travel(own, other, null, { leavesBack: true }), travel(other, own, null, { leavesBack: true })] : [];
    }
    case 'MATCH': {
      if (data.success === false) return [travel({ kind: 'draw' }, { kind: 'newSlot', seat: actor })];
      const target = slot(data.targetSeat, data.targetSlot);
      if (!target) return [];
      const moves = [travel(target, { kind: 'discard' }, data.card, { flip: true, leavesBack: true, ...coversDiscard() })];
      const given = slot(actor, data.givenSlot);
      if (given && data.targetSeat !== actor) moves.push(travel(given, target, null, { leavesBack: true }, TEMPO.followMs));
      return moves;
    }
    default: return [];
  }
}
