import { onBeforeUnmount, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';
import webSocketService from '../service/WebSocketService';
import { findGame } from '../model/games';
import { readStoredTokens } from './readStoredTokens';

interface UseGameBootstrapOptions {
  /** From useEndScreen — reveal the end overlay synchronously if reloading into a finished game. */
  revealImmediatelyIfOver: () => void;
}

/**
 * The one seat stored for a game, or null. An entry holding several seats comes from the former solo mode
 * (one browser held both tokens); that game is gone, so the entry is forgotten.
 */
export function resolveBatailleCorseSeat(gameId: string): { seat: number; token: string } | null {
  const key = `tokens:${gameId}`;
  const tokens = readStoredTokens(key);
  if (!tokens) return null;
  const seats = Object.keys(tokens);
  if (seats.length !== 1 || typeof tokens[seats[0]] !== 'string') {
    localStorage.removeItem(key);
    return null;
  }
  return { seat: Number(seats[0]), token: tokens[seats[0]] };
}

/**
 * Owns the room's mount/unmount lifecycle: re-attaches to the stored seat and loads the lobby or the running
 * game. Without a stored seat it leads to the join form; if the game no longer exists or the seat was lost,
 * to the Game home with a notice. On unmount it cancels the auto-grab timer and unsubscribes.
 */
export function useGameBootstrap(options: UseGameBootstrapOptions) {
  const { revealImmediatelyIfOver } = options;
  const store = useBatailleCorseStore();
  const route = useRoute();
  const router = useRouter();
  const game = findGame('bataillecorse')!;

  onMounted(async () => {
    const gameId = route.params.id as string;

    const stored = resolveBatailleCorseSeat(gameId);
    if (!stored) {
      // Someone shared the room URL instead of the invite link: join it.
      router.replace(game.joinRoute(gameId));
      return;
    }

    store.restore(gameId, stored.seat, stored.token);
    const result = await store.hydrate();
    // A server that fails or cannot be reached (`unavailable`) proves nothing: keep the token and the room page.
    if (result === 'gone' || result === 'lost') {
      localStorage.removeItem(`tokens:${gameId}`);
      router.replace({ path: game.gameHomeRoute, query: result === 'lost' ? { lost: '1' } : { gone: '1' } });
      return;
    }
    revealImmediatelyIfOver();
  });

  onBeforeUnmount(() => {
    store.cancelAutoGrab();
    webSocketService.unsubscribeFromGame();
    webSocketService.unsubscribeFromSeat();
  });
}
