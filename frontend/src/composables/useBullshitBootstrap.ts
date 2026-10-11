import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { findGame } from '../model/games';
import { readStoredTokens } from './readStoredTokens';
import { useBullshitStore } from '../state/Bullshit.store';

/** Reads the persisted seat+token for a game, or null if none stored. */
export function resolveBullshitSession(gameId: string): { seat: number; token: string } | null {
  const tokens = readStoredTokens(`bullshit:tokens:${gameId}`);
  if (!tokens) return null;
  const seats = Object.keys(tokens).map(Number);
  if (seats.length === 0) return null;
  const seat = seats[0];
  return { seat, token: tokens[String(seat)] };
}

/** On mount: re-attach to the game from localStorage, or lead to the join form / the Game home. */
export function useBullshitBootstrap(gameId: string) {
  const router = useRouter();
  const store = useBullshitStore();

  onMounted(async () => {
    const session = resolveBullshitSession(gameId);
    const game = findGame('bullshit')!;
    if (!session) {
      // Someone shared the room URL instead of the invite link: join it.
      router.replace(game.joinRoute(gameId));
      return;
    }
    store.restore(gameId, session.seat, session.token);
    const result = await store.hydrate();
    if (result !== 'ok') {
      // The game is gone (404) or the stored token no longer resolves (403, the seat was lost): forget the
      // token and offer a new game, saying which of the two happened.
      localStorage.removeItem(`bullshit:tokens:${gameId}`);
      router.replace({ path: game.gameHomeRoute, query: result === 'lost' ? { lost: '1' } : { gone: '1' } });
    }
  });
}
