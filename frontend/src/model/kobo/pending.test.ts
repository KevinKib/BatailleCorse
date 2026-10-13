import { describe, it, expect } from 'vitest';
import { reconcilePending } from './pending';
import type { KoboPending } from './interaction';
import { buildKoboState, buildKoboPlayer, buildKoboSlot } from '../fixtures/kobo';

const armed: KoboPending = { kind: 'match-armed' };
const give: KoboPending = {
  kind: 'match-give', target: { seat: 1, slot: 0 }, expectedTopRank: 'KING', expectedRevision: 3,
};
const blind: KoboPending = { kind: 'blind-own', ownSlot: 1 };

function withTarget(revision: number, occupied = true) {
  return buildKoboState({
    availableActions: ['MATCH_DISCARD'],
    players: [
      buildKoboPlayer(0, { current: true }),
      buildKoboPlayer(1, { slots: [buildKoboSlot(0, { revision, occupied }), buildKoboSlot(1)] }),
    ],
  });
}

describe('reconcilePending', () => {
  it('keeps a selection that is still valid', () => {
    const state = withTarget(3);
    expect(reconcilePending(armed, state, state, 0)).toEqual(armed);
    expect(reconcilePending(give, state, state, 0)).toEqual(give);
  });

  it('drops the match modes when MATCH_DISCARD is no longer listed', () => {
    const next = { ...withTarget(3), availableActions: [] };
    expect(reconcilePending(armed, withTarget(3), next, 0)).toEqual({ kind: 'none' });
    expect(reconcilePending(give, withTarget(3), next, 0)).toEqual({ kind: 'none' });
  });

  it('drops match-give when the revision of the target changed', () => {
    expect(reconcilePending(give, withTarget(3), withTarget(4), 0)).toEqual({ kind: 'none' });
  });

  it('drops match-give when the target slot is emptied or its player is gone', () => {
    expect(reconcilePending(give, withTarget(3), withTarget(3, false), 0)).toEqual({ kind: 'none' });
    const gone = buildKoboState({ availableActions: ['MATCH_DISCARD'], players: [buildKoboPlayer(0)] });
    expect(reconcilePending(give, withTarget(3), gone, 0)).toEqual({ kind: 'none' });
  });

  it('drops blind-own when BLIND_SWAP is no longer listed', () => {
    const before = buildKoboState({ availableActions: ['BLIND_SWAP'] });
    expect(reconcilePending(blind, before, before, 0)).toEqual(blind);
    expect(reconcilePending(blind, before, buildKoboState({ availableActions: [] }), 0)).toEqual({ kind: 'none' });
  });

  it('drops blind-own when my chosen slot changed or was emptied', () => {
    const mine = (revision: number, occupied = true) => buildKoboState({
      availableActions: ['BLIND_SWAP'],
      players: [buildKoboPlayer(0, { slots: [buildKoboSlot(0), buildKoboSlot(1, { revision, occupied })] }), buildKoboPlayer(1)],
    });
    expect(reconcilePending(blind, mine(0), mine(1), 0)).toEqual({ kind: 'none' });
    expect(reconcilePending(blind, mine(0), mine(0, false), 0)).toEqual({ kind: 'none' });
  });

  it('drops everything on a new round', () => {
    const before = withTarget(3);
    const next = { ...withTarget(3), round: before.round + 1 };
    expect(reconcilePending(armed, before, next, 0)).toEqual({ kind: 'none' });
    expect(reconcilePending(give, before, next, 0)).toEqual({ kind: 'none' });
  });

  it('drops everything when there is no running game', () => {
    expect(reconcilePending(armed, withTarget(3), null, 0)).toEqual({ kind: 'none' });
  });
});
