import { onBeforeUnmount, ref, watch, type Ref } from 'vue';
import type { NinetyNineLogEntry } from '../model/ninetynine/eventLog';

/**
 * The total the table shows: the server's total, but for a play it changes only when the played card has landed on the
 * pile (`delayOf(entry)` ms after the play appeared, 0 for none). Anything else that moves the total (a new round, a
 * reload) shows at once. Purely visual: the state itself never waits.
 */
export function useLandedTotal(
  total: () => number,
  /** The log, newest first. */
  events: () => readonly NinetyNineLogEntry[],
  delayOf: (entry: NinetyNineLogEntry) => number,
): Ref<number> {
  const shown = ref(total());
  let timer: ReturnType<typeof setTimeout> | null = null;
  let handled = -1;
  const clear = () => { if (timer !== null) { clearTimeout(timer); timer = null; } };

  watch(() => [total(), events()[0]?.id ?? -1] as const, ([current]) => {
    clear();
    const play = events().find(e => e.eventType === 'PLAY' && e.totalAfter === current && e.id > handled);
    const delay = play ? delayOf(play) : 0;
    if (play && delay > 0 && play.totalBefore !== null && play.totalBefore !== current) {
      handled = play.id;
      shown.value = play.totalBefore;
      timer = setTimeout(() => { timer = null; shown.value = total(); }, delay);
    } else {
      shown.value = current;
    }
  });
  onBeforeUnmount(clear);
  return shown;
}
