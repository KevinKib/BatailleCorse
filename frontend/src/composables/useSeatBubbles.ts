import { ref, type Ref } from 'vue';
import type { SeatBubble } from '../model/president/motion';

export interface ShownBubble {
  kind: SeatBubble['kind'];
  /** Changes with every bubble, so the same kind twice pops twice. */
  key: number;
}

/**
 * The bubbles over the seats ("Pass", "Out on a 2", "Four completed"). They are not queued with the flying cards:
 * each shows at once, a burst `gapMs` apart, for the life it is given; a new bubble on a seat replaces the old one.
 * Purely visual.
 */
export function useSeatBubbles(options: { gapMs: number }): {
  bySeat: Ref<Record<number, ShownBubble>>;
  show: (bubble: SeatBubble, lifeMs: number) => void;
  cancel: () => void;
} {
  const bySeat = ref<Record<number, ShownBubble>>({});
  const timers = new Set<ReturnType<typeof setTimeout>>();
  let nextFreeAt = 0;
  let counter = 0;

  const later = (ms: number, run: () => void) => {
    const timer = setTimeout(() => { timers.delete(timer); run(); }, ms);
    timers.add(timer);
  };

  function pop(bubble: SeatBubble, lifeMs: number) {
    const key = ++counter;
    bySeat.value = { ...bySeat.value, [bubble.seat]: { kind: bubble.kind, key } };
    later(lifeMs, () => {
      if (bySeat.value[bubble.seat]?.key !== key) return;
      const { [bubble.seat]: _gone, ...rest } = bySeat.value;
      bySeat.value = rest;
    });
  }

  function show(bubble: SeatBubble, lifeMs: number) {
    const now = Date.now();
    const at = Math.max(now, nextFreeAt);
    nextFreeAt = at + options.gapMs;
    if (at === now) pop(bubble, lifeMs);
    else later(at - now, () => pop(bubble, lifeMs));
  }

  function cancel() {
    for (const timer of timers) clearTimeout(timer);
    timers.clear();
    bySeat.value = {};
    nextFreeAt = 0;
  }

  return { bySeat, show, cancel };
}
