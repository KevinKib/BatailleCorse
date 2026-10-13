import type { KoboEventData, KoboLogEntry } from './KoboEvents';
import type Card from '../Card';
import { slotKey } from './interaction';

/** Which sentence of the log says it (a key of `koboUi.log`; the texts live in the locales). */
export type KoboLogKey =
  | 'draw' | 'drawEmpty' | 'swap' | 'discard' | 'peek' | 'blindSwap' | 'kingSwap' | 'skipPower' | 'endTurn'
  | 'kobo' | 'ready' | 'matchOwn' | 'matchOther' | 'matchOtherNoGive' | 'matchMissed' | 'giveTen';

/** What a log sentence is about: who acted, whose card it touched and which one (slots are 0-based here). */
export interface KoboLogLine {
  key: KoboLogKey;
  actor: number;
  /** The player whose card was looked at, swapped or matched, or who received the gift. */
  owner: number | null;
  slot: number | null;
  targetSlot: number | null;
  givenSlot: number | null;
  card: Card | null;
}

/** The sentences for a command. A command that ended the round arrives as ROUND_END: `action` still names it. */
const BY_ACTION: Record<string, KoboLogKey> = {
  SWAP: 'swap', DISCARD_DRAWN: 'discard', PEEK: 'peek', BLIND_SWAP: 'blindSwap', KING_SWAP: 'kingSwap',
  SKIP_POWER: 'skipPower', END_TURN: 'endTurn', KOBO: 'kobo', READY: 'ready', GIVE_TEN: 'giveTen',
};

function keyOf(data: KoboEventData): KoboLogKey | null {
  switch (data.action) {
    case 'DRAW': return data.success === false ? 'drawEmpty' : 'draw';
    case 'MATCH':
      if (data.success === false) return 'matchMissed';
      if (data.targetSeat === data.seat) return 'matchOwn';
      return data.givenSlot !== null ? 'matchOther' : 'matchOtherNoGive';
    default: return BY_ACTION[data.action] ?? null;
  }
}

/** One log entry as a structured sentence, or null when the event carries nothing to say. Pure. */
export function describeEvent(entry: KoboLogEntry): KoboLogLine | null {
  const data = entry.data;
  if (!data) return null;
  const key = keyOf(data);
  if (!key) return null;
  return {
    key, actor: data.seat, owner: data.targetSeat, slot: data.slot, targetSlot: data.targetSlot,
    givenSlot: data.givenSlot, card: data.card,
  };
}

/** The slots (by `slotKey`) a log entry names, for the brief highlight. A missed match names none. */
export function eventHighlights(entry: KoboLogEntry | null): string[] {
  const data = entry?.data;
  if (!data) return [];
  const target = data.targetSeat !== null && data.targetSlot !== null ? slotKey(data.targetSeat, data.targetSlot) : null;
  const own = data.slot !== null ? slotKey(data.seat, data.slot) : null;
  switch (data.action) {
    case 'SWAP': return own ? [own] : [];
    case 'PEEK': return target ? [target] : [];
    case 'BLIND_SWAP':
    case 'KING_SWAP': return [own, target].filter((k): k is string => k !== null);
    case 'MATCH': {
      if (data.success !== true) return [];
      const given = data.givenSlot !== null ? slotKey(data.seat, data.givenSlot) : null;
      return [target, given].filter((k): k is string => k !== null);
    }
    default: return [];
  }
}

/** Events the table already shows (the drawn card, the turn marker, the ready checks): never the headline. */
const SHOWN_BY_THE_TABLE = new Set(['DRAW', 'END_TURN', 'READY']);

/**
 * The entry the visible log line says: the newest one the table does not already show, so an opponent's discard
 * or match is not replaced by his "ended the turn". `events` is newest first.
 */
export function headlineEvent(events: readonly KoboLogEntry[]): KoboLogEntry | null {
  return events.find(e => !SHOWN_BY_THE_TABLE.has(e.data?.action ?? e.eventType) && describeEvent(e) !== null) ?? null;
}
