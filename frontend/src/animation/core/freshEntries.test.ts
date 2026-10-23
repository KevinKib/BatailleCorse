import { describe, expect, it } from 'vitest';
import { freshEntries } from './freshEntries';

const e = (id: number) => ({ id });

describe('freshEntries', () => {
  it('givenNewEntriesBeforeTheKnownHead_thenTheyComeOldestFirst', () => {
    const [a, b, c, d] = [e(1), e(2), e(3), e(4)];
    expect(freshEntries([d, c, b, a], b)).toEqual([c, d]);
  });

  it('givenTheHeadIsStillTheKnownOne_thenNothingIsNew', () => {
    const a = e(1);
    expect(freshEntries([a], a)).toEqual([]);
  });

  it('givenNoKnownHead_thenEveryEntryIsNewOldestFirst', () => {
    const [a, b] = [e(1), e(2)];
    expect(freshEntries([b, a], null)).toEqual([a, b]);
    expect(freshEntries([b, a], undefined)).toEqual([a, b]);
  });

  it('givenAHeadThatIsNotInTheLog_thenTheWholeLogIsNew', () => {
    const [a, b] = [e(1), e(2)];
    expect(freshEntries([b, a], e(9))).toEqual([a, b]);
  });

  it('givenAnEmptyLog_thenNothing', () => {
    expect(freshEntries([], e(1))).toEqual([]);
  });

  it('doesNotChangeTheLog', () => {
    const log = [e(3), e(2), e(1)];
    freshEntries(log, null);
    expect(log.map(x => x.id)).toEqual([3, 2, 1]);
  });
});
