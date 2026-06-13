import { onBeforeUnmount, onMounted } from 'vue';

export function useHotkeys(
  onSend: () => void,
  onSlap: () => void,
  getSendKeys: () => string[] = () => ['q'],
  getSlapKeys: () => string[] = () => ['d'],
) {
  function handleKey(e: KeyboardEvent) {
    const key = e.key.toLowerCase();
    if (getSendKeys().includes(key)) onSend();
    if (getSlapKeys().includes(key)) onSlap();
  }

  onMounted(() => document.addEventListener('keyup', handleKey));
  onBeforeUnmount(() => document.removeEventListener('keyup', handleKey));
}
