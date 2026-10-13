import { watch, type Ref } from 'vue';
import { useRouter } from 'vue-router';

/**
 * When the host removes this player from the lobby (the store flag turns true), leads to the Game selection with
 * a notice (`?removed=1`). Replace, not push: Back must not return to a room the player no longer sits in.
 * The flag is consumed so the next room starts clean.
 */
export function useRemovedRedirect(removedByHost: Ref<boolean>, acknowledge: () => void) {
  const router = useRouter();
  watch(removedByHost, (removed) => {
    if (!removed) return;
    acknowledge();
    void router.replace({ name: 'games', query: { removed: '1' } });
  }, { immediate: true });
}
