import type { LobbyView } from '../lobby/LobbyView';

export type MorrisPhase = 'PLACEMENT' | 'MOVEMENT' | 'FINISHED';

/** What the server lets the viewer do now: only the current seat has one, and none once the game is over. */
export type MorrisAction = 'PLACE' | 'MOVE';

export type MorrisReason = 'LINE' | 'BLOCKED' | 'FORFEIT';

export interface MorrisPlayer {
  seat: number;
  /** Name the player typed; absent or empty when none. */
  name: string | null;
  current: boolean;
  piecesToPlace: number;
}

export interface MorrisMove {
  from: number;
  to: number;
}

export type MorrisOutcome =
  | { status: 'ONGOING' }
  /** `winner` is a seat number (unlike Bullshit's string `winnerId`). */
  | { status: 'FINISHED'; winner: number; reason: MorrisReason };

/** One viewer's view of a running game: the board holds the owner seat of each point, row by row. */
export interface MorrisState {
  started: true;
  id: string;
  gameType: string;
  version: number;
  phase: MorrisPhase;
  currentSeat: number;
  board: (number | null)[];
  players: MorrisPlayer[];
  /** The viewer's legal moves; empty unless it is the viewer's turn in the movement phase. */
  legalMoves: MorrisMove[];
  availableActions: string[];
  /** The three points of the winning line; empty unless the game was won by a line. */
  winningLine: number[];
  outcome: MorrisOutcome;
}

export type MorrisView = LobbyView | MorrisState;

/** Public description of a command (`PLACE`, `MOVE`, and the `GAME_OVER` it caused): `action` tells which. */
export interface MorrisEventData {
  seat: number;
  action: 'PLACE' | 'MOVE';
  point: number | null;
  from: number | null;
  to: number | null;
}

export interface MorrisResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: MorrisView | null;
}
