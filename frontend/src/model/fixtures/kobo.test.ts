import { describe, it, expect } from 'vitest';
import { buildKoboState, buildKoboPlayer, buildKoboCard } from './kobo';

// The field lists of the backend records (KoboDto.java), so a drifting fixture fails here.
const KOBO_DTO_FIELDS = [
  'started', 'id', 'gameType', 'version', 'round', 'phase', 'currentSeat', 'readySeats', 'announcer',
  'finalTurnsRemaining', 'pendingPower', 'drawPileSize', 'discardSize', 'discardTop', 'revealed', 'players',
  'hasDrawn', 'drawnCard', 'startingCards', 'reveal', 'availableActions', 'lastRound', 'outcome',
];
const PLAYER_DTO_FIELDS = ['seat', 'name', 'current', 'total', 'handSize', 'slots'];
const SLOT_DTO_FIELDS = ['slot', 'occupied', 'revision', 'card'];

describe('Kobo fixtures', () => {
  it('buildKoboState has exactly the fields of KoboDto', () => {
    expect(Object.keys(buildKoboState()).sort()).toEqual([...KOBO_DTO_FIELDS].sort());
  });

  it('buildKoboPlayer has exactly the fields of PlayerDto and SlotDto', () => {
    const player = buildKoboPlayer(1, { handSize: 2 });
    expect(Object.keys(player).sort()).toEqual([...PLAYER_DTO_FIELDS].sort());
    expect(player.slots).toHaveLength(2);
    expect(Object.keys(player.slots[0]).sort()).toEqual([...SLOT_DTO_FIELDS].sort());
  });

  it('cards are shaped like CardDto (upper-case labels, SUIT_RANK name)', () => {
    expect(buildKoboCard('KING', 'SPADE')).toEqual({ rank: 'KING', suit: 'SPADE', name: 'SPADE_KING' });
  });
});
