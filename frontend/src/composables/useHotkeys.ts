import { onBeforeUnmount, onMounted } from 'vue';

export function useHotkeys(
  onSend: () => void,
  onSlap: () => void,
  getSendKeys: () => string[] = () => ['q'],
  getSlapKeys: () => string[] = () => ['d'],
) {
  function handleKey(e: KeyboardEvent) {
    if (getSendKeys().includes(e.key)) onSend();
    if (getSlapKeys().includes(e.key)) onSlap();
  }

  onMounted(() => document.addEventListener('keyup', handleKey));
  onBeforeUnmount(() => document.removeEventListener('keyup', handleKey));
}
