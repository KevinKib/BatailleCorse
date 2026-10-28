import type Card from '../Card';
import type {
  KiloDeMerdePlayer,
  KiloDeMerdeRoundOutcome,
  KiloDeMerdeSeatHand,
  KiloDeMerdeState,
} from './KiloDeMerdeState';

/** The options of a fresh room as the server fills them in (`KiloDeMerdeOptions.defaults()`). */
export const DEFAULT_KILO_DE_MERDE_OPTIONS: Record<string, string> = { rounds: '10' };

/** A card shaped like the backend `CardDto`: upper-case labels (`2`..`10`, `JACK`, `ACE`...) and a `SUIT_RANK` name. */
export function kiloCard(rank: string, suit = 'CLUB'): Card {
  return { rank, suit, name: `${suit}_${rank}` };
}

function player(seat: number, overrides: Partial<KiloDeMerdePlayer> = {}): KiloDeMerdePlayer {
  return {
    seat,
    name: `Player ${seat}`,
    kilos: 0,
    kiloCards: [],
    chosen: false,
    slapRank: null,
    active: true,
    place: null,
    ...overrides,
  };
}

function seats(count: number, overrides: (seat: number) => Partial<KiloDeMerdePlayer> = () => ({})) {
  return Array.from({ length: count }, (_, seat) => player(seat, overrides(seat)));
}

function base(overrides: Partial<KiloDeMerdeState>): KiloDeMerdeState {
  return {
    started: true,
    id: 'game-kilo',
    gameType: 'kilo-de-merde',
    version: 1,
    round: 1,
    roundsTotal: 10,
    step: 1,
    phase: 'PASSING',
    pileSize: 40,
    options: { ...DEFAULT_KILO_DE_MERDE_OPTIONS },
    passTimerEndsAt: null,
    raceEndsAt: null,
    players: [],
    race: null,
    lastOutcome: null,
    result: null,
    hand: [],
    myChoice: null,
    lastReceived: null,
    lastGiven: null,
    availableActions: [],
    ...overrides,
  };
}

/** Three seats, round 2 of 10, pass 3: seat 1 is ready, my choice (seat 0) is pending. */
export const kiloDeMerdeStepState: KiloDeMerdeState = base({
  version: 14,
  round: 2,
  step: 3,
  pileSize: 38,
  passTimerEndsAt: 1_900_000_015_000,
  players: seats(3, (seat) => ({
    chosen: seat === 1,
    kilos: seat === 2 ? 13 : 0,
    kiloCards: seat === 2 ? [kiloCard('KING', 'SPADE')] : [],
  })),
  hand: [
    kiloCard('JACK', 'CLUB'),
    kiloCard('JACK', 'HEART'),
    kiloCard('QUEEN', 'DIAMOND'),
    kiloCard('KING', 'SPADE'),
  ],
  lastReceived: { card: kiloCard('JACK', 'HEART'), from: 2 },
  lastGiven: { card: kiloCard('QUEEN', 'SPADE'), to: 1 },
  availableActions: ['PASS', 'SLAP'],
});

/** Ten seats, round 4 of 10: seat 3 slapped first, then 7 and 5; I (seat 0) have not slapped yet. */
export const kiloDeMerdeRaceState: KiloDeMerdeState = base({
  version: 52,
  round: 4,
  step: 6,
  phase: 'RACE',
  pileSize: 12,
  raceEndsAt: 1_900_000_005_000,
  players: seats(10, (seat) => ({
    slapRank: seat === 3 ? 1 : seat === 7 ? 2 : seat === 5 ? 3 : null,
    kilos: seat === 4 ? 9 : 0,
    kiloCards: seat === 4 ? [kiloCard('9', 'CLUB')] : [],
  })),
  race: { firstSlapper: 3, order: [3, 7, 5] },
  hand: [kiloCard('9', 'SPADE'), kiloCard('8', 'HEART'), kiloCard('3', 'CLUB'), kiloCard('KING', 'DIAMOND')],
  availableActions: ['SLAP'],
});

const fourHands: KiloDeMerdeSeatHand[] = [
  { seat: 0, hand: [kiloCard('JACK', 'CLUB'), kiloCard('JACK', 'HEART'), kiloCard('JACK', 'DIAMOND'), kiloCard('QUEEN', 'SPADE')] },
  { seat: 1, hand: [kiloCard('KING', 'CLUB'), kiloCard('KING', 'HEART'), kiloCard('KING', 'DIAMOND'), kiloCard('KING', 'SPADE')] },
  { seat: 2, hand: [kiloCard('QUEEN', 'CLUB'), kiloCard('QUEEN', 'HEART'), kiloCard('QUEEN', 'DIAMOND'), kiloCard('JACK', 'SPADE')] },
  { seat: 3, hand: [kiloCard('2', 'CLUB'), kiloCard('2', 'HEART'), kiloCard('2', 'DIAMOND'), kiloCard('2', 'SPADE')] },
];

const roundOverOutcome: KiloDeMerdeRoundOutcome = {
  kind: 'RACE',
  firstSlapper: 1,
  slapOrder: [1, 3, 0],
  holders: [1, 3],
  draws: [{ seat: 2, card: kiloCard('7', 'HEART'), flushed: true, kilosAfter: 0 }],
  hands: fourHands,
};

/** Four seats, round 5 over: seat 2 was last and drew the 7, the flush (its 25 kg went back into the pile). */
export const kiloDeMerdeRoundOverState: KiloDeMerdeState = base({
  version: 80,
  round: 5,
  step: 9,
  phase: 'ROUND_OVER',
  pileSize: 26,
  players: seats(4, (seat) => ({ slapRank: seat === 1 ? 1 : seat === 3 ? 2 : seat === 0 ? 3 : null })),
  lastOutcome: roundOverOutcome,
  availableActions: [],
});

/** Four seats, game over after 5 rounds: seats 0 and 2 share 1st place (0 kg), seat 1 is 3rd, seat 3 is 4th. */
export const kiloDeMerdeFinishedState: KiloDeMerdeState = base({
  version: 120,
  round: 5,
  roundsTotal: 5,
  step: 1,
  phase: 'FINISHED',
  pileSize: 22,
  options: { rounds: '5' },
  players: seats(4, (seat) => ({
    kilos: [0, 24, 0, 113][seat],
    place: [1, 3, 1, 4][seat],
    kiloCards:
      seat === 1
        ? [kiloCard('QUEEN', 'CLUB'), kiloCard('QUEEN', 'HEART')]
        : seat === 3
          ? [kiloCard('ACE', 'HEART'), kiloCard('KING', 'SPADE')]
          : [],
  })),
  lastOutcome: roundOverOutcome,
  result: {
    reason: 'ROUNDS_PLAYED',
    places: [
      { seat: 0, place: 1, kilos: 0 },
      { seat: 2, place: 1, kilos: 0 },
      { seat: 1, place: 3, kilos: 24 },
      { seat: 3, place: 4, kilos: 113 },
    ],
  },
  availableActions: [],
});
