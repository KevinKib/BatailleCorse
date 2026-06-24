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
  // Opaque host-selected game options (e.g. claimMode). Carried now; rendering the chosen
  // mode in the lobby is a deferred follow-up that owns BullshitGameScreen.vue.
  options?: Record<string, string>;
}
