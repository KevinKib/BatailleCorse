import type { LobbyView } from '../lobby/LobbyView';
import type { MangolaDirection } from './direction';

export type MangolaPhase = 'PLACEMENT' | 'PLAY' | 'FINISHED';

/** The two halves of the board: side A owns rows 1 and 2 (pits 0..15), side B rows 3 and 4 (pits 16..31). */
export type MangolaSide = 'A' | 'B';

/** What the server lets the viewer do now (placement: `ARRANGE`, `CONFIRM_LAYOUT`, `EDIT_LAYOUT`; play: `SOW`). */
export type MangolaAction = 'ARRANGE' | 'CONFIRM_LAYOUT' | 'EDIT_LAYOUT' | 'SOW';

export type MangolaReason = 'NO_MOVE' | 'FORFEIT' | 'REPETITION' | 'ENDLESS_SOWING';

export interface MangolaPlayer {
  seat: number;
  /** Name the player typed; absent or empty when none. */
  name: string | null;
  /** True for the seat to move in the play phase. */
  current: boolean;
  side: MangolaSide;
  /** Seeds on the player's side; null while that side is hidden from the viewer. */
  seeds: number | null;
  /** The player confirmed their layout (placement phase). */
  ready: boolean;
  /** True for a computer seat; left out by the server for a human. */
  bot?: boolean;
  /** The level key of a computer seat (random, greedy, expert); left out for a human. */
  level?: string | null;
}

export type MangolaStepType = 'PICK' | 'DROP' | 'SKIP_EMPTY' | 'CAPTURE' | 'RELAY' | 'RESUME_AFTER';

/**
 * One step of a sowing. `pit` is the pit concerned (for a capture, the capturing pit); `count` the seeds taken
 * (PICK, RELAY, CAPTURE); `facing` and `behind` the captured pits (CAPTURE). The server leaves out null fields.
 */
export interface MangolaStep {
  type: MangolaStepType;
  pit?: number;
  count?: number;
  facing?: number;
  behind?: number;
}

/** The last move played, kept by the server so that a client reconnecting mid-animation can still show it. */
export interface MangolaLastMove {
  seat: number;
  pit: number;
  trace: MangolaStep[];
}

export type MangolaOutcome =
  | { status: 'ONGOING' }
  /** `winner` is a seat number, null when the game is drawn (repetition or endless sowing). */
  | { status: 'FINISHED'; winner: number | null; reason: MangolaReason };

/** One viewer's view of a running game. */
export interface MangolaState {
  started: true;
  id: string;
  gameType: string;
  version: number;
  phase: MangolaPhase;
  currentSeat: number;
  /** The seat that owns side A (rows 1 and 2). */
  seatA: number;
  /**
   * The sowing direction chosen by the host. The server always sends it; optional here so that the fixtures
   * written before it compile, and an absent one reads as the default (`directionOf`).
   */
  direction?: MangolaDirection;
  /**
   * The 32 pits, `row * 8 + column` (row 0 is the row named "1", column 0 the column named "a"). During a hidden
   * placement the viewer sees its own draft and null for each of the opponent's 16 pits.
   */
  board: (number | null)[];
  players: MangolaPlayer[];
  /** The pits the viewer may sow; empty unless it is the viewer's turn in the play phase. */
  legalPits: number[];
  availableActions: MangolaAction[];
  outcome: MangolaOutcome;
  /** The viewer's draft has no finite move on an otherwise empty board: the editor warns. */
  draftOnlyEndless: boolean;
  // Left out of the JSON while null: read an absent one as null.
  lastMove?: MangolaLastMove;
  /** How many times the current position has occurred, only written from 2. */
  repetitions?: number;
}

export type MangolaView = LobbyView | MangolaState;

/**
 * Public description of a command. `action` is the command (ARRANGE, CONFIRM_LAYOUT, EDIT_LAYOUT, SOW) even when
 * the broadcast event is the GAME_OVER it caused. A layout event carries the seat and nothing else, never the
 * counts; the reveal has no seat; a move carries its pit, trace and seeds captured.
 */
export interface MangolaEventData {
  seat?: number;
  action: 'ARRANGE' | 'CONFIRM_LAYOUT' | 'EDIT_LAYOUT' | 'SOW';
  pit?: number;
  trace?: MangolaStep[];
  captured?: number;
}

export interface MangolaResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: MangolaView | null;
}
