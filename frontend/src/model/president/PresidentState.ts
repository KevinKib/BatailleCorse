import type Card from '../Card';
import type { LobbyView } from '../lobby/LobbyView';

/** Mirrors of the backend `PresidentDto` (the viewer's projection of a started game); nothing here is derived. */

export type PresidentPhase = 'EXCHANGE' | 'PLAYING' | 'ROUND_OVER' | 'FINISHED';

export type PresidentTitle = 'PRESIDENT' | 'VICE_PRESIDENT' | 'NEUTRAL' | 'VICE_SCUM' | 'SCUM';

export type PresidentAction = 'PLAY' | 'PASS' | 'COMPLETE' | 'GIVE_BACK' | 'READY';

/** A set of `count` cards of one rank. The only kind of play for now (runs come with their own PR). */
export interface PresidentPattern {
  kind: 'SET';
  rank: string;
  count: number;
}

export interface PresidentPlayer {
  seat: number;
  name: string | null;
  /** Computer player; omitted by the server when false. */
  bot?: boolean;
  handSize: number;
  /** The title in force this round (null in round 1). */
  title: PresidentTitle | null;
  /** Finishing place of this round, null while still holding cards. */
  place: number | null;
  out: boolean;
  passed: boolean;
  points: number;
  /** Has pressed "ready" on the round-over barrier. */
  ready: boolean;
  /** Went out on a forbidden finish: out at once, but placed last when the round ends (`place` stays null until then). */
  forbiddenFinish: boolean;
}

export interface PresidentTrickPlay {
  seat: number;
  cards: Card[];
}

export interface PresidentTrick {
  leader: number | null;
  plays: PresidentTrickPlay[];
  /** The pattern to beat, null when the next play leads. */
  top: PresidentPattern | null;
  lastPlayer: number | null;
  passed: number[];
  /** BEAT (play higher), MATCH_OR_PASS (equal play: the same rank and count, or pass) or FREE (a lead). */
  constraint: 'FREE' | 'BEAT' | 'MATCH_OR_PASS';
  /** The rank and count the next play must match; only under MATCH_OR_PASS. */
  requiredRank?: string | null;
  requiredCount?: number | null;
  /** The run of equal plays so far (public); null when no play is on the table. */
  chain?: PresidentChain | null;
}

export interface PresidentChain {
  rank: string;
  cards: Card[];
  /** How many plays make the chain. */
  plays: number;
}

/** A title pair of the exchange: seats and a count only, so every player may see it. */
export interface PresidentExchangePair {
  high: number;
  low: number;
  count: number;
  done: boolean;
}

export interface PresidentRoundPlayer {
  seat: number;
  place: number;
  title: PresidentTitle | null;
  points: number;
  total: number;
  forbiddenFinish: boolean;
}

export interface PresidentRound {
  round: number;
  finishingOrder: number[];
  players: PresidentRoundPlayer[];
}

export interface PresidentStanding {
  seat: number;
  points: number;
}

export type PresidentOutcome =
  | { status: 'ONGOING' }
  | { status: 'FINISHED'; winners: number[]; reason: string };

export interface PresidentState {
  started: true;
  id: string;
  gameType: string;
  /** Increases with every change of the game; a state older than the one held is stale. */
  version: number;
  round: number;
  /** The target of a rounds match; null for a points or endless match. */
  rounds: number | null;
  phase: PresidentPhase;
  currentSeat: number;
  /** The game options in force, by key (defaults filled): what the rule chips are read from. */
  options: Record<string, string>;
  players: PresidentPlayer[];
  trick: PresidentTrick;
  /** The title pairs of the exchange of this round; empty outside it. */
  exchange: PresidentExchangePair[];
  lastRound: PresidentRound | null;
  /** Seats ranked by points. */
  standings: PresidentStanding[];
  outcome: PresidentOutcome;
  /** The viewer's own hand; the other hands are never sent. */
  hand: Card[];
  /** The cards the viewer received in the exchange. */
  received: Card[];
  /** The cards the viewer gave in the exchange. */
  given: Card[];
  /** How many cards the viewer still has to give back. */
  toGiveBack: number;
  /** What the viewer may play right now; empty unless it is the viewer's turn. */
  legalPlays: PresidentPattern[];
  /** The cards that complete the four, only for the seat that holds them while the window is open. */
  completion?: Card[];
  availableActions: PresidentAction[];
}

export type PresidentView = LobbyView | PresidentState;
