import { onScopeDispose, ref, watch } from 'vue';
import { useTableSettled } from '../animation';
import type { EndKind } from '../model/president/endSequence';

/**
 * Sequences the end of a round on the President table (owner feedback, 2026-10-19): the game finishes visually (the
 * last plays, the closing badge, the sweep, the last place being decided) before any sheet comes in, and the sheet of
 * the round always comes before the final scores.
 *
 * - `round` or `final`: the round sheet shows once the table has settled; on the last round of the match (`final`)
 *   the final scores wait for `continueToFinal()` (a button on the sheet): the players read their round first.
 * - `abandoned` (a forfeit): no round to show, the final scores show once the table has settled.
 * - A page opened on an ended game (a reload) shows its sheet, or the final scores, at once: nothing is playing.
 *
 * The table has settled (`useTableSettled`) when the tempo queue has nothing left (`backlogMs`), no picture is held
 * (`busy`) and the `settleMs` of rest have passed with nothing new, after a short `graceMs` that lets the last move of
 * the round reach the queue (the state arrives before its event). Purely visual: the game itself never waits for it.
 */
export function usePresidentEndSequence(source: {
  kind: () => EndKind;
  /** How long until the moves already queued are over. */
  backlogMs: () => number;
  /** A picture of the table is still held (the sweep of a closed trick). */
  busy: () => boolean;
  timings: () => { graceMs: number; settleMs: number };
}) {
  const sheetVisible = ref(false);
  const finalVisible = ref(false);
  const settled = useTableSettled({
    backlogMs: source.backlogMs, busy: source.busy, settleMs: () => source.timings().settleMs,
  });
  let grace: ReturnType<typeof setTimeout> | null = null;

  function stop() {
    if (grace !== null) { clearTimeout(grace); grace = null; }
    settled.cancel();
  }

  function reveal(kind: EndKind) {
    if (kind === 'abandoned') finalVisible.value = true;
    else sheetVisible.value = true;
  }

  function immediately(kind: EndKind) {
    if (kind === 'round') sheetVisible.value = true;
    else finalVisible.value = true;
  }

  const initial = source.kind();
  if (initial !== 'none') immediately(initial);

  watch(source.kind, (kind, previous) => {
    stop();
    if (kind === 'none') {
      sheetVisible.value = false;
      finalVisible.value = false;
      return;
    }
    if (kind === previous) return;
    grace = setTimeout(() => {
      grace = null;
      void settled.whenSettled(() => reveal(kind));
    }, source.timings().graceMs);
  });

  /** The last round's sheet is read: on to the final scores. */
  function continueToFinal() { finalVisible.value = true; }

  onScopeDispose(stop);

  return { sheetVisible, finalVisible, continueToFinal };
}
