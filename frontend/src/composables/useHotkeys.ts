import { onBeforeUnmount, onMounted } from 'vue';

// Typing in a field or pressing a browser shortcut (Ctrl+D...) must never trigger a game action.
function isIgnored(e: KeyboardEvent): boolean {
  if (e.ctrlKey || e.metaKey || e.altKey) return true;
  const target = e.target as HTMLElement | null;
  return !!target && (['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName) || target.isContentEditable === true);
}

export function useHotkeys(
  onSend: () => void,
  onSlap: () => void,
  getSendKeys: () => string[] = () => ['q'],
  getSlapKeys: () => string[] = () => ['d'],
  // Called with every other key (lower-cased) and its physical code (layout independent), for games that need more than two actions.
  onOtherKey?: (key: string, code: string) => void,
) {
  function handleKey(e: KeyboardEvent) {
    if (isIgnored(e)) return;
    const key = e.key.toLowerCase();
    if (getSendKeys().includes(key)) onSend();
    else if (getSlapKeys().includes(key)) onSlap();
    else onOtherKey?.(key, e.code);
  }

  onMounted(() => document.addEventListener('keyup', handleKey));
  onBeforeUnmount(() => document.removeEventListener('keyup', handleKey));
}
