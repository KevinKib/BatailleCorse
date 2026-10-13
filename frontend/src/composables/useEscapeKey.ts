import { onBeforeUnmount, onMounted } from 'vue';

/** Calls `handler` when Escape is pressed anywhere on the page, for as long as the component lives. */
export function useEscapeKey(handler: () => void) {
  const onKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') handler();
  };
  onMounted(() => document.addEventListener('keydown', onKeydown));
  onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown));
}
