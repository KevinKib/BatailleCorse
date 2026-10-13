import type Card from '../Card';
import type { KoboPlayer, KoboSlot, KoboState } from '../kobo/KoboState';

/** A card shaped like the backend `CardDto`: upper-case labels and a `SUIT_RANK` name. */
export function buildKoboCard(rank = 'ACE', suit = 'HEART'): Card {
  return { rank, suit, name: `${suit}_${rank}` };
}

/** A slot holding a card the viewer may not see: occupied, `card` null. */
export function buildKoboSlot(slot: number, overrides: Partial<KoboSlot> = {}): KoboSlot {
  return { slot, occupied: true, revision: 0, card: null, ...overrides };
}

/** A player with `handSize` occupied face-down slots (4 by default). */
export function buildKoboPlayer(seat: number, overrides: Partial<KoboPlayer> = {}): KoboPlayer {
  const handSize = overrides.handSize ?? 4;
  return {
    seat,
    name: `Player ${seat}`,
    current: false,
    total: 0,
    handSize,
    slots: Array.from({ length: handSize }, (_, i) => buildKoboSlot(i)),
    ...overrides,
  };
}

/** A running two-player game at the draw step of seat 0, with nothing special to do for the viewer. */
export function buildKoboState(overrides: Partial<KoboState> = {}): KoboState {
  return {
    started: true,
    id: 'g1',
    gameType: 'kobo',
    version: 1,
    round: 1,
    phase: 'DRAW',
    currentSeat: 0,
    readySeats: [],
    announcer: null,
    finalTurnsRemaining: [],
    pendingPower: 'NONE',
    drawPileSize: 40,
    discardSize: 1,
    discardTop: buildKoboCard('7', 'SPADE'),
    revealed: false,
    players: [buildKoboPlayer(0, { current: true }), buildKoboPlayer(1)],
    hasDrawn: false,
    drawnCard: null,
    startingCards: [],
    reveal: null,
    availableActions: [],
    lastRound: null,
    outcome: { status: 'ONGOING' },
    ...overrides,
  };
}
