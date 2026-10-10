import { describe, it, expect } from 'vitest';
import { seatDisplayName } from './displayName';
import type { BullshitPlayer } from './BullshitState';

const labels = { player: 'Player {n}', bot: 'Bot {n}' };
const p = (id: number, extra: Partial<BullshitPlayer> = {}): BullshitPlayer =>
  ({ id: String(id), handCount: 5, isCurrentPlayer: false, ...extra });

describe('seatDisplayName', () => {
  it('uses the typed name, trimmed', () => {
    expect(seatDisplayName([p(0, { name: ' Alice ' })], 0, labels)).toBe('Alice');
  });

  it('falls back to Player N for a missing, null or blank name', () => {
    const players = [p(0), p(1, { name: null }), p(2, { name: '   ' })];
    expect(seatDisplayName(players, 0, labels)).toBe('Player 1');
    expect(seatDisplayName(players, 1, labels)).toBe('Player 2');
    expect(seatDisplayName(players, 2, labels)).toBe('Player 3');
  });

  it('labels bots by their position among the bots, ignoring any name', () => {
    const players = [p(0, { name: 'Alice' }), p(1, { bot: true, name: 'x' }), p(2, { name: 'Bob' }), p(3, { bot: true })];
    expect(seatDisplayName(players, 1, labels)).toBe('Bot 1');
    expect(seatDisplayName(players, 3, labels)).toBe('Bot 2');
  });

  it('falls back to Player N for an unknown seat', () => {
    expect(seatDisplayName([p(0)], 4, labels)).toBe('Player 5');
  });
});
