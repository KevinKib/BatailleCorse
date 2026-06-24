import { ref, computed } from 'vue';
import type { Ref, ComputedRef } from 'vue';
import type {
  OpponentDisconnectedEventData,
  OpponentReconnectedEventData,
  ForfeitEventData,
} from '../model/SeatLifecycleEvents';
import { SEAT_LIFECYCLE_EVENT } from '../model/SeatLifecycleEvents';

// Re-exported for existing importers; the canonical definition lives in the model
// so the framework-free application layer can import it without pulling in Vue.
export { SEAT_LIFECYCLE_EVENT };

/** How long the "Player N forfeited" notice stays up before auto-dismissing. */
export const FORFEIT_NOTICE_HOLD_MS = 4000;

export interface SeatPresence {
  disconnections: Ref<Record<number, number>>;       // seat -> deadlineEpochMs
  forfeitNotice: Ref<{ seat: number } | null>;
  liveDisconnections: ComputedRef<Record<number, number>>;
  applyPresenceEvent(eventType: string, eventData: unknown): void;
  reset(): void;
}

export interface UseSeatPresenceOptions {
  /** Seats currently present at the table; used to drop stale disconnect entries. */
  presentSeats?: () => number[];
  forfeitNoticeHoldMs?: number;
}

/**
 * Transient opponent-presence state reduced from the per-seat lifecycle events.
 * Game-agnostic: hold a map of disconnected seats (seat -> server deadline) plus a
 * timed-hold forfeit notice. Mirrors the `reveal` transient-state pattern.
 */
export function useSeatPresence(options: UseSeatPresenceOptions = {}): SeatPresence {
  const { presentSeats, forfeitNoticeHoldMs = FORFEIT_NOTICE_HOLD_MS } = options;

  const disconnections = ref<Record<number, number>>({});
  const forfeitNotice = ref<{ seat: number } | null>(null);
  let forfeitTimer: ReturnType<typeof setTimeout> | null = null;

  const liveDisconnections = computed<Record<number, number>>(() => {
    if (!presentSeats) return disconnections.value;
    const present = new Set(presentSeats());
    return Object.fromEntries(
      Object.entries(disconnections.value).filter(([seat]) => present.has(Number(seat))),
    );
  });

  function applyPresenceEvent(eventType: string, eventData: unknown): void {
    switch (eventType) {
      case SEAT_LIFECYCLE_EVENT.OPPONENT_DISCONNECTED: {
        const { disconnectedSeat, deadlineEpochMs } = eventData as OpponentDisconnectedEventData;
        disconnections.value = { ...disconnections.value, [disconnectedSeat]: deadlineEpochMs };
        break;
      }
      case SEAT_LIFECYCLE_EVENT.OPPONENT_RECONNECTED: {
        const { reconnectedSeat } = eventData as OpponentReconnectedEventData;
        const next = { ...disconnections.value };
        delete next[reconnectedSeat];
        disconnections.value = next;
        break;
      }
      case SEAT_LIFECYCLE_EVENT.FORFEIT: {
        const { loserSeat } = eventData as ForfeitEventData;
        const next = { ...disconnections.value };
        delete next[loserSeat];
        disconnections.value = next;
        forfeitNotice.value = { seat: loserSeat };
        if (forfeitTimer !== null) clearTimeout(forfeitTimer);
        forfeitTimer = setTimeout(() => {
          forfeitNotice.value = null;
          forfeitTimer = null;
        }, forfeitNoticeHoldMs);
        break;
      }
    }
  }

  function reset(): void {
    disconnections.value = {};
    forfeitNotice.value = null;
    if (forfeitTimer !== null) {
      clearTimeout(forfeitTimer);
      forfeitTimer = null;
    }
  }

  return { disconnections, forfeitNotice, liveDisconnections, applyPresenceEvent, reset };
}
