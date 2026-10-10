import { describe, it, expect } from 'vitest';
import { extractGameId } from './gameId';

describe('extractGameId', () => {
  it('returns a bare id unchanged, trimmed', () => {
    expect(extractGameId('  abc-123 ')).toBe('abc-123');
  });

  it('extracts the id from a pasted invite link', () => {
    expect(extractGameId('http://localhost:5173/games/bullshit/join/abc-123')).toBe('abc-123');
    expect(extractGameId('https://example.com/games/bullshit/join/abc-123/?x=1#y')).toBe('abc-123');
  });

  it('returns an empty string for blank input', () => {
    expect(extractGameId('   ')).toBe('');
  });
});
