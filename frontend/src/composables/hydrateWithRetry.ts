import type { HydrateResult } from '../application/SeatGameSession';

/** Waits before each new attempt after an `unavailable` answer: 3 retries, 4 attempts in all, 7 s in total. */
export const HYDRATE_RETRY_DELAYS_MS: readonly number[] = [1000, 2000, 4000];

/**
 * Loads a room, trying again when the server could not answer (`unavailable`: 5xx from a proxy under load,
 * network error). The attempts are bounded; the last answer is returned, so a server that stays down still ends
 * `unavailable` and the caller keeps the room page and the seat. `cancelled` stops the retries (the page left).
 */
export async function hydrateWithRetry(
  hydrate: () => Promise<HydrateResult>,
  cancelled: () => boolean = () => false,
  delays: readonly number[] = HYDRATE_RETRY_DELAYS_MS,
): Promise<HydrateResult> {
  let result = await hydrate();
  for (const delay of delays) {
    if (result !== 'unavailable' || cancelled()) break;
    await new Promise<void>(resolve => setTimeout(resolve, delay));
    if (cancelled()) break;
    result = await hydrate();
  }
  return result;
}
