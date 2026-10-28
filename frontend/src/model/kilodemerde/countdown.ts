/** What a countdown ring and its number show at a given time. */
export interface Countdown {
  remainingMs: number;
  /** Whole seconds left, rounded up: 0 only once the time is over. */
  seconds: number;
  /** The share of `totalMs` left (0 to 1) for a ring; null when the total is not given. */
  fraction: number | null;
}

/**
 * The time left until `endsAt` (the server epoch ms of `passTimerEndsAt` / `raceEndsAt`) at `now` (the caller's
 * clock). Null when there is no timer. Pure: the caller ticks.
 */
export function countdown(endsAt: number | null, now: number, totalMs?: number): Countdown | null {
  if (endsAt === null) return null;
  const remainingMs = Math.max(0, endsAt - now);
  const fraction = totalMs && totalMs > 0 ? Math.min(1, remainingMs / totalMs) : null;
  return { remainingMs, seconds: Math.ceil(remainingMs / 1000), fraction };
}
