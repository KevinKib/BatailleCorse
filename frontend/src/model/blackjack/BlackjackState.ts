import type Card from '../Card';
import type { LobbyView } from '../lobby/LobbyView';

/**
 * Mirrors of the backend `BlackjackDto` (the viewer's projection of a started game); nothing here is derived:
 * totals, soft flags and statuses come from the server, the client never counts cards.
 */

export type BlackjackPhase = 'BETTING' | 'PLAYING' | 'FINISHED';

export type BlackjackAction = 'BET' | 'HIT' | 'STAND' | 'DOUBLE' | 'SPLIT';

export type BlackjackEndReason = 'HANDS_PLAYED' | 'LAST_STANDING' | 'BROKE' | 'FORFEIT';

export type BlackjackHandStatus = 'ACTIVE' | 'STOOD' | 'BUST' | 'BLACKJACK' | 'WAITING';

/** The refusal codes of a command, answered to the sender alone (the client never renders a server message). */
export type BlackjackErrorCode =
  | 'NOT_YOUR_TURN'
  | 'WRONG_PHASE'
  | 'ALREADY_BET'
  | 'INVALID_BET'
  | 'ACTION_NOT_ALLOWED'
  | 'GAME_FINISHED';

export interface BlackjackHand {
  cards: Card[];
  total: number;
  soft: boolean;
  status: BlackjackHandStatus;
  doubled: boolean;
  fromSplit: boolean;
  /** The net gain of the hand once settled, else absent. */
  net?: number;
  /** Client only (`shownState`): some cards of the hand have not landed yet, so its total is not told. */
  pending?: boolean;
}

export interface BlackjackDealer {
  upCard: Card | null;
  /** The hole card is in the list only once shown. */
  cards: Card[];
  holeHidden: boolean;
  /** Total of the shown cards. */
  total: number;
  soft: boolean;
  blackjack: boolean;
  bust: boolean;
  /** Client only (`shownState`): the dealer's cards are still being dealt or turned, so the total is not told. */
  pending?: boolean;
}

export interface BlackjackSeat {
  seat: number;
  /** Null for a bot or an unnamed seat: the client names it. */
  name: string | null;
  /** Computer player; omitted by the server when false. */
  bot?: boolean;
  /** Bot level (`easy`, `medium`, `hard`); only for a bot. */
  level?: string;
  stack: number;
  betPlaced: boolean;
  /** Null for another seat during BETTING (sealed bets). */
  bet: number | null;
  hands: BlackjackHand[];
  eliminated: boolean;
  forfeited: boolean;
  /** Final place once eliminated or the game is over (shared places), else null. */
  place: number | null;
  /** Current rank by stack. */
  standing: number;
}

export interface BlackjackLastHandHand {
  cards: Card[];
  total: number;
  status: BlackjackHandStatus;
  net: number;
}

export interface BlackjackLastHandSeat {
  seat: number;
  hands: BlackjackLastHandHand[];
  net: number;
}

/** The previous hand, kept during the next BETTING. */
export interface BlackjackLastHand {
  handNumber: number;
  dealer: { cards: Card[]; total: number; bust: boolean; blackjack: boolean };
  seats: BlackjackLastHandSeat[];
  /** The seat(s) with the largest gain of the hand; empty when nobody gained. */
  bestSeats: number[];
}

export interface BlackjackBetLimits {
  min: number;
  max: number;
}

export type BlackjackOutcome =
  | { status: 'ONGOING' }
  | {
      status: 'FINISHED';
      /** Places by seat (shared places repeat); empty in solo. */
      places: Record<string, number>;
      stacks: Record<string, number>;
      reason: BlackjackEndReason;
      /** Solo only: final stack minus the starting stack. */
      net?: number;
    };

export interface BlackjackOptionsView {
  hands: string;
  maxBet: string;
  startingStack: number;
  minBet: number;
}

export interface BlackjackState {
  started: true;
  id: string;
  gameType: string;
  /** Increases with every change of the game; a state older than the one held is stale. */
  version: number;
  phase: BlackjackPhase;
  handNumber: number;
  handsTotal: number;
  solo: boolean;
  /** The seat that plays first this hand. */
  button: number;
  currentSeat: number;
  activeHandIndex: number;
  options: BlackjackOptionsView;
  dealer: BlackjackDealer;
  players: BlackjackSeat[];
  lastHand: BlackjackLastHand | null;
  outcome: BlackjackOutcome;
  /** Private to the viewer. */
  availableActions: BlackjackAction[];
  /** Private to the viewer: present during BETTING. */
  betLimits: BlackjackBetLimits | null;
  /** Private to the viewer: the previous bet, for the one-tap "Bet N". */
  lastBet: number | null;
}

export type BlackjackView = LobbyView | BlackjackState;

export type BlackjackEventType =
  | 'START' | 'BET' | 'DEAL' | 'DEALER_BLACKJACK' | 'HIT' | 'STAND' | 'DOUBLE' | 'SPLIT' | 'BUST'
  | 'DEALER_PLAY' | 'SETTLE' | 'ELIMINATED' | 'GAME_OVER';

/** What a command did, public to every seat. Only the fields of the event type are set. */
export interface BlackjackEventData {
  seat: number | null;
  handIndex: number | null;
  card: Card | null;
  /** The hand total after a card (HIT) or the dealer's final total (DEALER_PLAY). */
  value: number | null;
  /** The cards of a SPLIT, the dealer's drawn cards of a DEALER_PLAY. */
  cards: Card[] | null;
  /** The hole card of a DEALER_BLACKJACK or a DEALER_PLAY. */
  holeCard: Card | null;
  handNumber: number | null;
  /** The first seat of a DEAL. */
  button: number | null;
  /** The bets of a DEAL, by seat. */
  bets: Record<string, number> | null;
  /** The net gains and stacks of a SETTLE, by seat. */
  nets: Record<string, number> | null;
  stacks: Record<string, number> | null;
  bestSeats: number[] | null;
}

export interface BlackjackResponse {
  success: boolean;
  eventType: string;
  eventData: unknown;
  message: string;
  state: BlackjackView | null;
  /** Set on a refusal (see `BlackjackErrorCode`). */
  errorCode?: string;
}
