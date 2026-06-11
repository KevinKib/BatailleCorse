import { describe, it, expect } from 'vitest';
import Player from './Player';

describe('Player', () => {
  describe('hasAvailableAction', () => {
    it('returns true when the action is in the list', () => {
      const player = new Player('0', 26, ['SEND', 'SLAP']);
      expect(player.hasAvailableAction('SEND')).toBe(true);
      expect(player.hasAvailableAction('SLAP')).toBe(true);
    });

    it('returns false when the action is not in the list', () => {
      const player = new Player('0', 26, []);
      expect(player.hasAvailableAction('SEND')).toBe(false);
    });

    it('is case-sensitive', () => {
      const player = new Player('0', 26, ['SEND']);
      expect(player.hasAvailableAction('send')).toBe(false);
    });
  });

  describe('fromJSON', () => {
    it('constructs a Player instance', () => {
      const data = { id: '1', nbCards: 10, availableActions: ['SEND'] };
      const player = Player.fromJSON(data);
      expect(player).toBeInstanceOf(Player);
      expect(player.id).toBe('1');
      expect(player.nbCards).toBe(10);
      expect(player.hasAvailableAction('SEND')).toBe(true);
    });
  });
});

describe('Player.fromJSON forfeitReason', () => {
  it('defaults forfeitReason to null when absent', () => {
    const player = Player.fromJSON({ id: '0', nbCards: 26, availableActions: [] });
    expect(player.forfeitReason).toBeNull();
  });

  it('reads forfeitReason when present', () => {
    const player = Player.fromJSON({
      id: '1', nbCards: 0, availableActions: [], forfeitReason: 'DISCONNECTED',
    });
    expect(player.forfeitReason).toBe('DISCONNECTED');
  });
});
