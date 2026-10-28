import type Card from '../Card';
import type { NinetyNineEventData, NinetyNineState } from './NinetyNineState';

/**
 * One move at the table, as the screen draws it: the server's event data, plus what only the state before it knows
 * (the total, the pile top). Purely visual.
 */
export interface NinetyNineLogEntry {
  /** Increases with every entry of the session, so two equal moves are two entries. */
  id: number;
  eventType: string;
  seat: number | null;
  card: Card | null;
  aceValue: number | null;
  /** The total before the event (null when the state before is unknown). */
  totalBefore: number | null;
  totalAfter: number | null;
  reversed: boolean;
  /** The stuck hand of a bust. */
  hand: Card[] | null;
  livesLeft: number | null;
  place: number | null;
  stockSize: number | null;
  /** The round the event happened in (null when the state before is unknown). */
  round: number | null;
  /** The pile top the event covers (null when the pile was empty or the state before is unknown). */
  pileTopBefore: Card | null;
}

/** How many entries the store keeps (newest first). */
export const MAX_LOG = 20;

/** The entry of an event; null for the start of the game, which moves nothing. */
export function toLogEntry(
  id: number, eventType: string, data: NinetyNineEventData | null, before: NinetyNineState | null,
): NinetyNineLogEntry | null {
  if (eventType === 'START') return null;
  return {
    id,
    eventType,
    seat: data?.seat ?? null,
    card: data?.card ?? null,
    aceValue: data?.aceValue ?? null,
    totalBefore: before?.total ?? null,
    totalAfter: data?.totalAfter ?? null,
    reversed: data?.reversed === true,
    hand: data?.hand ?? null,
    livesLeft: data?.livesLeft ?? null,
    place: data?.place ?? null,
    stockSize: data?.stockSize ?? null,
    round: before?.round ?? null,
    pileTopBefore: before?.pileTop ?? null,
  };
}

/** The log with `entry` in front (newest first), capped at `MAX_LOG`; the same log when there is no entry. */
export function appendEntry(log: NinetyNineLogEntry[], entry: NinetyNineLogEntry | null): NinetyNineLogEntry[] {
  return entry ? [entry, ...log].slice(0, MAX_LOG) : log;
}
