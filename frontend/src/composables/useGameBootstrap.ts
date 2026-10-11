import { onBeforeUnmount, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useBatailleCorseStore } from '../state/BatailleCorse.store';
import webSocketService from '../service/WebSocketService';
import { findGame } from '../model/games';
import { readStoredTokens } from './readStoredTokens';
import type BatailleCorse from '../model/BatailleCorse';

interface UseGameBootstrapOptions {
  /** From useEndScreen — reveal the end overlay synchronously if reloading into a finished game. */
  revealImmediatelyIfOver: () => void;
}

/**
 * Owns the game's mount/unmount lifecycle: validates the stored tokens, fetches and
 * hydrates the initial state, restores the session, loads the multiplayer view, and
 * subscribes to the game's websocket topic. On unmount it cancels the auto-grab timer
 * and unsubscribes. Without a stored token it leads to the join form; if the game no longer exists, to the Game home with a notice.
 */
export function useGameBootstrap(options: UseGameBootstrapOptions) {
  const { revealImmediatelyIfOver } = options;
  const store = useBatailleCorseStore();
  const route = useRoute();
  const router = useRouter();

  onMounted(async () => {
    const gameId = route.params.id as string;

    const stored = readStoredTokens(`tokens:${gameId}`);
    if (!stored) {
      // Someone shared the room URL instead of the invite link: join it.
      router.replace(findGame('bataillecorse')!.joinRoute(gameId));
      return;
    }

    const response = await fetch(`/api/game/${gameId}`);
    if (!response.ok) {
      router.replace({ path: findGame('bataillecorse')!.gameHomeRoute, query: { gone: '1' } });
      return;
    }

    const gameState = await response.json() as BatailleCorse;
    store.hydrate(gameId, gameState);
    revealImmediatelyIfOver();
    store.restoreSession(stored as Record<number, string>);
    await store.loadSessionView(gameId);
    webSocketService.subscribeToGame(gameId);
  });

  onBeforeUnmount(() => {
    store.cancelAutoGrab();
    webSocketService.unsubscribeFromGame();
  });
}
