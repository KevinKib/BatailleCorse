import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useLiveAnnouncements } from './useLiveAnnouncements';

describe('useLiveAnnouncements', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  it('givenNoAnnouncement_thenTheListIsEmpty', () => {
    expect(useLiveAnnouncements().items.value).toEqual([]);
  });

  it('givenAnnouncements_thenTheyAreAppendedInOrder', () => {
    const live = useLiveAnnouncements();
    live.announce('Bob is out, 1st');
    live.announce('You are out, 2nd');
    expect(live.items.value.map(i => i.text)).toEqual(['Bob is out, 1st', 'You are out, 2nd']);
  });

  it('givenMoreThanThree_thenOnlyTheLastThreeAreKept', () => {
    const live = useLiveAnnouncements();
    for (const text of ['a', 'b', 'c', 'd', 'e']) live.announce(text);
    expect(live.items.value.map(i => i.text)).toEqual(['c', 'd', 'e']);
  });

  it('givenTheSameTextTwice_thenEachItemHasItsOwnKey', () => {
    const live = useLiveAnnouncements();
    live.announce('same');
    live.announce('same');
    const [first, second] = live.items.value;
    expect(first.key).not.toBe(second.key);
  });

  it('givenCancel_thenTheListIsEmptied', () => {
    const live = useLiveAnnouncements();
    live.announce('a');
    live.cancel();
    vi.runAllTimers();
    expect(live.items.value).toEqual([]);
  });
});
