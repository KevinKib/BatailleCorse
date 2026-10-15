import { describe, it, expect } from 'vitest';
import { NO_BOARD_MARKS, boardMarks, endPit } from './marks';
import type { MangolaLastMove, MangolaStep } from './MangolaState';

const move = (trace: MangolaStep[], pit = 9, seat = 1): MangolaLastMove => ({ seat, pit, trace });

describe('endPit', () => {
  it('is the pit of the last seed dropped', () => {
    expect(endPit([{ type: 'PICK', pit: 9, count: 2 }, { type: 'DROP', pit: 10 }, { type: 'DROP', pit: 11 }])).toBe(11);
  });

  it('ignores the steps that come after the last drop and carry no seed', () => {
    expect(endPit([
      { type: 'PICK', pit: 9, count: 1 },
      { type: 'DROP', pit: 10 },
      { type: 'SKIP_EMPTY', pit: 11 },
    ])).toBe(10);
  });

  it('is null for a trace without a drop', () => {
    expect(endPit([])).toBeNull();
    expect(endPit([{ type: 'PICK', pit: 9, count: 1 }])).toBeNull();
  });
});

describe('boardMarks', () => {
  it('is empty without a last move', () => {
    expect(boardMarks(undefined)).toEqual(NO_BOARD_MARKS);
    expect(NO_BOARD_MARKS).toEqual({ start: null, end: null, captured: [] });
  });

  it('marks the start pit and the end pit of a quiet move', () => {
    const marks = boardMarks(move([{ type: 'PICK', pit: 9, count: 2 }, { type: 'DROP', pit: 10 }, { type: 'DROP', pit: 11 }]));
    expect(marks).toEqual({ start: 9, end: 11, captured: [] });
  });

  it('lists the captured pits once each, with the seeds they held when the previous board is known', () => {
    const previous: (number | null)[] = Array(32).fill(0);
    previous[18] = 3;
    previous[26] = 2;
    const marks = boardMarks(
      move([
        { type: 'PICK', pit: 9, count: 1 },
        { type: 'DROP', pit: 10 },
        { type: 'CAPTURE', pit: 10, count: 5, facing: 18, behind: 26 },
      ]),
      previous,
    );
    expect(marks.captured).toEqual([{ pit: 18, seeds: 3 }, { pit: 26, seeds: 2 }]);
  });

  it('leaves the seeds of a captured pit unknown without the previous board', () => {
    const marks = boardMarks(move([{ type: 'CAPTURE', pit: 10, count: 5, facing: 18, behind: 26 }]));
    expect(marks.captured).toEqual([{ pit: 18, seeds: null }, { pit: 26, seeds: null }]);
  });
});
