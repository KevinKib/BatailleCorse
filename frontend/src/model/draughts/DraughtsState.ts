import type { LobbyView } from '../lobby/LobbyView';

/** The DTO of the draughts game, per seat (spec `docs/specs/2026-10-28-draughts-design.md`, "Network contract"). */

export type DraughtsPhase = 'PLAY' | 'FINISHED';

/** What the server lets the viewer do now. */
export type DraughtsAction = 'MOVE' | 'OFFER_DRAW' | 'ACCEPT_DRAW' | 'DECLINE_DRAW';

export type DraughtsColour = 'WHITE' | 'BLACK';

/** A square of the board: `w` white man, `W` white king, `b` black man, `B` black king, null when empty. */
export type DraughtsPieceCode = 'w' | 'W' | 'b' | 'B';

/** Fifty entries, index `square - 1` (squares are numbered 1 to 50 the FMJD way). */
export type DraughtsBoard = (DraughtsPieceCode | null)[];

export type DraughtsReason =
  | 'NO_PIECES'
  | 'BLOCKED'
  | 'FORFEIT'
  | 'DRAW_AGREED'
  | 'DRAW_REPETITION'
  | 'DRAW_KINGS_ONLY'
  | 'DRAW_ENDGAME';

export interface DraughtsPlayer {
  seat: number;
  /** Name the player typed; null for a bot or a nameless human. */
  name: string | null;
  colour: DraughtsColour;
  current: boolean;
  men: number;
  kings: number;
  /** Opponent pieces taken so far. */
  captured: number;
  /** True for a computer seat; left out by the server for a human. */
  bot?: boolean;
  /** The level key of a computer seat (random, greedy, expert); left out for a human. */
  level?: string | null;
}

/** A legal move: `path` is the landing squares after `from`, ending with `to`; `captures` in capture order. */
export interface DraughtsLegalMove {
  from: number;
  to: number;
  path: number[];
  captures: number[];
  promotes: boolean;
}

export interface DraughtsLastMove extends DraughtsLegalMove {
  seat: number;
}

export interface DraughtsDrawOffer {
  by: number;
}

export interface DraughtsEndgameCounter {
  case: 'SIXTEEN' | 'FIVE';
  plies: number;
  limit: number;
}

/** Each part is present only once it matters (king-only plies from 30, the endgame as soon as it applies). */
export interface DraughtsDrawCounters {
  kingOnlyPlies?: number;
  kingOnlyLimit?: number;
  endgame?: DraughtsEndgameCounter;
}

export type DraughtsOutcome =
  | { status: 'ONGOING' }
  | { status: 'FINISHED'; winner: number | null; reason: DraughtsReason };

export interface DraughtsState {
  started: true;
  id: string;
  gameType: 'draughts';
  version: number;
  phase: DraughtsPhase;
  currentSeat: number;
  whiteSeat: number;
  board: DraughtsBoard;
  players: DraughtsPlayer[];
  availableActions: DraughtsAction[];
  /** The viewer's legal moves, on its turn only. */
  legalMoves: DraughtsLegalMove[];
  captureRequired: boolean;
  /** The count of the majority rule, for the compulsory-capture hint. */
  maxCaptures: number;
  lastMove?: DraughtsLastMove;
  drawOffer?: DraughtsDrawOffer;
  drawCounters?: DraughtsDrawCounters;
  /** Repetition count of the current position, sent from 2. */
  repetitions?: number;
  /** The notation of every move (`32-28`, `19x30`), oldest first. */
  history: string[];
  outcome: DraughtsOutcome;
}

export type DraughtsView = LobbyView | DraughtsState;

export type DraughtsEventType = 'START' | 'MOVE' | 'DRAW_OFFERED' | 'DRAW_DECLINED' | 'GAME_OVER';

/** Data of a `MOVE` event, and of `GAME_OVER` when the game ended on a move. */
export interface DraughtsMoveEventData {
  seat: number;
  from: number;
  to: number;
  path: number[];
  captures: number[];
  promotes: boolean;
}

/** Data of `DRAW_OFFERED` and `DRAW_DECLINED`, and of the `GAME_OVER` of an accepted draw: who did it. */
export interface DraughtsSeatEventData {
  seat: number;
}

export interface DraughtsResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: DraughtsView | null;
}

/** Payload of `/app/draughts/move`: the client always sends the captured set. */
export interface DraughtsMovePayload {
  gameId: string;
  token: string;
  from: number;
  to: number;
  captures: number[];
}
