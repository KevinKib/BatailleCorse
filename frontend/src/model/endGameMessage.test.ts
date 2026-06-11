import { describe, it, expect } from 'vitest';
import { endGameMessage } from './endGameMessage';

describe('endGameMessage', () => {
  it('normal card win', () => {
    expect(endGameMessage(true, 'Alice', null)).toBe('You beat Alice!');
  });

  it('win by opponent resignation', () => {
    expect(endGameMessage(true, 'Alice', 'RESIGNED')).toBe('Alice resigned.');
  });

  it('win by opponent disconnection', () => {
    expect(endGameMessage(true, 'Alice', 'DISCONNECTED')).toBe('Alice disconnected.');
  });

  it('defeat is always plain regardless of reason', () => {
    expect(endGameMessage(false, 'Alice', null)).toBe('Alice won.');
    expect(endGameMessage(false, 'Alice', 'RESIGNED')).toBe('Alice won.');
    expect(endGameMessage(false, 'Alice', 'DISCONNECTED')).toBe('Alice won.');
  });
});
