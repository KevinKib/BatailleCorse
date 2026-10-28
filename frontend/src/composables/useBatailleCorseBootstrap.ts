import { useBatailleCorseStore } from '../state/BatailleCorse.store';
import webSocketService from '../service/WebSocketService';
import { findGame } from '../model/games';
import { useSeatGameBootstrap } from './useSeatGameBootstrap';

/** Prefix of the seat token Bataille Corse stores: `tokens:{gameId}`, the entry players already hold. */
export const BATAILLE_CORSE_TOKEN_KEY_PREFIX = 'tokens:';

/**
 * On mount: re-attach to the stored seat and load the lobby or the running game; without one lead to the
 * join form, and if the game no longer exists or the seat was lost, to the Game home with a notice.
 * `revealImmediatelyIfOver` (from useEndScreen) reveals the end overlay synchronously when reloading into a
 * finished game. On unmount it cancels the auto-grab timer and unsubscribes.
 */
export function useBatailleCorseBootstrap(gameId: string, revealImmediatelyIfOver: () => void) {
  const store = useBatailleCorseStore();
  useSeatGameBootstrap(findGame('bataillecorse')!, gameId, BATAILLE_CORSE_TOKEN_KEY_PREFIX, store, {
    singleSeat: true,
    onHydrated: revealImmediatelyIfOver,
    onUnmount: () => {
      store.cancelAutoGrab();
      webSocketService.unsubscribeFromGame();
      webSocketService.unsubscribeFromSeat();
    },
  });
}
