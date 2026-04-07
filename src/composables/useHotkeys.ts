import { onBeforeUnmount, onMounted } from 'vue';

export function useHotkeys(onSend: () => void, onSlap: () => void) {
  function handleKey(e: KeyboardEvent) {
    if (e.key === 'q' || e.key === 'c') onSend();
    if (e.key === 'd' || e.key === ' ') onSlap();
  }

  onMounted(() => document.addEventListener('keyup', handleKey));
  onBeforeUnmount(() => document.removeEventListener('keyup', handleKey));
}
