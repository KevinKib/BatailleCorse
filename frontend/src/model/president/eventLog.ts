import type Card from '../Card';
import type { PresidentEventData } from './PresidentEvents';
import type { PresidentState } from './PresidentState';

/** Why a trick ended, as the server names it (`TrickEndReason`); an unknown value is kept as given. */
export type PresidentTrickEndReason = 'ALL_PASSED' | 'FIRST_OUT' | 'CUT' | 'QUAD' | (string & {});

/** A trick that a move closed: who takes the lead, why, and what lay on the pile when it closed. */
export interface PresidentTrickClosure {
  winner: number;
  reason: PresidentTrickEndReason;
  /** The seat whose play was on top when the trick closed (the closing play, or the play everyone passed on). */
  closer: number;
  /** That play's cards. */
  cards: Card[];
  /** How many cards the closed trick held. */
  size: number;
  /** Every card of the closed trick, in sweep order: the closing play first, then the earlier plays, newest first. */
  trickCards: Card[];
}

/**
 * One move at the table, as the screen draws it: the server's event data, plus what only the previous state knows
 * (the cards of a trick that the new state already shows empty, who was to play). Purely visual.
 */
export interface PresidentLogEntry {
  /** Increases with every entry of the session, so two equal moves are two entries. */
  id: number;
  eventType: string;
  action: 'PLAY' | 'PASS';
  seat: number;
  cards: Card[];
  /** The round the move was made in (null when the previous state is unknown). */
  round: number | null;
  /** A play by a seat that was not to play: the completion of the four. */
  outOfTurn: boolean;
  /** The move emptied the hand on a forbidden finish ("Out on a 2"). */
  outForbidden: boolean;
  /** The place taken when the move emptied the hand. */
  outPlace: number | null;
  closure: PresidentTrickClosure | null;
}

/** How many entries the store keeps (newest first). */
export const MAX_LOG = 20;

const NOT_MOVES = new Set(['START', 'ROUND_START']);

/** The entry of an event, or null when it is not a play or a pass (the start, a round start, an exchange, a ready). */
export function toLogEntry(
  id: number, eventType: string, data: PresidentEventData | null, before: PresidentState | null,
): PresidentLogEntry | null {
  if (!data || NOT_MOVES.has(eventType)) return null;
  if (data.action !== 'PLAY' && data.action !== 'PASS') return null;
  const action = data.action;
  const cards = action === 'PLAY' ? data.cards ?? [] : [];
  const plays = before?.trick.plays ?? [];
  const top = plays.length ? plays[plays.length - 1] : null;
  const closure: PresidentTrickClosure | null = data.trickWinner !== null && data.trickWinner !== undefined && data.trickEndReason
    ? {
      winner: data.trickWinner,
      reason: data.trickEndReason,
      closer: action === 'PLAY' ? data.seat : top?.seat ?? data.trickWinner,
      cards: action === 'PLAY' ? cards : top?.cards ?? [],
      size: plays.reduce((n, p) => n + p.cards.length, 0) + cards.length,
      // A pass closes on the play already on top (its cards lead the sweep once, not twice).
      trickCards: [
        ...(action === 'PLAY' ? cards : []),
        ...[...plays].reverse().flatMap(p => p.cards),
      ],
    }
    : null;
  return {
    id,
    eventType,
    action,
    seat: data.seat,
    cards,
    round: before?.round ?? null,
    outOfTurn: action === 'PLAY' && (eventType === 'COMPLETION' || (before !== null && before.currentSeat !== data.seat)),
    outForbidden: data.outForbidden === true,
    outPlace: data.outPlace ?? null,
    closure,
  };
}

/** The log with `entry` in front (newest first), capped at `MAX_LOG`; the same log when there is no entry. */
export function appendEntry(log: PresidentLogEntry[], entry: PresidentLogEntry | null): PresidentLogEntry[] {
  return entry ? [entry, ...log].slice(0, MAX_LOG) : log;
}
