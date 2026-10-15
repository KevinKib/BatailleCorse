import { describe, it, expect } from 'vitest';
import {
  DRAFT_SEEDS, addSeed, checkerboardLayout, clearLayout, draftOf, randomLayout, removeSeed, seedsLeft, twoPerPit,
} from './draft';
import { loopOf } from './geometry';
import { checkerboardBoard, mangolaState, placementState } from './mangolaFixtures';

const sum = (xs: number[]) => xs.reduce((a, b) => a + b, 0);

describe('draft counter', () => {
  it('has 32 seeds to place', () => {
    expect(DRAFT_SEEDS).toBe(32);
  });

  it('seedsLeft is 32 minus what is placed', () => {
    expect(seedsLeft(clearLayout())).toBe(32);
    expect(seedsLeft(twoPerPit())).toBe(0);
    expect(seedsLeft([...Array(15).fill(0), 5])).toBe(27);
  });

  it('addSeed adds one to a pit and returns a new draft', () => {
    const draft = clearLayout();
    const next = addSeed(draft, 3);
    expect(next[3]).toBe(1);
    expect(draft[3]).toBe(0);
  });

  it('addSeed refuses a 33rd seed', () => {
    const full = twoPerPit();
    expect(addSeed(full, 0)).toEqual(full);
  });

  it('removeSeed never goes below zero', () => {
    expect(removeSeed(clearLayout(), 2)).toEqual(clearLayout());
    const draft = addSeed(clearLayout(), 2);
    expect(removeSeed(draft, 2)[2]).toBe(0);
  });
});

describe('presets', () => {
  it('Two per pit: 16 pits of 2', () => {
    expect(twoPerPit()).toEqual(Array(16).fill(2));
  });

  it('Clear: 16 empty pits', () => {
    expect(clearLayout()).toEqual(Array(16).fill(0));
  });

  it.each(['A', 'B'] as const)('Checkerboard of side %s matches the classic board on its own pits', (side) => {
    const layout = checkerboardLayout(side);
    expect(layout).toHaveLength(16);
    expect(sum(layout)).toBe(32);
    const board = checkerboardBoard();
    loopOf(side).forEach((pit, i) => expect(layout[i]).toBe(board[pit]));
  });

  it('Random: 32 seeds dropped on pits drawn uniformly, 16th pit included', () => {
    expect(randomLayout(() => 0)).toEqual([32, ...Array(15).fill(0)]);
    expect(randomLayout(() => 0.999)).toEqual([...Array(15).fill(0), 32]);
    let i = 0;
    const layout = randomLayout(() => ((i++ % 16) + 0.5) / 16);
    expect(layout).toEqual(Array(16).fill(2));
  });

  it('Random with the real generator is a valid layout', () => {
    const layout = randomLayout();
    expect(layout).toHaveLength(16);
    expect(sum(layout)).toBe(32);
    expect(layout.every(n => Number.isInteger(n) && n >= 0)).toBe(true);
  });
});

describe('draftOf (the draft the server holds, read from the DTO)', () => {
  it('reads my 16 counts in the order of my loop', () => {
    const draft = [1, 2, 3, 4, 5, 6, 7, 0, 0, 0, 0, 0, 0, 0, 0, 4];
    expect(draftOf(placementState(draft), 0)).toEqual(draft);
  });

  it('reads the loop of side B for the other seat', () => {
    const state = placementState(Array(16).fill(0), { seatA: 1 });
    state.board[24] = 3; // first pit of the loop of B
    state.board[16] = 1; // last pit of the loop of B
    const draft = draftOf(state, 0);
    expect(draft[0]).toBe(3);
    expect(draft[15]).toBe(1);
  });

  it('is empty (zeros) when the pits are unknown or the game is not in placement', () => {
    expect(draftOf(mangolaState(), 0)).toEqual(Array(16).fill(0));
    const hidden = placementState();
    expect(draftOf(hidden, 1)).toEqual(Array(16).fill(0));
  });
});
