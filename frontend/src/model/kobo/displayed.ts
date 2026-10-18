import type { Movement, MotionAnchor } from './motion';
import type { KoboState } from './KoboState';
import { slotKey } from './interaction';

/**
 * What the table still SHOWS while the flights of an event play. The state is applied at once, but the places a
 * flight touches keep showing their old card until the copy lands; a match tapped on such a card must be judged on
 * what the player saw (its revision, the rank of the discard top), not on the latest state, or the server sees a
 * matching revision on a card of another rank and charges a penalty. Held values are keyed to an end time (a
 * timestamp), after which the latest state is the truth again. Pure: time is passed in.
 */
export interface DisplayedHold {
  /** The revision on screen of the slots a flight touches, by `slotKey`. */
  slots: Record<string, { revision: number; until: number }>;
  /** The rank on screen of the discard top, while a flight to the discard has not landed. */
  discard: { rank: string; until: number } | null;
}

/** What `koboInteraction` reads: the values on screen where they differ from the state's. */
export interface KoboDisplayed {
  slotRevisions: Record<string, number>;
  discardTopRank: string | undefined;
}

export const NO_HOLD: DisplayedHold = { slots: {}, discard: null };

/**
 * The values to keep on screen for the places the movements of one event touch, read from the state BEFORE the
 * event, until `until`. Reveals and every place that is not a slot or the discard are left out.
 */
export function planHold(before: KoboState | null, movements: Movement[], until: number): DisplayedHold {
  if (!before) return NO_HOLD;
  const slots: DisplayedHold['slots'] = {};
  let discard: DisplayedHold['discard'] = null;
  const touch = (anchor: MotionAnchor) => {
    if (anchor.kind === 'discard') {
      if (before.discardTop) discard = { rank: before.discardTop.rank, until };
      return;
    }
    if (anchor.kind !== 'slot') return;
    const slot = before.players.find(p => p.seat === anchor.seat)?.slots.find(s => s.slot === anchor.slot);
    if (slot) slots[slotKey(anchor.seat, anchor.slot)] = { revision: slot.revision, until };
  };
  for (const movement of movements) {
    if (movement.kind === 'reveal') continue;
    touch(movement.from);
    touch(movement.to);
  }
  return { slots, discard };
}

/** Adds a new hold to the one in force: a place already held keeps the value it showed first, and the later end. */
export function mergeHold(current: DisplayedHold, incoming: DisplayedHold): DisplayedHold {
  const slots = { ...current.slots };
  for (const [key, held] of Object.entries(incoming.slots)) {
    const known = slots[key];
    slots[key] = known ? { revision: known.revision, until: Math.max(known.until, held.until) } : held;
  }
  const discard = current.discard && incoming.discard
    ? { rank: current.discard.rank, until: Math.max(current.discard.until, incoming.discard.until) }
    : current.discard ?? incoming.discard;
  return { slots, discard };
}

/** What is still held at `now`: a value whose flight has landed (end time reached) is dropped. */
export function liveHold(hold: DisplayedHold, now: number): DisplayedHold {
  const slots = Object.fromEntries(Object.entries(hold.slots).filter(([, h]) => h.until > now));
  const discard = hold.discard && hold.discard.until > now ? hold.discard : null;
  return { slots, discard };
}

export function displayedOf(hold: DisplayedHold): KoboDisplayed {
  return {
    slotRevisions: Object.fromEntries(Object.entries(hold.slots).map(([key, h]) => [key, h.revision])),
    discardTopRank: hold.discard?.rank,
  };
}
