import type { LobbyView } from '../lobby/LobbyView';

export type MorrisPhase = 'PLACEMENT' | 'MOVEMENT' | 'FINISHED';

/** What the server lets the viewer do now: only the current seat has one, and none once the game is over. */
export type MorrisAction = 'PLACE' | 'MOVE' | 'REMOVE';

/** `REDUCED` (a player is down to two pieces) and `DRAW_MOVE_LIMIT` (quiet plies) only occur on Six Men's Morris. */
export type MorrisReason =
  | 'LINE'
  | 'BLOCKED'
  | 'FORFEIT'
  | 'DRAW_AGREED'
  | 'DRAW_REPETITION'
  | 'REDUCED'
  | 'DRAW_MOVE_LIMIT';

/** The variants a lobby offers; the server leaves `variant` out of the state for the default, Tapatan. */
export type MorrisVariantKey = 'tapatan' | 'achi' | 'six';

export type MorrisColour = 'WHITE' | 'BLACK';

export interface MorrisPlayer {
  seat: number;
  /** Name the player typed; absent or empty when none. */
  name: string | null;
  current: boolean;
  piecesToPlace: number;
  /** Left out by the server while seat 0 plays White (the default). */
  colour?: MorrisColour;
  /** Pieces removed so far; written only on mill variants (Six Men's Morris). */
  lost?: number;
}

/** A pending removal: `seat` closed the mill `mill` (three points) and must now take an opponent piece. */
export interface MorrisRemoval {
  seat: number;
  mill: number[];
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
  // Mill variants only (Six Men's Morris); a 3x3 state carries none of these four fields.
  /** The pending removal, while a player who closed a mill has yet to take a piece. */
  removal?: MorrisRemoval;
  /** The opponent pieces the viewer may take now; only written during the viewer's own pending removal. */
  legalRemovals?: number[];
  /** Movement plies without a removal, only written from 40 (for the warning). */
  quietPlies?: number;
  /** The quiet-ply limit that draws the game (50), so the client does not hard-code it. */
  quietPlyLimit?: number;
}

export type MorrisView = LobbyView | MorrisState;

/** Public description of a command (`PLACE`, `MOVE`, and the `GAME_OVER` it caused): `action` tells which. */
export interface MorrisEventData {
  seat: number;
  action: 'PLACE' | 'MOVE';
  point: number | null;
  from: number | null;
  to: number | null;
  /** The three points of the mill this placement or move closed; absent when it closed none. */
  mill?: number[];
}

/** Public description of a removal (`REMOVE` event, or the `GAME_OVER` it caused): the point taken. */
export interface MorrisRemovalEventData {
  seat: number;
  action: 'REMOVE';
  point: number;
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
