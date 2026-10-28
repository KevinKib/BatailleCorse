import { describe, expect, it } from 'vitest';
import {
  kiloDeMerdeFinishedState,
  kiloDeMerdeRaceState,
  kiloDeMerdeRoundOverState,
  kiloDeMerdeStepState,
} from './fixtures';
import type { KiloDeMerdeState } from './KiloDeMerdeState';

const all: Array<[string, KiloDeMerdeState]> = [
  ['step', kiloDeMerdeStepState],
  ['race', kiloDeMerdeRaceState],
  ['round over', kiloDeMerdeRoundOverState],
  ['finished', kiloDeMerdeFinishedState],
];

describe('kilo de merde fixtures', () => {
  it.each(all)('%s: one player per seat, seats numbered from 0', (_name, state) => {
    expect(state.started).toBe(true);
    expect(state.players.map((p) => p.seat)).toEqual(state.players.map((_, i) => i));
  });

  it('the step has 3 seats, my choice pending and four cards in hand', () => {
    expect(kiloDeMerdeStepState.players).toHaveLength(3);
    expect(kiloDeMerdeStepState.phase).toBe('PASSING');
    expect(kiloDeMerdeStepState.myChoice).toBeNull();
    expect(kiloDeMerdeStepState.hand).toHaveLength(4);
    expect(kiloDeMerdeStepState.availableActions).toEqual(['PASS', 'SLAP']);
  });

  it('the race has 10 seats, a first slapper and an order', () => {
    expect(kiloDeMerdeRaceState.players).toHaveLength(10);
    expect(kiloDeMerdeRaceState.phase).toBe('RACE');
    expect(kiloDeMerdeRaceState.race?.firstSlapper).toBe(3);
    expect(kiloDeMerdeRaceState.race?.order.length).toBeGreaterThan(1);
    expect(kiloDeMerdeRaceState.raceEndsAt).not.toBeNull();
  });

  it('the round over reveals every hand and a flush', () => {
    const outcome = kiloDeMerdeRoundOverState.lastOutcome;
    expect(kiloDeMerdeRoundOverState.phase).toBe('ROUND_OVER');
    expect(outcome?.hands).toHaveLength(kiloDeMerdeRoundOverState.players.length);
    expect(outcome?.draws.some((d) => d.flushed)).toBe(true);
    expect(kiloDeMerdeRoundOverState.hand).toEqual([]);
  });

  it('the finished game has a shared first place', () => {
    const places = kiloDeMerdeFinishedState.result?.standings ?? [];
    expect(kiloDeMerdeFinishedState.phase).toBe('FINISHED');
    expect(places.filter((p) => p.place === 1).length).toBe(2);
    expect(kiloDeMerdeFinishedState.availableActions).toEqual([]);
  });
});
