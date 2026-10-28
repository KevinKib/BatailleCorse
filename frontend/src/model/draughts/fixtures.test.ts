import { describe, expect, it } from 'vitest';
import {
  draughtsAmbiguousState,
  draughtsCaptureState,
  draughtsFinishedState,
  draughtsOpeningState,
  draughtsState,
} from './fixtures';
import type { DraughtsState } from './DraughtsState';

function count(state: DraughtsState, codes: string[]): number {
  return state.board.filter((c) => c !== null && codes.includes(c)).length;
}

const all = [
  draughtsOpeningState,
  draughtsCaptureState,
  draughtsAmbiguousState,
  draughtsFinishedState,
].map((s) => [s.id, s] as const);

describe('draughts fixtures', () => {
  it.each(all)('%s has 50 squares and counts that match its players', (_id, state) => {
    expect(state.board).toHaveLength(50);
    for (const p of state.players) {
      const men = p.colour === 'WHITE' ? 'w' : 'b';
      const king = p.colour === 'WHITE' ? 'W' : 'B';
      expect(count(state, [men])).toBe(p.men);
      expect(count(state, [king])).toBe(p.kings);
    }
  });

  it.each(all)('%s only lists moves that start on a piece of the mover', (_id, state) => {
    const mover = state.players.find((p) => p.seat === state.currentSeat)!;
    for (const m of state.legalMoves) {
      const code = state.board[m.from - 1];
      expect(code?.toLowerCase()).toBe(mover.colour === 'WHITE' ? 'w' : 'b');
      expect(m.path[m.path.length - 1]).toBe(m.to);
      expect(state.board[m.to - 1]).toBeNull();
    }
  });

  it('opening: White to move with its nine moves, none backwards', () => {
    expect(draughtsOpeningState.legalMoves).toHaveLength(9);
    expect(draughtsOpeningState.captureRequired).toBe(false);
    expect(draughtsOpeningState.availableActions).toContain('MOVE');
    for (const m of draughtsOpeningState.legalMoves) {
      expect(m.to).toBeLessThan(m.from);
    }
  });

  it('capture: a compulsory capture lists only captures', () => {
    expect(draughtsCaptureState.captureRequired).toBe(true);
    expect(draughtsCaptureState.maxCaptures).toBe(1);
    expect(draughtsCaptureState.legalMoves.every((m) => m.captures.length === 1)).toBe(true);
  });

  it('ambiguous: two moves share from and to but take different pieces', () => {
    const [a, b] = draughtsAmbiguousState.legalMoves;
    expect(a.from).toBe(b.from);
    expect(a.to).toBe(b.to);
    expect(a.captures).not.toEqual(b.captures);
  });

  it('finished: an outcome with a winner and a reason', () => {
    expect(draughtsFinishedState.phase).toBe('FINISHED');
    expect(draughtsFinishedState.outcome).toEqual({ status: 'FINISHED', winner: 0, reason: 'NO_PIECES' });
    expect(draughtsFinishedState.legalMoves).toEqual([]);
  });

  it('draughtsState builds from overrides', () => {
    expect(draughtsState({ version: 7 }).version).toBe(7);
  });
});
