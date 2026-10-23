/**
 * What is new in an event log that lists its newest entry first: the entries that come before the head already known,
 * oldest first, so they can be replayed in the order they happened. With no known head (or one no longer in the log)
 * every entry is new.
 */
export function freshEntries<E>(log: readonly E[], knownHead: E | null | undefined): E[] {
  const fresh: E[] = [];
  for (const entry of log) {
    if (entry === knownHead) break;
    fresh.push(entry);
  }
  return fresh.reverse();
}
