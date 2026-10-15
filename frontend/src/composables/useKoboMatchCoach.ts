import { ref } from 'vue';

// The first-time cue that explains how to match (a note and a ring around my hand). Once dismissed, or once a
// match was made, it stays away in this browser. Storage is a nicety: when it is blocked the cue just shows
// again on the next visit.
export const MATCH_COACH_STORAGE_KEY = 'kobo.matchCoach';

export function createKoboMatchCoach(storage: Pick<Storage, 'getItem' | 'setItem'> | null) {
  const read = (): boolean => {
    try { return storage?.getItem(MATCH_COACH_STORAGE_KEY) === 'seen'; } catch { return false; }
  };
  const seen = ref(read());

  function dismiss(): void {
    seen.value = true;
    try { storage?.setItem(MATCH_COACH_STORAGE_KEY, 'seen'); } catch { /* not remembered */ }
  }

  return { seen, dismiss };
}

let shared: ReturnType<typeof createKoboMatchCoach> | undefined;
/** Forgets the shared cue so the next `useKoboMatchCoach` reads the storage again (tests). */
export function resetKoboMatchCoach(): void { shared = undefined; }

/** The one cue of the page (one preference, shared by every component that asks). */
export function useKoboMatchCoach() {
  shared ??= createKoboMatchCoach((() => { try { return window.localStorage; } catch { return null; } })());
  return shared;
}
