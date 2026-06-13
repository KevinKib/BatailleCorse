import { onBeforeUnmount, onMounted } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';

interface UseLeaveGuardOptions {
  isInProgress: () => boolean;
  mode: () => 'solo' | 'multiplayer';
  forfeit: () => void;
}

/**
 * Confirms before leaving an in-progress game. Confirming in multiplayer forfeits
 * (opponent wins immediately); solo just leaves. A finished/not-started game leaves
 * freely. Browser close/refresh shows the native prompt only — a hard close cannot
 * reliably send a forfeit, so it falls back to the server disconnect timer.
 */
export function useLeaveGuard(options: UseLeaveGuardOptions) {
  const { isInProgress, mode, forfeit } = options;

  onBeforeRouteLeave(() => {
    if (!isInProgress()) return true;
    const message = mode() === 'multiplayer'
      ? 'Leave the game? You will forfeit and your opponent wins.'
      : 'Leave the game? Your current game will be lost.';
    const confirmed = window.confirm(message);
    if (!confirmed) return false;
    if (mode() === 'multiplayer') forfeit();
    return true;
  });

  function handleBeforeUnload(event: BeforeUnloadEvent) {
    if (!isInProgress()) return;
    event.preventDefault();
    event.returnValue = '';
  }

  onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload));
  onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload));
}
