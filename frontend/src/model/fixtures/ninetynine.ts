import type Card from '../Card';
import type { NinetyNinePlayable, NinetyNinePlayer, NinetyNineState } from '../ninetynine/NinetyNineState';

/**
 * The options of a fresh room as the server fills them in (`NinetyNineOptions.defaults()`). `freePlay` is a hidden
 * option (no lobby group): on, the server reports every card as playable and both Ace values as allowed.
 */
export const DEFAULT_NINETY_NINE_OPTIONS: Record<string, string> = { lives: '3', quick: 'off', freePlay: 'on' };

/** A card shaped like the backend `CardDto`: upper-case labels (`2`..`10`, `JACK`, `ACE`...) and a `SUIT_RANK` name. */
export function buildNinetyNineCard(rank: string, suit = 'CLUB'): Card {
  return { rank, suit, name: `${suit}_${rank}` };
}

function player(seat: number, overrides: Partial<NinetyNinePlayer> = {}): NinetyNinePlayer {
  return { seat, name: `Player ${seat}`, lives: 3, handSize: 3, eliminated: false, place: null, ...overrides };
}

function safe(card: Card, aceValues: number[] = []): NinetyNinePlayable {
  return { card, safe: true, aceValues };
}

function base(overrides: Partial<NinetyNineState>): NinetyNineState {
  return {
    started: true,
    id: 'game-99',
    gameType: 'ninety-nine',
    version: 1,
    round: 1,
    phase: 'PLAYING',
    total: 0,
    direction: 'CLOCKWISE',
    currentSeat: 0,
    pileTop: null,
    pileCount: 0,
    stockSize: 52,
    options: { ...DEFAULT_NINETY_NINE_OPTIONS },
    players: [],
    lastPlay: null,
    lastBust: null,
    outcome: { status: 'ONGOING' },
    stuck: false,
    hand: [],
    playable: [],
    availableActions: [],
    ...overrides,
  };
}

/** Four seats, total 42, the viewer (seat 0) to play a 7, an Ace and a King. */
export function buildNinetyNineFourSeatRound(): NinetyNineState {
  const seven = buildNinetyNineCard('7', 'HEART');
  const ace = buildNinetyNineCard('ACE', 'SPADE');
  const king = buildNinetyNineCard('KING', 'CLUB');
  const jack = buildNinetyNineCard('JACK', 'DIAMOND');
  return base({
    version: 6,
    total: 42,
    currentSeat: 0,
    pileTop: jack,
    pileCount: 3,
    stockSize: 52 - 12 - 3,
    players: [0, 1, 2, 3].map((seat) => player(seat)),
    lastPlay: { seat: 3, card: jack, aceValue: null, totalBefore: 32, totalAfter: 42, reversed: false },
    hand: [seven, ace, king],
    playable: [safe(seven), safe(ace, [1, 11]), safe(king)],
    availableActions: ['PLAY'],
  });
}

/**
 * Twelve seats at total 94, direction reversed; the viewer (seat 0) holds a 9 and two cards that go over. The server
 * has free play off here (a guarded game): it reports the two cards that go over as not playable.
 */
export function buildNinetyNineTwelveSeatRound(): NinetyNineState {
  const nine = buildNinetyNineCard('9', 'HEART');
  const eight = buildNinetyNineCard('8', 'SPADE');
  const queen = buildNinetyNineCard('QUEEN', 'CLUB');
  const four = buildNinetyNineCard('4', 'DIAMOND');
  return base({
    version: 31,
    round: 2,
    total: 94,
    direction: 'COUNTER_CLOCKWISE',
    currentSeat: 0,
    pileTop: four,
    pileCount: 11,
    stockSize: 52 - 36 - 11,
    players: Array.from({ length: 12 }, (_, seat) =>
      player(seat, seat === 5 ? { lives: 1 } : seat === 9 ? { lives: 0, handSize: 0, eliminated: true, place: 12 } : {})),
    lastPlay: { seat: 1, card: four, aceValue: null, totalBefore: 94, totalAfter: 94, reversed: true },
    options: { ...DEFAULT_NINETY_NINE_OPTIONS, freePlay: 'off' },
    hand: [nine, eight, queen],
    playable: [safe(nine), { card: eight, safe: false, aceValues: [] }, { card: queen, safe: false, aceValues: [] }],
    availableActions: ['PLAY'],
  });
}

