import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { findGame } from '../model/games';
import { useBullshitStore } from '../state/Bullshit.store';

/** Reads the persisted seat+token for a game, or null if none stored. */
export function resolveBullshitSession(gameId: string): { seat: number; token: string } | null {
  const stored = localStorage.getItem(`bullshit:tokens:${gameId}`);
  if (!stored) return null;
  const tokens = JSON.parse(stored) as Record<string, string>;
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
    if (!(await store.hydrate())) {
      // The game is gone or the token is refused: forget it and offer a new game.
      localStorage.removeItem(`bullshit:tokens:${gameId}`);
      router.replace({ path: game.gameHomeRoute, query: { gone: '1' } });
    }
  });
}
