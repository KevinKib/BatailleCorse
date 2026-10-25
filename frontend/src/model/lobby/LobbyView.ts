/** A game option the host may edit in the lobby, as declared by the game on the server. */
export interface OptionDescriptor {
  key: string;
  type: 'CHOICE';
  /** Permitted values, in display order. */
  values: string[];
  defaultValue: string;
  /** Where the texts live in the locale messages (`options.<game>.<key>`). */
  labelKey: string;
}

export interface LobbyPlayer {
  seat: number;
  name: string | null;
  joined: boolean;
  /** True for a computer-controlled seat (the backend always sends it). */
  bot?: boolean;
  /** Options of a computer seat, defaults filled; empty for a human. */
  seatOptions?: Record<string, string>;
  /** A human whose connection is down: the host may remove the seat. */
  disconnected?: boolean;
}

/** What the server tells one viewer about a lobby (the game is not started). */
export interface LobbyView {
  started: false;
  gameId: string;
  players: LobbyPlayer[];
  hostSeat: number;
  /** The viewer's own seat: the server is the source of truth, it moves when a seat above is vacated. */
  mySeat: number;
  minPlayers: number;
  maxPlayers: number;
  canStart: boolean;
  /** Current value of every option the game declares (defaults filled). */
  options?: Record<string, string>;
  optionDescriptors?: OptionDescriptor[];
  seatOptionDescriptors?: OptionDescriptor[];
  /** The viewer is the host and the game declares options. */
  canEditOptions?: boolean;
  /** Bot seats the host may remove right now (host only). */
  removableBotSeats?: number[];
  /** Increases with every mutation of the lobby: a view older than the one held is dropped. */
  lobbyVersion?: number;
  /** The host hid the game from the public list (absent on an older server: treat as public). */
  privateGame?: boolean;
}
