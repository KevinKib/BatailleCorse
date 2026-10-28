import type Card from '../Card';
import type {
  BlackjackDealer,
  BlackjackHand,
  BlackjackLastHand,
  BlackjackSeat,
  BlackjackState,
} from './BlackjackState';

type Rank = 'TWO' | 'THREE' | 'FOUR' | 'FIVE' | 'SIX' | 'SEVEN' | 'EIGHT' | 'NINE' | 'TEN' | 'JACK' | 'QUEEN' | 'KING' | 'ACE';
type Suit = 'HEART' | 'DIAMOND' | 'CLUB' | 'SPADE';

/** A card in the wire format of the other card games (`rank: 'ACE', suit: 'SPADE', name: 'SPADE_ACE'`). */
export function card(rank: Rank, suit: Suit = 'SPADE'): Card {
  return { rank, suit, name: `${suit}_${rank}` };
}

function hand(cards: Card[], total: number, overrides: Partial<BlackjackHand> = {}): BlackjackHand {
  return { cards, total, soft: false, status: 'ACTIVE', doubled: false, fromSplit: false, ...overrides };
}

function seat(n: number, name: string | null, overrides: Partial<BlackjackSeat> = {}): BlackjackSeat {
  return {
    seat: n,
    name,
    stack: 100,
    betPlaced: false,
    bet: null,
    hands: [],
    eliminated: false,
    forfeited: false,
    place: null,
    standing: 1,
    ...overrides,
  };
}

const noDealer: BlackjackDealer = {
  upCard: null,
  cards: [],
  holeHidden: false,
  total: 0,
  soft: false,
  blackjack: false,
  bust: false,
};

/** A Blackjack state for tests; the defaults describe a 4-seat table at hand 1, Alice (seat 0, the viewer) to bet. */
export function blackjackState(overrides: Partial<BlackjackState> = {}): BlackjackState {
  return {
    started: true,
    id: 'g1',
    gameType: 'blackjack',
    version: 0,
    phase: 'BETTING',
    handNumber: 1,
    handsTotal: 10,
    solo: false,
    button: 0,
    currentSeat: 0,
    activeHandIndex: 0,
    options: { hands: '10', maxBet: '50', startingStack: 100, minBet: 1 },
    dealer: noDealer,
    players: [
      seat(0, 'Alice'),
      seat(1, 'Bob'),
      seat(2, null, { bot: true, level: 'medium' }),
      seat(3, null, { bot: true, level: 'hard' }),
    ],
    lastHand: null,
    outcome: { status: 'ONGOING' },
    availableActions: ['BET'],
    betLimits: { min: 1, max: 50 },
    lastBet: null,
    ...overrides,
  };
}

/** The settlement of a hand the table has just played: Bob blackjack (+15), Alice loses 10, the dealer made 20. */
export const lastHandSample: BlackjackLastHand = {
  handNumber: 2,
  dealer: { cards: [card('KING'), card('QUEEN', 'HEART')], total: 20, bust: false, blackjack: false },
  seats: [
    { seat: 0, hands: [{ cards: [card('TEN', 'CLUB'), card('SEVEN', 'CLUB')], total: 17, status: 'STOOD', net: -10 }], net: -10 },
    { seat: 1, hands: [{ cards: [card('ACE', 'HEART'), card('KING', 'HEART')], total: 21, status: 'BLACKJACK', net: 15 }], net: 15 },
    { seat: 2, hands: [{ cards: [card('TEN', 'DIAMOND'), card('QUEEN', 'DIAMOND')], total: 20, status: 'STOOD', net: 0 }], net: 0 },
    { seat: 3, hands: [{ cards: [card('NINE', 'SPADE'), card('SIX', 'HEART'), card('KING', 'CLUB')], total: 25, status: 'BUST', net: -20 }], net: -20 },
  ],
  bestSeats: [1],
};

/** A 4-seat betting phase, hand 3: Bob and the first bot have bet (sealed), Alice (the viewer) has not. */
export const blackjackBettingState: BlackjackState = blackjackState({
  id: 'betting',
  version: 7,
  handNumber: 3,
  button: 2,
  currentSeat: 0,
  players: [
    seat(0, 'Alice', { stack: 90, standing: 2 }),
    seat(1, 'Bob', { stack: 115, betPlaced: true, standing: 1 }),
    seat(2, null, { bot: true, level: 'medium', stack: 100, betPlaced: true, standing: 3 }),
    seat(3, null, { bot: true, level: 'hard', stack: 80, standing: 4 }),
  ],
  lastHand: lastHandSample,
  betLimits: { min: 1, max: 50 },
  lastBet: 10,
});

