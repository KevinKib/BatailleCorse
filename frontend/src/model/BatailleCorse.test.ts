import { describe, it, expect } from 'vitest';
import BatailleCorse from './BatailleCorse';
import Pile from './Pile';
import Player from './Player';

describe('BatailleCorse.fromJSON', () => {
  it('constructs Pile and Player instances with their behaviour methods', () => {
    const data = {
      currentPlayer: { id: '0', nbCards: 26, availableActions: ['SEND'] },
      pile: {
        cards: [],
        grabbable: true,
        nbCardsSinceLastHonourCard: 0,
        playerThatAddedLastHonourCard: { id: '0' },
      },
      players: [
        { id: '0', nbCards: 26, availableActions: ['SEND'] },
        { id: '1', nbCards: 26, availableActions: [] },
      ],
      winner: null,
    };

    const game = BatailleCorse.fromJSON(data);

    expect(game).toBeInstanceOf(BatailleCorse);
    expect(game.pile).toBeInstanceOf(Pile);
    expect(game.players[0]).toBeInstanceOf(Player);
    expect(game.players[1]).toBeInstanceOf(Player);
    expect(game.pile.getAutoGrabPlayer()).toBe(0);
    expect(game.players[0].hasAvailableAction('SEND')).toBe(true);
    expect(game.players[1].hasAvailableAction('SEND')).toBe(false);
    expect(game.winner).toBeNull();
  });
});
