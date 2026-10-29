import { onBeforeUnmount, onMounted } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';
import { useI18n } from './useI18n';

interface UseLeaveGuardOptions {
  isInProgress: () => boolean;
  forfeit: () => void;
}

/**
 * Confirms before leaving an in-progress game. Confirming forfeits (the opponents win the seat).
 * A finished/not-started game leaves freely. Browser close/refresh shows the native prompt only — a hard
 * close cannot reliably send a forfeit, so it falls back to the server disconnect timer.
 */
export function useLeaveGuard(options: UseLeaveGuardOptions) {
  const { isInProgress, forfeit } = options;
  const ui = useI18n().leaveGuard;

  onBeforeRouteLeave(() => {
    if (!isInProgress()) return true;
    const confirmed = window.confirm(ui.confirm);
    if (!confirmed) return false;
    try {
      forfeit();
    } catch {
      // The socket is down: the forfeit cannot be sent, but the player chose to go. The server's disconnect
      // timer concedes the seat instead.
    }
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
