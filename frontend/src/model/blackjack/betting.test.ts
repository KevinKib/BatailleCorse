import { describe, it, expect } from 'vitest';
import {
  BET_CHIPS, DEFAULT_BET, addChip, chipsLeft, clampBet, defaultBet, isBetAllowed, maxOf, parseBetInput,
} from './betting';

describe('parseBetInput and chipsLeft', () => {
  it('keeps only the digits of what was typed', () => {
    expect(parseBetInput('12')).toBe(12);
    expect(parseBetInput(' 1a2 ')).toBe(12);
    expect(parseBetInput('abc')).toBe(0);
    expect(parseBetInput('')).toBe(0);
  });

  it('counts the chips left after the bet, and after a double or split', () => {
    expect(chipsLeft(100, 30)).toEqual({ after: 70, afterDouble: 40 });
    expect(chipsLeft(100, 60)).toEqual({ after: 40, afterDouble: null });
    expect(chipsLeft(100, 0)).toEqual({ after: 100, afterDouble: 100 });
  });
});

const limits37 = { min: 1, max: 37 };
const limits50 = { min: 1, max: 50 };

describe('betting', () => {
  it('offers the chips +1 +5 +10 +25', () => {
    expect(BET_CHIPS).toEqual([1, 5, 10, 25]);
  });

  describe('clampBet', () => {
    it('keeps a bet inside the limits (a stack of 37: 1 to 37)', () => {
      expect(clampBet(0, limits37)).toBe(1);
      expect(clampBet(1, limits37)).toBe(1);
      expect(clampBet(20, limits37)).toBe(20);
      expect(clampBet(37, limits37)).toBe(37);
      expect(clampBet(38, limits37)).toBe(37);
      expect(clampBet(500, limits37)).toBe(37);
    });

    it('rounds down to a whole number and survives NaN', () => {
      expect(clampBet(12.9, limits37)).toBe(12);
      expect(clampBet(Number.NaN, limits37)).toBe(1);
    });
  });

  describe('maxOf', () => {
    it('is the largest bet allowed, min(maxBet, stack) as the server computed it', () => {
      expect(maxOf({ min: 1, max: Math.min(50, 37) })).toBe(37);
      expect(maxOf(limits50)).toBe(50);
    });

    it('is 0 without limits (no betting phase)', () => {
      expect(maxOf(null)).toBe(0);
    });
  });

  describe('addChip', () => {
    it('adds the chip and stops at the maximum', () => {
      expect(addChip(0, 5, limits37)).toBe(5);
      expect(addChip(10, 25, limits37)).toBe(35);
      expect(addChip(35, 25, limits37)).toBe(37);
    });
  });

  describe('defaultBet (the "Bet N" button)', () => {
    it('is the last bet, clamped', () => {
      expect(defaultBet(20, limits50)).toBe(20);
      expect(defaultBet(40, limits37)).toBe(37);
      expect(defaultBet(40, { min: 1, max: 25 })).toBe(25);
    });

    it('is 10, clamped, on the first hand', () => {
      expect(DEFAULT_BET).toBe(10);
      expect(defaultBet(null, limits50)).toBe(10);
      expect(defaultBet(null, { min: 1, max: 6 })).toBe(6);
    });

    it('is 0 when there are no limits', () => {
      expect(defaultBet(10, null)).toBe(0);
    });
  });

  describe('isBetAllowed', () => {
    it('accepts whole numbers within the limits only', () => {
      expect(isBetAllowed(1, limits37)).toBe(true);
      expect(isBetAllowed(37, limits37)).toBe(true);
      expect(isBetAllowed(0, limits37)).toBe(false);
      expect(isBetAllowed(38, limits37)).toBe(false);
      expect(isBetAllowed(2.5, limits37)).toBe(false);
      expect(isBetAllowed(5, null)).toBe(false);
    });
  });
});