/** The playing phase: Alice (the viewer, on turn) split a pair of eights and plays her first hand; Bob has 18. */
export const blackjackPlayingState: BlackjackState = blackjackState({
  id: 'playing',
  version: 21,
  phase: 'PLAYING',
  handNumber: 4,
  button: 3,
  currentSeat: 0,
  activeHandIndex: 0,
  dealer: {
    upCard: card('SIX', 'DIAMOND'),
    cards: [card('SIX', 'DIAMOND')],
    holeHidden: true,
    total: 6,
    soft: false,
    blackjack: false,
    bust: false,
  },
  players: [
    seat(0, 'Alice', {
      stack: 70,
      betPlaced: true,
      bet: 20,
      standing: 2,
      hands: [
        hand([card('EIGHT', 'CLUB'), card('THREE', 'HEART')], 11, { fromSplit: true }),
        hand([card('EIGHT', 'DIAMOND')], 8, { fromSplit: true, status: 'WAITING' }),
      ],
    }),
    seat(1, 'Bob', {
      stack: 105,
      betPlaced: true,
      bet: 10,
      standing: 1,
      hands: [hand([card('TEN', 'HEART'), card('EIGHT', 'SPADE')], 18)],
    }),
    seat(2, null, {
      bot: true,
      level: 'medium',
      stack: 95,
      betPlaced: true,
      bet: 10,
      standing: 3,
      hands: [hand([card('ACE', 'CLUB'), card('SIX', 'SPADE')], 17, { soft: true })],
    }),
    seat(3, null, {
      bot: true,
      level: 'hard',
      stack: 90,
      betPlaced: true,
      bet: 10,
      standing: 4,
      hands: [hand([card('KING', 'SPADE'), card('QUEEN', 'CLUB')], 20)],
    }),
  ],
  availableActions: ['HIT', 'STAND', 'DOUBLE'],
  betLimits: null,
  lastBet: 20,
});

/** The next betting phase right after a dealer blackjack: the hole card is shown, nobody played, one bet pushed. */
export const blackjackDealerBlackjackState: BlackjackState = blackjackState({
  id: 'dealer-blackjack',
  version: 30,
  handNumber: 5,
  button: 0,
  players: [
    seat(0, 'Alice', { stack: 80, standing: 3 }),
    seat(1, 'Bob', { stack: 110, standing: 1 }),
    seat(2, null, { bot: true, level: 'medium', stack: 90, standing: 4 }),
    seat(3, null, { bot: true, level: 'hard', stack: 100, standing: 2 }),
  ],
  lastHand: {
    handNumber: 4,
    dealer: { cards: [card('ACE', 'DIAMOND'), card('KING', 'DIAMOND')], total: 21, bust: false, blackjack: true },
    seats: [
      { seat: 0, hands: [{ cards: [card('NINE', 'CLUB'), card('NINE', 'HEART')], total: 18, status: 'STOOD', net: -10 }], net: -10 },
      { seat: 1, hands: [{ cards: [card('ACE', 'SPADE'), card('QUEEN', 'SPADE')], total: 21, status: 'BLACKJACK', net: 0 }], net: 0 },
      { seat: 2, hands: [{ cards: [card('TEN', 'CLUB'), card('SIX', 'CLUB')], total: 16, status: 'STOOD', net: -10 }], net: -10 },
      { seat: 3, hands: [{ cards: [card('EIGHT', 'HEART'), card('FOUR', 'SPADE')], total: 12, status: 'STOOD', net: -10 }], net: -10 },
    ],
    bestSeats: [],
  },
  lastBet: 10,
});

/** A settled hand with its best hand (Bob, +15), the next betting open: the summary reads `lastHand`. */
export const blackjackSettledState: BlackjackState = blackjackState({
  id: 'settled',
  version: 25,
  handNumber: 3,
  button: 2,
  players: [
    seat(0, 'Alice', { stack: 90, standing: 3 }),
    seat(1, 'Bob', { stack: 115, standing: 1 }),
    seat(2, null, { bot: true, level: 'medium', stack: 100, standing: 2 }),
    seat(3, null, { bot: true, level: 'hard', stack: 80, standing: 4 }),
  ],
  lastHand: lastHandSample,
  lastBet: 10,
});

/** A tournament end after the last hand: Alice and Bob share first place (140 each), Cleo is third. */
export const blackjackTournamentEndState: BlackjackState = blackjackState({
  id: 'tournament-end',
  version: 90,
  phase: 'FINISHED',
  handNumber: 10,
  currentSeat: -1,
  players: [
    seat(0, 'Alice', { stack: 140, place: 1, standing: 1 }),
    seat(1, 'Bob', { stack: 140, place: 1, standing: 1 }),
    seat(2, 'Cleo', { stack: 60, place: 3, standing: 3 }),
    seat(3, null, { bot: true, level: 'medium', stack: 0, eliminated: true, place: 4, standing: 4 }),
  ],
  lastHand: lastHandSample,
  outcome: {
    status: 'FINISHED',
    places: { 0: 1, 1: 1, 2: 3, 3: 4 },
    stacks: { 0: 140, 1: 140, 2: 60, 3: 0 },
    reason: 'HANDS_PLAYED',
  },
  availableActions: [],
  betLimits: null,
  lastBet: 10,
});

/** A solo end after the last hand: the player finishes with 123 points (+23). */
export const blackjackSoloEndState: BlackjackState = blackjackState({
  id: 'solo-end',
  version: 60,
  phase: 'FINISHED',
  solo: true,
  handNumber: 10,
  currentSeat: -1,
  players: [seat(0, 'Alice', { stack: 123, standing: 1 })],
  lastHand: {
    handNumber: 10,
    dealer: { cards: [card('TEN', 'HEART'), card('SEVEN', 'HEART')], total: 17, bust: false, blackjack: false },
    seats: [
      { seat: 0, hands: [{ cards: [card('TEN', 'CLUB'), card('NINE', 'CLUB')], total: 19, status: 'STOOD', net: 10 }], net: 10 },
    ],
    bestSeats: [0],
  },
  outcome: {
    status: 'FINISHED',
    places: {},
    stacks: { 0: 123 },
    reason: 'HANDS_PLAYED',
    net: 23,
  },
  availableActions: [],
  betLimits: null,
  lastBet: 10,
});
