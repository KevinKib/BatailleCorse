import { describe, it, expect } from 'vitest';
import { endGameMessage } from './endGameMessage';
import { messagesEn } from '../locales/en';

const texts = messagesEn.endGame;

describe('endGameMessage', () => {
  it('normal card win', () => {
    expect(endGameMessage(true, 'Alice', null, texts)).toBe('You beat Alice!');
  });

  it('win by opponent resignation', () => {
    expect(endGameMessage(true, 'Alice', 'RESIGNED', texts)).toBe('Alice resigned.');
  });

  it('win by opponent disconnection', () => {
    expect(endGameMessage(true, 'Alice', 'DISCONNECTED', texts)).toBe('Alice disconnected.');
  });

  it('defeat is always plain regardless of reason', () => {
    expect(endGameMessage(false, 'Alice', null, texts)).toBe('Alice won.');
    expect(endGameMessage(false, 'Alice', 'RESIGNED', texts)).toBe('Alice won.');
    expect(endGameMessage(false, 'Alice', 'DISCONNECTED', texts)).toBe('Alice won.');
  });

  it('givenOtherTexts_thenTheSentencesComeFromThem', () => {
    const other = { won: 'W {opponent}', resigned: 'R {opponent}', disconnected: 'D {opponent}', beat: 'B {opponent}' };
    expect(endGameMessage(true, 'Al', null, other)).toBe('B Al');
    expect(endGameMessage(true, 'Al', 'RESIGNED', other)).toBe('R Al');
    expect(endGameMessage(false, 'Al', null, other)).toBe('W Al');
  });
});
