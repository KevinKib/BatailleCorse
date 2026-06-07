import { ref } from 'vue';

// Minimal open/close state for collapsible UI. Lifecycle-free so it can be
// unit-tested directly; DOM wiring (e.g. Esc key) lives in the consuming component.
export function useDisclosure(initial = false) {
  const isOpen = ref(initial);
  const open = () => { isOpen.value = true; };
  const close = () => { isOpen.value = false; };
  const toggle = () => { isOpen.value = !isOpen.value; };
  return { isOpen, open, close, toggle };
}
