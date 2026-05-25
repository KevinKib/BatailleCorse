import { describe, it, expect } from 'vitest';
import Pile from './Pile';

describe('Pile', () => {
  describe('getAutoGrabPlayer', () => {
    it('returns null when pile is not grabbable', () => {
      const pile = new Pile([], false, 0, { id: '0' });
      expect(pile.getAutoGrabPlayer()).toBeNull();
    });

    it('returns the player index when pile is grabbable', () => {
      const pile = new Pile([], true, 3, { id: '1' });
      expect(pile.getAutoGrabPlayer()).toBe(1);
    });

    it('returns null when playerThatAddedLastHonourCard id is not numeric', () => {
      const pile = new Pile([], true, 0, { id: 'not-a-number' });
      expect(pile.getAutoGrabPlayer()).toBeNull();
    });
  });

  describe('fromJSON', () => {
    it('constructs a Pile instance with all fields accessible', () => {
      const data = {
        cards: [{ rank: 'A', suit: 'spade', name: 'Ace of spades' }],
        grabbable: true,
        nbCardsSinceLastHonourCard: 2,
        playerThatAddedLastHonourCard: { id: '0' },
      };
      const pile = Pile.fromJSON(data);
      expect(pile).toBeInstanceOf(Pile);
      expect(pile.grabbable).toBe(true);
      expect(pile.cards).toHaveLength(1);
      expect(pile.getAutoGrabPlayer()).toBe(0);
    });
  });
});
