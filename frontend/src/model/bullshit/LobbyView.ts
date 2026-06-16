export interface LobbyPlayer {
  seat: number;
  name: string | null;
  joined: boolean;
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
}
