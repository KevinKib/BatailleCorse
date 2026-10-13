import { onBeforeUnmount, readonly, ref, type Ref } from 'vue';

/** Below this width the Kobo table drops the ring of opponents for a compact grid (px). */
export const NARROW_MAX_WIDTH = 767;

/** Whether the viewport is narrow (a phone), kept in step with the window. Not narrow without `matchMedia`. */
export function useNarrowScreen(maxWidth = NARROW_MAX_WIDTH): Readonly<Ref<boolean>> {
  const narrow = ref(false);
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return readonly(narrow);
  const query = window.matchMedia(`(max-width: ${maxWidth}px)`);
  narrow.value = query.matches;
  const onChange = (event: { matches: boolean }) => { narrow.value = event.matches; };
  query.addEventListener('change', onChange);
  onBeforeUnmount(() => query.removeEventListener('change', onChange));
  return readonly(narrow);
}
