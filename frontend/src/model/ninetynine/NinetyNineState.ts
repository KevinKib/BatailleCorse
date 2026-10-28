import type Card from '../Card';
import type { LobbyView } from '../lobby/LobbyView';

/** Mirrors of the backend `NinetyNineDto` (the viewer's projection of a started game); nothing here is derived. */

export type NinetyNinePhase = 'PLAYING' | 'ROUND_OVER' | 'FINISHED';

export type NinetyNineDirection = 'CLOCKWISE' | 'COUNTER_CLOCKWISE';

export type NinetyNineAction = 'PLAY';

export type NinetyNineEndReason = 'LAST_STANDING' | 'QUICK' | 'FORFEIT';

export interface NinetyNinePlayer {
  seat: number;
  /** Null for a bot or an unnamed seat: the client names it. */
  name: string | null;
  /** Computer player; omitted by the server when false. */
  bot?: boolean;
  /** Bot level (`easy`, `medium`, `hard`); only for a bot. */
  level?: string;
  lives: number;
  handSize: number;
  eliminated: boolean;
  /** Final place once eliminated or the game is over (shared places in quick mode), else null. */
  place: number | null;
}

/** The last card played: the total before and after, the Ace value chosen, whether the direction flipped. */
export interface NinetyNineLastPlay {
  seat: number;
  card: Card;
  /** 1 or 11 for an Ace, null for any other card. */
  aceValue: number | null;
  totalBefore: number;
  totalAfter: number;
  reversed: boolean;
}

/** The last bust: the stuck hand is public, it proves that no card could be played. */
export interface NinetyNineLastBust {
  seat: number;
  totalBefore: number;
  hand: Card[];
  livesLeft: number;
  eliminated: boolean;
}

/** What the server says about one card of the viewer's hand: playable or not, and the Ace values allowed. */
export interface NinetyNinePlayable {
  card: Card;
  safe: boolean;
  /** The Ace values allowed (`[1]` or `[1, 11]`); empty for any other card. */
  aceValues: number[];
}

export type NinetyNineOutcome =
  | { status: 'ONGOING' }
  | { status: 'FINISHED'; winners: number[]; reason: NinetyNineEndReason };

export interface NinetyNineState {
  started: true;
  id: string;
  gameType: string;
  /** Increases with every change of the game; a state older than the one held is stale. */
  version: number;
  round: number;
  phase: NinetyNinePhase;
  /** The running total, 0 to 99. */
  total: number;
  direction: NinetyNineDirection;
  currentSeat: number;
  pileTop: Card | null;
  pileCount: number;
  stockSize: number;
  /** The game options in force, by key (defaults filled): `lives`, `quick`. */
  options: Record<string, string>;
  players: NinetyNinePlayer[];
  lastPlay: NinetyNineLastPlay | null;
  lastBust: NinetyNineLastBust | null;
  outcome: NinetyNineOutcome;
  /** The seat on turn holds no safe card: it must play a card that goes over 99 and lose a life. */
  stuck: boolean;
  /** The viewer's own hand; the other hands are never sent. */
  hand: Card[];
  /** Per card of the viewer's hand: safe or not, and the Ace values allowed. */
  playable: NinetyNinePlayable[];
  availableActions: NinetyNineAction[];
}

export type NinetyNineView = LobbyView | NinetyNineState;

export type NinetyNineEventType =
  | 'START' | 'ROUND_START' | 'PLAY' | 'RESHUFFLE' | 'BUST' | 'ELIMINATED' | 'GAME_OVER';

/** What a command did, public to every seat. Only the fields of the event type are set. */
export interface NinetyNineEventData {
  seat: number | null;
  card: Card | null;
  aceValue: number | null;
  totalAfter: number | null;
  reversed: boolean | null;
  /** The stuck hand of a BUST. */
  hand: Card[] | null;
  livesLeft: number | null;
  place: number | null;
  /** The stock size after a RESHUFFLE. */
  stockSize: number | null;
}

export interface NinetyNineResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: NinetyNineView | null;
}
