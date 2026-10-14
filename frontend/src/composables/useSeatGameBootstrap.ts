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

/** Reads the persisted seat+token for a game (key `${tokenKeyPrefix}${gameId}`), or null if none stored. */
export function resolveSeatSession(tokenKeyPrefix: string, gameId: string): { seat: number; token: string } | null {
  const tokens = readStoredTokens(`${tokenKeyPrefix}${gameId}`);
  if (!tokens) return null;
  const seats = Object.keys(tokens).map(Number);
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
) {
  const router = useRouter();
  let unmounted = false;
  onBeforeUnmount(() => { unmounted = true; });

  onMounted(async () => {
    const session = resolveSeatSession(tokenKeyPrefix, gameId);
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
    }
  });
}
