import type Card from '../Card';
import type { KoboState } from './KoboState';
import { slotKey } from './interaction';

/**
 * The cards the viewer may see, by `slotKey`. Pure memory: a card appears only when the server hands it over
 * (a revealed round, the peek of the moment, my starting cards until I say I have memorised them); nothing
 * is remembered here. Every other slot is drawn face down.
 */
export function visibleFaces(game: KoboState, mySeat: number): Record<string, Card> {
  const faces: Record<string, Card> = {};

  for (const player of game.players) {
    for (const slot of player.slots) {
      if (slot.occupied && slot.card) faces[slotKey(player.seat, slot.slot)] = slot.card;
    }
  }

  // The state keeps carrying the starting cards after I am ready (until the first draw): they go face down then.
  if (game.phase === 'MEMORISING' && !game.readySeats.includes(mySeat)) {
    for (const { slot, card } of game.startingCards) faces[slotKey(mySeat, slot)] = card;
  }

  if (game.reveal) {
    const { seat, slot, revision, card } = game.reveal;
    const target = game.players.find(p => p.seat === seat)?.slots.find(s => s.slot === slot);
    // A card that changed since the peek is not the one that was seen.
    if (target?.occupied && target.revision === revision) faces[slotKey(seat, slot)] = card;
  }

  return faces;
}

/** Columns of the opponents' grid on a narrow screen: 1 to 3 side by side, then 2 + 2 and 3 + 2. */
export function opponentColumns(count: number): number {
  if (count <= 1) return 1;
  if (count === 4) return 2;
  return Math.min(count, 3);
}
