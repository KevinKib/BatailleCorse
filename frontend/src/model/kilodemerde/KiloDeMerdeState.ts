import type Card from '../Card';
import type { LobbyView } from '../lobby/LobbyView';

/** Mirrors of the backend `KiloDeMerdeDto` (the viewer's projection of a started game); nothing here is derived. */

/** `AWAITING_START` (before the first round) and `ROUND_OVER` are the two waits where every seat confirms it is ready. */
export type KiloDeMerdePhase = 'AWAITING_START' | 'PASSING' | 'RACE' | 'ROUND_OVER' | 'FINISHED';

export type KiloDeMerdeAction = 'PASS' | 'SLAP' | 'READY';

export type KiloDeMerdeEndReason = 'ROUNDS_PLAYED' | 'PILE_EMPTY' | 'FORFEIT';

export type KiloDeMerdeOutcomeKind = 'RACE' | 'FALSE_SLAP' | 'STALLED' | 'VOIDED';

export interface KiloDeMerdePlayer {
  seat: number;
  /** Null for a bot or an unnamed seat: the client names it. */
  name: string | null;
  /** Computer player; omitted by the server when false. */
  bot?: boolean;
  /** Bot level (`easy`, `medium`, `hard`); only for a bot. */
  level?: string;
  /** Total kilos held (0 after a flush). */
  kilos: number;
  /** The kilo cards held, public all the time. */
  kiloCards: Card[];
  /** Whether the seat has chosen its card for the current step (public); the card itself is private. */
  chosen: boolean;
  /** Whether the seat confirmed it is ready (before the first round, between two rounds); public. Bots and absent seats count ready. */
  ready: boolean;
  /** Position in the slap order of the open or last race (1 is the first slapper), else null. */
  slapRank: number | null;
  /** False once the seat has forfeited. */
  active: boolean;
  /** Final place once the game is over (places may be shared), else null. */
  place: number | null;
}

/** The open race: the first slapper and every seat that slapped so far, in order. */
export interface KiloDeMerdeRace {
  firstSlapper: number;
  order: number[];
}

/** One draw of a round: `card` is null when the kilo pile was empty (the draw is skipped). */
export interface KiloDeMerdeDraw {
  seat: number;
  card: Card | null;
  flushed: boolean;
  kilosAfter: number;
}

export interface KiloDeMerdeSeatHand {
  seat: number;
  cards: Card[];
}

/** How the last round ended; every hand is public once the round is over. */
export interface KiloDeMerdeRoundOutcome {
  kind: KiloDeMerdeOutcomeKind;
  firstSlapper: number | null;
  slapOrder: number[];
  /** Seats whose hand was a four of a kind. */
  holders: number[];
  draws: KiloDeMerdeDraw[];
  hands: KiloDeMerdeSeatHand[];
}

export interface KiloDeMerdePlace {
  seat: number;
  place: number;
  kilos: number;
}

export interface KiloDeMerdeResult {
  standings: KiloDeMerdePlace[];
  reason: KiloDeMerdeEndReason;
}

/** The card I received at the last resolved step and from whom. */
export interface KiloDeMerdeReceived {
  card: Card;
  from: number;
}

/** The card I gave at the last resolved step and to whom. */
export interface KiloDeMerdeGiven {
  card: Card;
  to: number;
}

export interface KiloDeMerdeState {
  started: true;
  id: string;
  gameType: string;
  /** Increases with every change of the game; a state older than the one held is stale. */
  version: number;
  round: number;
  roundsTotal: number;
  /** The pass step within the round, from 1. */
  step: number;
  phase: KiloDeMerdePhase;
  pileSize: number;
  /** The game options in force, by key (defaults filled): `rounds`. */
  options: Record<string, string>;
  /** Server epoch ms when the pass timer expires, for the countdown; null when off. */
  passTimerEndsAt: number | null;
  /** Server epoch ms when the open race times out; null outside a race. */
  raceEndsAt: number | null;
  /** Server epoch ms when the wait for the ready confirmations times out (the missing seats are counted ready); null when off or not waiting. */
  readyEndsAt: number | null;
  players: KiloDeMerdePlayer[];
  race: KiloDeMerdeRace | null;
  /** Only once the round is over. */
  lastOutcome: KiloDeMerdeRoundOutcome | null;
  /** Only once the game is over. */
  result: KiloDeMerdeResult | null;
  /** The viewer's own hand; the other hands are never sent during a round. */
  hand: Card[];
  /** The card the viewer chose this step, else null. */
  myChoice: Card | null;
  lastReceived: KiloDeMerdeReceived | null;
  lastGiven: KiloDeMerdeGiven | null;
  availableActions: KiloDeMerdeAction[];
}

export type KiloDeMerdeView = LobbyView | KiloDeMerdeState;

export type KiloDeMerdeEventType =
  | 'START'
  | 'ROUND_START'
  | 'PASS_CHOSEN'
  | 'STEP'
  | 'RACE'
  | 'SLAP'
  | 'FALSE_SLAP'
  | 'DRAW'
  | 'FLUSH'
  | 'ROUND_OVER'
  | 'READY'
  | 'GAME_OVER'
  | 'FORFEIT';

/** What a command did, public to every seat. Only the fields of the event type are set. */
export interface KiloDeMerdeEventData {
  seat: number | null;
  round: number | null;
  step: number | null;
  /** The drawn card of a DRAW (null when the pile was empty). */
  card: Card | null;
  kilosAfter: number | null;
  /** The place in the slap order of a SLAP. */
  rank: number | null;
  /** The number of cards returned to the pile by a FLUSH. */
  returnedCount: number | null;
}

export interface KiloDeMerdeResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: KiloDeMerdeView | null;
}
