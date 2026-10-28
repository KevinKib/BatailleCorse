import { onBeforeUnmount, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import type { GameDefinition } from '../model/games';
import type { HydrateResult } from '../application/SeatGameSession';
import { readStoredTokens } from './readStoredTokens';
import { hydrateWithRetry } from './hydrateWithRetry';

/** The two store calls the bootstrap needs. */
export interface SeatGameBootstrapStore {
  restore(gameId: string, seat: number, token: string): void;
  hydrate(): Promise<HydrateResult>;
}

export interface SeatGameBootstrapOptions {
  /**
   * Accept only an entry holding exactly one seat with a string token; any other entry (Bataille Corse's
   * former solo mode held both tokens in one browser) is forgotten and leads to the join form.
   */
  singleSeat?: boolean;
  /** Runs once the room is loaded (not when the game is gone or the seat lost). */
  onHydrated?: () => void;
  /** Runs when the screen is torn down, after the bootstrap stops waiting for the server. */
  onUnmount?: () => void;
}

/** Reads the persisted seat+token for a game (key `${tokenKeyPrefix}${gameId}`), or null if none stored. */
export function resolveSeatSession(
  tokenKeyPrefix: string, gameId: string, singleSeat = false,
): { seat: number; token: string } | null {
  const key = `${tokenKeyPrefix}${gameId}`;
  const tokens = readStoredTokens(key);
  if (!tokens) return null;
  const seats = Object.keys(tokens).map(Number);
  if (singleSeat && (seats.length !== 1 || typeof tokens[String(seats[0])] !== 'string')) {
    localStorage.removeItem(key);
    return null;
  }
  if (seats.length === 0) return null;
  const seat = seats[0];
  return { seat, token: tokens[String(seat)] };
}

/** On mount: re-attach to the game from localStorage, or lead to the join form / the Game home. */
export function useSeatGameBootstrap(
  game: GameDefinition,
  gameId: string,
  tokenKeyPrefix: string,
  store: SeatGameBootstrapStore,
  options: SeatGameBootstrapOptions = {},
) {
  const router = useRouter();
  let unmounted = false;
  onBeforeUnmount(() => {
    unmounted = true;
    options.onUnmount?.();
  });

  onMounted(async () => {
    const session = resolveSeatSession(tokenKeyPrefix, gameId, options.singleSeat);
    if (!session) {
      // Someone shared the room URL instead of the invite link: join it.
      router.replace(game.joinRoute(gameId));
      return;
    }
    store.restore(gameId, session.seat, session.token);
    const result = await hydrateWithRetry(() => store.hydrate(), () => unmounted);
    // A server that fails or cannot be reached (`unavailable`, after a few tries) proves nothing: keep the token and the room page.
    if (result === 'gone' || result === 'lost') {
      // The game is gone (404) or the stored token no longer resolves (403, the seat was lost): forget the
      // token and offer a new game, saying which of the two happened.
      localStorage.removeItem(`${tokenKeyPrefix}${gameId}`);
      router.replace({ path: game.gameHomeRoute, query: result === 'lost' ? { lost: '1' } : { gone: '1' } });
      return;
    }
    options.onHydrated?.();
  });
}
