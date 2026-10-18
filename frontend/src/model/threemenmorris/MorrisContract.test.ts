import { describe, expect, it } from 'vitest';
import type {
  MorrisAction,
  MorrisEventData,
  MorrisOutcome,
  MorrisReason,
  MorrisRemovalEventData,
  MorrisState,
} from './MorrisState';
import { morrisState } from './morrisFixtures';

/**
 * Pins the additive wire contract of Six Men's Morris as types. Vitest does not type-check, so the real
 * assertions are the `satisfies` clauses below, checked by `npx tsc --noEmit`; the expectations only keep
 * the file honest at runtime. A 3x3 state carries none of the new fields.
 */
describe('Six Men\'s Morris contract types', () => {
  it('describes a pending removal with its mill, the legal removals and the counts', () => {
    const state = morrisState({
      phase: 'PLACEMENT',
      board: Array(16).fill(null),
      availableActions: ['REMOVE'],
      variant: 'tapatan',
      removal: { seat: 0, mill: [0, 1, 2] },
      legalRemovals: [9, 11],
      quietPlies: 42,
      quietPlyLimit: 50,
      players: [
        { seat: 0, name: 'Alice', current: true, piecesToPlace: 3, lost: 0 },
        { seat: 1, name: 'Bob', current: false, piecesToPlace: 4, lost: 1 },
      ],
    }) satisfies MorrisState;

    expect(state.removal?.mill).toEqual([0, 1, 2]);
    expect(state.legalRemovals).toEqual([9, 11]);
    expect(state.players[1].lost).toBe(1);
    expect(state.quietPlies).toBe(42);
  });

  it('knows the new actions and end reasons', () => {
    const action = 'REMOVE' satisfies MorrisAction;
    const reduced = 'REDUCED' satisfies MorrisReason;
    const outcome = { status: 'FINISHED', winner: 0, reason: 'DRAW_MOVE_LIMIT' } satisfies MorrisOutcome;

    expect([action, reduced, outcome.reason]).toEqual(['REMOVE', 'REDUCED', 'DRAW_MOVE_LIMIT']);
  });

  it('describes the REMOVE event and the mill carried by a PLACE or MOVE event', () => {
    const closed = {
      seat: 0, action: 'MOVE', point: null, from: 3, to: 4, mill: [2, 3, 4],
    } satisfies MorrisEventData;
    const removed = { seat: 0, action: 'REMOVE', point: 9 } satisfies MorrisRemovalEventData;
    const legacy = { seat: 1, action: 'PLACE', point: 4, from: null, to: null } satisfies MorrisEventData;

    expect(closed.mill).toEqual([2, 3, 4]);
    expect(removed.point).toBe(9);
    expect('mill' in legacy).toBe(false);
  });

  it('leaves the new fields out of a 3x3 state', () => {
    const state = morrisState();

    expect(state.removal).toBeUndefined();
    expect(state.legalRemovals).toBeUndefined();
    expect(state.quietPlies).toBeUndefined();
    expect(state.players[0].lost).toBeUndefined();
  });
});
