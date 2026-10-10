export interface LobbyPlayer {
  seat: number;
  name: string | null;
  joined: boolean;
  // True for a computer-controlled seat (the backend always sends it).
  bot?: boolean;
}

export interface LobbyView {
  started: false;
  gameId: string;
  players: LobbyPlayer[];
  hostSeat: number;
  mySeat: number;
  minPlayers: number;
  maxPlayers: number;
  canStart: boolean;
  // Host-selected game options; `claimMode` is shown in the lobby.
  options?: Record<string, string>;
  // Bot seats the host may remove right now (host only).
  removableBotSeats?: number[];
}
