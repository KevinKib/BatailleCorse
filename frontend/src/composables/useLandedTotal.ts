import { onBeforeUnmount, ref, watch, type Ref } from 'vue';
import type { NinetyNineLogEntry } from '../model/ninetynine/eventLog';

/** Safety net: a play whose picture never reports lands this long after it appeared, so the total never stays stuck. */
const LANDING_FALLBACK_MS = 30_000;

export interface LandedTotal {
  /** The total the table shows. */
  shown: Ref<number>;
  /**
   * Tells when a play's picture has been admitted to the animation queue: its total shows `landsInMs` from now (0: at
   * once). Called by the motion's `onPlayed`, with the real start of that play in the queue.
   */
  landsIn: (entry: NinetyNineLogEntry, landsInMs: number) => void;
}

/**
 * The total the table shows: the server's total, but a play's total only shows when its card lands, counted from the
 * real start of its animation in the queue (never from when the play arrived), and the plays in the order they were
 * played: while plays are queued the number stays on what has landed. Anything else that moves the total (a new round,
 * a reload) shows at once. Purely visual: the state itself never waits.
 */
export function useLandedTotal(total: () => number, events: () => readonly NinetyNineLogEntry[]): LandedTotal {
  const shown = ref(total());
  // The plays waiting to land, by entry id (oldest first); `told` keeps a landing announced before the play was seen.
  const pending = new Map<number, NinetyNineLogEntry>();
  const told = new Map<number, number>();
  const timers = new Map<number, ReturnType<typeof setTimeout>>();
  let seen = events()[0]?.id ?? -1;

  function land(id: number) {
    const timer = timers.get(id);
    if (timer !== undefined) clearTimeout(timer);
    timers.delete(id);
    told.delete(id);
    const entry = pending.get(id);
    if (!entry) return;
    pending.delete(id);
    shown.value = pending.size === 0 ? total() : entry.totalAfter ?? total();
  }
  function arm(id: number, ms: number) {
    const timer = timers.get(id);
    if (timer !== undefined) clearTimeout(timer);
    if (ms <= 0) { land(id); return; }
    timers.set(id, setTimeout(() => land(id), ms));
  }
  function clearAll() {
    for (const timer of timers.values()) clearTimeout(timer);
    timers.clear();
    pending.clear();
    told.clear();
  }

  watch(() => [total(), events()[0]?.id ?? -1] as const, () => {
    const fresh = events().filter(e => e.id > seen).reverse();
    seen = Math.max(seen, events()[0]?.id ?? -1);
    const roundStart = fresh.filter(e => e.eventType === 'ROUND_START').map(e => e.id);
    if (roundStart.length > 0) clearAll();
    const lastReset = Math.max(-1, ...roundStart);
    for (const e of fresh) {
      if (e.eventType !== 'PLAY' || e.card === null || e.seat === null || e.totalAfter === null || e.id < lastReset) continue;
      pending.set(e.id, e);
      arm(e.id, told.get(e.id) ?? LANDING_FALLBACK_MS);
    }
    if (pending.size === 0) shown.value = total();
  });

  function landsIn(entry: NinetyNineLogEntry, landsInMs: number) {
    if (pending.has(entry.id)) arm(entry.id, landsInMs);
    else told.set(entry.id, landsInMs);
  }
  onBeforeUnmount(clearAll);
  return { shown, landsIn };
}
