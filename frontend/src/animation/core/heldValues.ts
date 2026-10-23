/**
 * Values a table keeps ON SCREEN for a while after its state has moved on (the picture lags the state): a place a flight
 * touches shows its old value until the copy lands, and a click on it must be judged on what the player saw, not on
 * the latest state. Each place (a game's own key) holds a value until a timestamp, after which the latest state is the
 * truth again. Pure: time is passed in.
 */
export type Held<V> = Record<string, { value: V; until: number }>;

/** The values to keep for these places, until `until`. */
export function holdValues<V>(values: Record<string, V>, until: number): Held<V> {
  return Object.fromEntries(Object.entries(values).map(([place, value]) => [place, { value, until }]));
}

/** Adds a new hold to the one in force: a place already held keeps the value it showed first, and the later end. */
export function mergeHeld<V>(current: Held<V>, incoming: Held<V>): Held<V> {
  const merged: Held<V> = { ...current };
  for (const [place, held] of Object.entries(incoming)) {
    const known = merged[place];
    merged[place] = known ? { value: known.value, until: Math.max(known.until, held.until) } : held;
  }
  return merged;
}

/** What is still held at `now`: a value whose flight has landed (end time reached) is dropped. */
export function liveHeld<V>(held: Held<V>, now: number): Held<V> {
  return Object.fromEntries(Object.entries(held).filter(([, h]) => h.until > now));
}
