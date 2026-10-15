import { describe, it, expect } from 'vitest';
import { moveSentence } from './announce';
import { boardOf, sow } from './sowingFixtures';
import { pitFromName } from './geometry';
import { messagesEn } from '../../locales/en';
import type { MangolaLastMove } from './MangolaState';

const ui = messagesEn.mangolaUi;
const lastMove = (before: string, pit: string, seat = 0): MangolaLastMove =>
  ({ seat, pit: pitFromName(pit)!, trace: sow(boardOf(before), pit).trace });

describe('moveSentence', () => {
  it('says who sowed from which pit', () => {
    expect(moveSentence(lastMove('a2:3', 'a2'), 'Alice', ui)).toBe('Alice sowed from a2.');
  });

  it('counts the relays', () => {
    expect(moveSentence(lastMove('a2:2 c2:3', 'a2'), 'Alice', ui)).toBe('Alice sowed from a2: 1 relay.');
    expect(moveSentence(lastMove('a2:17', 'a2'), 'Bob', ui)).toMatch(/^Bob sowed from a2: \d+ relays\.$/);
  });

  it('counts the seeds captured, with the relays', () => {
    expect(moveSentence(lastMove('a2:2 c2:1 c3:2 c4:3', 'a2'), 'Alice', ui)).toBe('Alice sowed from a2: 5 seeds captured.');
    expect(moveSentence(lastMove('a2:2 c2:1 d2:1 c3:1 c4:2 d3:1 d4:1', 'a2'), 'Alice', ui))
      .toBe('Alice sowed from a2: 1 relay, 5 seeds captured.');
  });

  it('uses the singular for one seed', () => {
    const move: MangolaLastMove = {
      seat: 0, pit: 8,
      trace: [{ type: 'PICK', pit: 8, count: 2 }, { type: 'CAPTURE', pit: 10, count: 1, facing: 18, behind: 26 }],
    };
    expect(moveSentence(move, 'Alice', ui)).toBe('Alice sowed from a2: 1 seed captured.');
  });
});
