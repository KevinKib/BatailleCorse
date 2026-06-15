import { onMounted } from 'vue';
import { useRouter } from 'vue-router';
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

/** On mount: re-attach to the game from localStorage, or redirect to create. */
export function useBullshitBootstrap(gameId: string) {
  const router = useRouter();
  const store = useBullshitStore();

  onMounted(async () => {
    const session = resolveBullshitSession(gameId);
    if (!session) {
      router.replace('/games/bullshit/create');
      return;
    }
    store.restore(gameId, session.seat, session.token);
    await store.hydrate();
  });
}
