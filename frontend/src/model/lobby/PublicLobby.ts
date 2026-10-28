/** State of a listed game, in the order of the list: open first. */
export type PublicLobbyState = 'OPEN' | 'FULL' | 'IN_PLAY';

/**
 * One entry of the public game list (mirrors `PublicLobbyDto`). Only what is public: the host's display name,
 * the counts, the state and the game id (the join capability). No token, no seat, no timestamp.
 */
export interface PublicLobby {
  gameId: string;
  /** The server game type (`GameDefinition.serverType`). */
  gameType: string;
  /** What the host typed; null when none (the client then labels seat 1). */
  hostName: string | null;
  /** Taken seats, bots included. */
  joined: number;
  bots: number;
  maxPlayers: number;
  state: PublicLobbyState;
}

/**
 * The whole list of one game type, from `GET /api/lobbies/{gameType}` and the topic `/topic/lobbies/{gameType}`
 * (never a delta). A client drops a list whose `version` is older than the one it holds.
 */
export interface PublicLobbyList {
  gameType: string;
  lobbies: PublicLobby[];
  /** Every public entry of the type; more than `lobbies.length` when `truncated`. */
  total: number;
  truncated: boolean;
  version: number;
}
