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
