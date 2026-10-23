import { getCurrentScope, onScopeDispose } from 'vue';

/** How often a held picture is looked at again. */
const POLL_MS = 100;

export interface TableSettledSource {
  /** How long until the moves already queued are over (`TableMotion.backlogMs`). */
  backlogMs: () => number;
  /** A picture of the table is still held (a sweep, a closed pile). */
  busy?: () => boolean;
  /** The rest, with nothing new, that makes the table settled. */
  settleMs: () => number;
}

/**
 * Knows when a table has finished showing what it was told, so that something can wait for it (the sheet of the end of
 * a round, a final screen): the queue has nothing left (`backlogMs`), no picture is held (`busy`) and `settleMs` of
 * rest have passed with nothing new. Purely visual: the game itself never waits for it.
 *
 * `whenSettled()` starts a wait (a new one replaces the one before; a cancelled or replaced wait never ends).
 */
export function useTableSettled(source: TableSettledSource): {
  whenSettled: () => Promise<void>;
  cancel: () => void;
} {
  let timer: ReturnType<typeof setTimeout> | null = null;

  function cancel() {
    if (timer !== null) { clearTimeout(timer); timer = null; }
  }

  function wait(ms: number, then: () => void) {
    cancel();
    timer = setTimeout(() => { timer = null; then(); }, ms);
  }

  function step(done: () => void, afterRest: boolean) {
    if (source.busy?.()) return wait(POLL_MS, () => step(done, false));
    const backlog = source.backlogMs();
    if (backlog > 0) return wait(backlog, () => step(done, false));
    const settleMs = source.settleMs();
    if (!afterRest && settleMs > 0) return wait(settleMs, () => step(done, true));
    done();
  }

  function whenSettled(): Promise<void> {
    cancel();
    return new Promise<void>(resolve => step(resolve, false));
  }

  if (getCurrentScope()) onScopeDispose(cancel);

  return { whenSettled, cancel };
}
