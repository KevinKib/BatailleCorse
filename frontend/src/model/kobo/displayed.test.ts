import { describe, it, expect } from 'vitest';
import { planHold, mergeHold, liveHold, displayedOf, NO_HOLD } from './displayed';
import type { Movement } from './motion';
import { buildKoboState, buildKoboPlayer, buildKoboSlot, buildKoboCard } from '../fixtures/kobo';

const move = (from: Movement['from'], to: Movement['to'], kind: Movement['kind'] = 'travel'): Movement => ({
  kind, from, to, card: null, flip: false, turnDown: false, actor: 1, byOpponent: true,
  delayMs: 0, durationMs: 1000, holdAfterMs: 0, marks: [],
});

const before = buildKoboState({
  discardTop: buildKoboCard('KING'),
  players: [
    buildKoboPlayer(0, { slots: [buildKoboSlot(0, { revision: 4 }), buildKoboSlot(1, { revision: 2 })] }),
    buildKoboPlayer(1, { slots: [buildKoboSlot(0, { revision: 9 })] }),
  ],
});

describe('planHold', () => {
  it('keeps the revision shown before the event for every slot a movement touches, and the discard rank', () => {
    const hold = planHold(before, [
      move({ kind: 'slot', seat: 0, slot: 0 }, { kind: 'discard' }),
      move({ kind: 'slot', seat: 1, slot: 0 }, { kind: 'slot', seat: 0, slot: 0 }),
    ], 5000);
    expect(hold).toEqual({
      slots: { '0:0': { revision: 4, until: 5000 }, '1:0': { revision: 9, until: 5000 } },
      discard: { rank: 'KING', until: 5000 },
    });
  });

  it('ignores reveals, piles other than the discard, and the places that are not a slot of the state', () => {
    const hold = planHold(before, [
      move({ kind: 'table' }, { kind: 'table' }, 'reveal'),
      move({ kind: 'draw' }, { kind: 'hand' }),
      move({ kind: 'draw' }, { kind: 'newSlot', seat: 0 }),
      move({ kind: 'slot', seat: 0, slot: 7 }, { kind: 'seat', seat: 1 }),
    ], 5000);
    expect(hold).toEqual(NO_HOLD);
  });

  it('holds no discard rank when the pile was empty, and nothing without a previous state', () => {
    const empty = buildKoboState({ discardTop: null });
    expect(planHold(empty, [move({ kind: 'hand' }, { kind: 'discard' })], 5000)).toEqual(NO_HOLD);
    expect(planHold(null, [move({ kind: 'hand' }, { kind: 'discard' })], 5000)).toEqual(NO_HOLD);
  });
});

describe('mergeHold', () => {
  it('keeps the older value of a place (the one on screen) and the later end', () => {
    const first = planHold(before, [move({ kind: 'slot', seat: 0, slot: 0 }, { kind: 'discard' })], 3000);
    const later = buildKoboState({
      discardTop: buildKoboCard('2'),
      players: [buildKoboPlayer(0, { slots: [buildKoboSlot(0, { revision: 6 })] }), buildKoboPlayer(1)],
    });
    const second = planHold(later, [
      move({ kind: 'slot', seat: 0, slot: 0 }, { kind: 'discard' }),
      move({ kind: 'slot', seat: 0, slot: 0 }, { kind: 'slot', seat: 1, slot: 0 }),
    ], 6000);
    expect(mergeHold(first, second)).toEqual({
      slots: { '0:0': { revision: 4, until: 6000 }, '1:0': { revision: 0, until: 6000 } },
      discard: { rank: 'KING', until: 6000 },
    });
  });
});

describe('liveHold and displayedOf', () => {
  const hold = {
    slots: { '0:0': { revision: 4, until: 1000 }, '1:0': { revision: 9, until: 2000 } },
    discard: { rank: 'KING', until: 1500 },
  };

  it('drops what has landed', () => {
    expect(liveHold(hold, 999)).toEqual(hold);
    expect(liveHold(hold, 1000)).toEqual({ slots: { '1:0': { revision: 9, until: 2000 } }, discard: { rank: 'KING', until: 1500 } });
    expect(liveHold(hold, 2000)).toEqual(NO_HOLD);
  });

  it('reads as what the interaction needs', () => {
    expect(displayedOf(hold)).toEqual({ slotRevisions: { '0:0': 4, '1:0': 9 }, discardTopRank: 'KING' });
    expect(displayedOf(NO_HOLD)).toEqual({ slotRevisions: {}, discardTopRank: undefined });
  });
});
