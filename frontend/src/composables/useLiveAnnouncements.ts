import { ref, type Ref } from 'vue';

/** How many announcements the list region keeps: assistive technology reads the additions, the rest is noise. */
const KEPT = 3;

export interface LiveAnnouncement {
  text: string;
  /** Changes with every announcement, so the same text twice is two items. */
  key: number;
}

/**
 * The news of a table said to assistive technology: a short list that only grows (`aria-relevant="additions"`), the
 * last three items kept. Purely a text queue: it plays no part in the game.
 */
export function useLiveAnnouncements(): {
  items: Ref<LiveAnnouncement[]>;
  announce: (text: string) => void;
  cancel: () => void;
} {
  const items = ref<LiveAnnouncement[]>([]) as Ref<LiveAnnouncement[]>;
  let counter = 0;

  function announce(text: string) {
    items.value = [...items.value, { text, key: ++counter }].slice(-KEPT);
  }

  function cancel() {
    items.value = [];
  }

  return { items, announce, cancel };
}
