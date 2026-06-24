// Per-seat lifecycle event payloads broadcast on /topic/game/{id}/seat/{token}.
// Game-agnostic: any game on the shared session/presence layer emits these.
export interface OpponentDisconnectedEventData {
  disconnectedSeat: number;
  deadlineEpochMs: number;
}

export interface OpponentReconnectedEventData {
  reconnectedSeat: number;
}

export interface ForfeitEventData {
  loserSeat: number;
}

/** The three game-agnostic per-seat lifecycle event types (single source of truth). */
export const SEAT_LIFECYCLE_EVENT = {
  OPPONENT_DISCONNECTED: 'OPPONENT_DISCONNECTED',
  OPPONENT_RECONNECTED: 'OPPONENT_RECONNECTED',
  FORFEIT: 'FORFEIT',
} as const;

export type SeatLifecycleEventType = typeof SEAT_LIFECYCLE_EVENT[keyof typeof SEAT_LIFECYCLE_EVENT];
