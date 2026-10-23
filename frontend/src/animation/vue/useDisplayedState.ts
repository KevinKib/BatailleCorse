import { shallowRef, type ShallowRef } from 'vue';
import { createDisplayQueue, type Displayed } from '../core/displayQueue';

/**
 * The state a table shows (`createDisplayQueue`) with its timers: a state reserved for a move is shown `revealIn` ms
 * later (when its cards have landed), after every state before it, and never later than `maxLagMs` after it arrived,
 * so the texts catch up with the game even if a picture is lost. `onReveal` is told each move as it is shown (its
 * bubbles pop then). Purely visual: the buttons and the rules read the latest state.
 */
export function useDisplayedState<S, E>(options: { maxLagMs: number; onReveal?: (entry: E) => void }): {
  displayed: ShallowRef<Displayed<S, E> | null>;
  reserve: (state: S, entry?: E | null) => number;
  revealIn: (id: number, ms: number) => void;
  show: (state: S, entry?: E | null) => void;
  flush: () => void;
  reset: (state: S | null) => void;
  /** How many states wait to be shown. */
  waiting: () => number;
  cancel: () => void;
} {
  const queue = createDisplayQueue<S, E>();
  const displayed = shallowRef<Displayed<S, E> | null>(null);
  const timers = new Set<ReturnType<typeof setTimeout>>();

  const apply = (shown: Displayed<S, E>[]) => {
    if (shown.length === 0) return;
    displayed.value = queue.current();
    for (const item of shown) if (item.entry !== null) options.onReveal?.(item.entry);
  };
  const later = (ms: number, action: () => void) => {
    const timer = setTimeout(() => { timers.delete(timer); action(); }, ms);
    timers.add(timer);
  };
  const cancel = () => { timers.forEach(clearTimeout); timers.clear(); };

  return {
    displayed,
    reserve(state, entry = null) {
      const id = queue.reserve(state, entry);
      later(options.maxLagMs, () => apply(queue.ready(id)));
      return id;
    },
    revealIn(id, ms) {
      if (ms <= 0) apply(queue.ready(id));
      else later(ms, () => apply(queue.ready(id)));
    },
    show(state, entry = null) { apply(queue.show(state, entry)); },
    flush() { apply(queue.flush()); },
    reset(state) {
      cancel();
      queue.reset(state);
      displayed.value = queue.current();
    },
    waiting: () => queue.waiting(),
    cancel,
  };
}