/**
 * The same position with free play on (the default): total 94, the viewer (seat 0) holds a 9, an 8 and an Ace. The server
 * reports every card as playable and the Ace as 1 or 11, though the 8 and the Ace as 11 go over 99.
 */
export function buildNinetyNineFreePlayRound(): NinetyNineState {
  const nine = buildNinetyNineCard('9', 'HEART');
  const eight = buildNinetyNineCard('8', 'SPADE');
  const ace = buildNinetyNineCard('ACE', 'CLUB');
  return base({
    version: 31,
    round: 2,
    total: 94,
    currentSeat: 0,
    pileTop: buildNinetyNineCard('4', 'DIAMOND'),
    pileCount: 11,
    stockSize: 52 - 36 - 11,
    players: [0, 1, 2, 3].map((seat) => player(seat)),
    hand: [nine, eight, ace],
    playable: [safe(nine), safe(eight), safe(ace, [1, 11])],
    availableActions: ['PLAY'],
  });
}

/** The viewer (seat 0) is stuck at 99: 7, Ace and J all go over, yet one must be played and costs a life. */
export function buildNinetyNineStuck(): NinetyNineState {
  const hand = [buildNinetyNineCard('7', 'HEART'), buildNinetyNineCard('ACE', 'CLUB'), buildNinetyNineCard('JACK', 'SPADE')];
  return base({
    version: 13,
    total: 99,
    currentSeat: 0,
    pileTop: buildNinetyNineCard('2', 'DIAMOND'),
    pileCount: 13,
    stockSize: 23,
    players: [0, 1, 2, 3].map((seat) => player(seat)),
    stuck: true,
    hand,
    playable: [safe(hand[0]), safe(hand[1], [1]), safe(hand[2])],
    availableActions: ['PLAY'],
  });
}

/** Seat 2 was stuck at 96, played its 7 (103) and lost a life; the next round follows after the pause. */
export function buildNinetyNineBust(): NinetyNineState {
  const hand = [buildNinetyNineCard('8', 'CLUB'), buildNinetyNineCard('JACK', 'SPADE')];
  return base({
    version: 14,
    phase: 'ROUND_OVER',
    total: 103,
    currentSeat: 2,
    pileTop: buildNinetyNineCard('7', 'HEART'),
    pileCount: 14,
    stockSize: 22,
    players: [0, 1, 2, 3].map((seat) => player(seat, seat === 2 ? { lives: 2 } : {})),
    lastPlay: { seat: 2, card: buildNinetyNineCard('7', 'HEART'), aceValue: null, totalBefore: 96, totalAfter: 103, reversed: false },
    lastBust: { seat: 2, totalBefore: 103, hand, livesLeft: 2, eliminated: false },
    hand: [buildNinetyNineCard('5', 'HEART'), buildNinetyNineCard('9', 'CLUB'), buildNinetyNineCard('3', 'SPADE')],
    playable: [],
    availableActions: [],
  });
}

/** Quick mode, three seats: seat 2 lost its last life; seats 0 and 1 share first place with 2 lives each. */
export function buildNinetyNineQuickEnd(): NinetyNineState {
  return base({
    version: 40,
    round: 5,
    phase: 'FINISHED',
    total: 97,
    currentSeat: 2,
    options: { lives: '3', quick: 'on' },
    players: [
      player(0, { lives: 2, place: 1 }),
      player(1, { lives: 2, place: 1 }),
      player(2, { lives: 0, handSize: 0, eliminated: true, place: 3 }),
    ],
    lastBust: {
      seat: 2,
      totalBefore: 97,
      hand: [buildNinetyNineCard('8', 'HEART'), buildNinetyNineCard('JACK', 'CLUB'), buildNinetyNineCard('QUEEN', 'SPADE')],
      livesLeft: 0,
      eliminated: true,
    },
    outcome: { status: 'FINISHED', winners: [0, 1], reason: 'QUICK' },
  });
}
