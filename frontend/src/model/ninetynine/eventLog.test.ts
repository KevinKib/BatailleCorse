import { describe, it, expect } from 'vitest';
import { appendEntry, toLogEntry, MAX_LOG, type NinetyNineLogEntry } from './eventLog';
import type { NinetyNineEventData } from './NinetyNineState';
import { buildNinetyNineCard, buildNinetyNineFourSeatRound } from '../fixtures/ninetynine';

const empty: NinetyNineEventData = {
  seat: null, card: null, aceValue: null, totalAfter: null, reversed: null, hand: null,
  livesLeft: null, place: null, stockSize: null,
};
const nine = buildNinetyNineCard('9', 'HEART');

describe('toLogEntry', () => {
  const before = buildNinetyNineFourSeatRound();

  it('turns a play into an entry that knows the total and the pile top before it', () => {
    const entry = toLogEntry(7, 'PLAY', { ...empty, seat: 0, card: nine, totalAfter: 42, reversed: false }, before)!;
    expect(entry).toMatchObject({
      id: 7, eventType: 'PLAY', seat: 0, card: nine, aceValue: null, totalBefore: 42, totalAfter: 42,
      reversed: false, round: 1, pileTopBefore: before.pileTop,
    });
  });

  it('knows nothing of the state before when there is none', () => {
    const entry = toLogEntry(1, 'PLAY', { ...empty, seat: 2, card: nine, totalAfter: 9 }, null)!;
    expect(entry).toMatchObject({ totalBefore: null, round: null, pileTopBefore: null, reversed: false });
  });

  it('keeps the stuck hand and the lives of a bust, the stock of a reshuffle, the place of an elimination', () => {
    const hand = [nine];
    expect(toLogEntry(1, 'BUST', { ...empty, seat: 2, hand, livesLeft: 1 }, before))
      .toMatchObject({ eventType: 'BUST', seat: 2, hand, livesLeft: 1 });
    expect(toLogEntry(2, 'RESHUFFLE', { ...empty, stockSize: 30 }, before)).toMatchObject({ stockSize: 30 });
    expect(toLogEntry(3, 'ELIMINATED', { ...empty, seat: 2, place: 4 }, before)).toMatchObject({ place: 4 });
  });

  it('logs a round start even without data, but never the start of the game', () => {
    expect(toLogEntry(1, 'ROUND_START', null, before)).toMatchObject({ eventType: 'ROUND_START', seat: null });
    expect(toLogEntry(2, 'START', null, before)).toBeNull();
  });
});

describe('appendEntry', () => {
  const entry = (id: number): NinetyNineLogEntry => toLogEntry(id, 'RESHUFFLE', empty, null)!;

  it('puts the newest first and caps the log', () => {
    let log: NinetyNineLogEntry[] = [];
    for (let i = 1; i <= MAX_LOG + 5; i++) log = appendEntry(log, entry(i));
    expect(log).toHaveLength(MAX_LOG);
    expect(log[0].id).toBe(MAX_LOG + 5);
  });

  it('leaves the log alone without an entry', () => {
    const log = [entry(1)];
    expect(appendEntry(log, null)).toBe(log);
  });
});
