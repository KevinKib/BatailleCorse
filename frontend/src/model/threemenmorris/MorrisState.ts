import type { LobbyView } from '../lobby/LobbyView';

export type MorrisPhase = 'PLACEMENT' | 'MOVEMENT' | 'FINISHED';

/** What the server lets the viewer do now: only the current seat has one, and none once the game is over. */
export type MorrisAction = 'PLACE' | 'MOVE';

export type MorrisReason = 'LINE' | 'BLOCKED' | 'FORFEIT' | 'DRAW_AGREED' | 'DRAW_REPETITION';

/** The variants a lobby offers; the server leaves `variant` out of the state for the default, Tapatan. */
export type MorrisVariantKey = 'tapatan' | 'achi' | 'picaria';

export type MorrisColour = 'WHITE' | 'BLACK';

export interface MorrisPlayer {
  seat: number;
  /** Name the player typed; absent or empty when none. */
  name: string | null;
  current: boolean;
  piecesToPlace: number;
  /** Left out by the server while seat 0 plays White (the default). */
  colour?: MorrisColour;
}

export interface MorrisMove {
  from: number;
  to: number;
}

export type MorrisOutcome =
  | { status: 'ONGOING' }
  /** `winner` is a seat number (unlike Bullshit's string `winnerId`), null when the game is drawn. */
  | { status: 'FINISHED'; winner: number | null; reason: MorrisReason };

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
  // The fields below are left out of the JSON when they hold their default: read an absent one as the default.
  /** Absent means `tapatan`. */
  variant?: MorrisVariantKey;
  /** Points the viewer may place a piece on; absent means every free point (Tapatan). */
  legalPlacements?: number[];
  /** Absent means seat 0 plays White. */
  whiteSeat?: number;
  /** The pending draw offer, if any; a move by either player cancels it. */
  drawOffer?: { seat: number } | null;
  /** How many times the current position has occurred, only written from 2. */
  repetitions?: number;
  /**
   * Draw commands the viewer may send, only written when they differ from the default: absent means
   * `OFFER_DRAW` in the movement phase and none otherwise. Written empty: an offer of mine waits, or I offered
   * since my last move.
   */
  drawActions?: string[];
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

/** Public description of a draw command; an accepted offer comes as the `GAME_OVER` with `ACCEPT_DRAW`. */
export interface MorrisDrawEventData {
  seat: number;
  action: 'OFFER_DRAW' | 'DECLINE_DRAW' | 'ACCEPT_DRAW';
}

export interface MorrisResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: MorrisView | null;
}
