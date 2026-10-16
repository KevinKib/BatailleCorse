import type Card from '../Card';
import type { PresidentPlayer, PresidentState, PresidentTrick } from '../president/PresidentState';

/** The options of a fresh room as the server fills them in (`PresidentOptions.defaults()`). */
export const CLASSIC_PRESIDENT_OPTIONS: Record<string, string> = {
  afterPass: 'reenter',
  firstLead: 'kingOfHearts',
  exchange: 'two',
  giveBack: 'free',
  nextLead: 'scum',
  scoring: 'rank',
  length: 'rounds-5',
  equalPlay: 'on',
};

/** A card shaped like the backend `CardDto`: upper-case labels (`3`..`10`, `JACK`, `ACE`...) and a `SUIT_RANK` name. */
export function buildPresidentCard(rank = '3', suit = 'CLUB'): Card {
  return { rank, suit, name: `${suit}_${rank}` };
}

/** A hand from `[rank, suit]` pairs. */
export function buildPresidentHand(...cards: [string, string][]): Card[] {
  return cards.map(([rank, suit]) => buildPresidentCard(rank, suit));
}

/** A seat that still holds `handSize` cards (8 by default), has not passed and has no title. */
export function buildPresidentPlayer(seat: number, overrides: Partial<PresidentPlayer> = {}): PresidentPlayer {
  return {
    seat,
    name: `Player ${seat}`,
    handSize: 8,
    title: null,
    place: null,
    out: false,
    passed: false,
    points: 0,
    ready: false,
    ...overrides,
  };
}

/** A trick nobody has played in yet. */
export function buildPresidentTrick(overrides: Partial<PresidentTrick> = {}): PresidentTrick {
  return { leader: null, plays: [], top: null, lastPlayer: null, passed: [], constraint: 'FREE', requiredRank: null, requiredCount: null, chain: null, ...overrides };
}

/** A running three-player game, round 1, seat 0 to lead; the viewer's hand and legal plays are the caller's. */
export function buildPresidentState(overrides: Partial<PresidentState> = {}): PresidentState {
  return {
    started: true,
    id: 'g1',
    gameType: 'president',
    version: 1,
    round: 1,
    rounds: 5,
    phase: 'PLAYING',
    currentSeat: 0,
    options: { ...CLASSIC_PRESIDENT_OPTIONS },
    players: [buildPresidentPlayer(0), buildPresidentPlayer(1), buildPresidentPlayer(2)],
    trick: buildPresidentTrick(),
    exchange: [],
    lastRound: null,
    standings: [{ seat: 0, points: 0 }, { seat: 1, points: 0 }, { seat: 2, points: 0 }],
    outcome: { status: 'ONGOING' },
    hand: [],
    received: [],
    given: [],
    toGiveBack: 0,
    legalPlays: [],
    availableActions: [],
    ...overrides,
  };
}
