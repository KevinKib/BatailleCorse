import { computed, getCurrentScope, onScopeDispose, ref, shallowRef } from 'vue';
import type { MangolaLastMove, MangolaState } from '../model/mangola/MangolaState';
import { boardMarks, NO_BOARD_MARKS, type BoardMarks } from '../model/mangola/marks';
import { sowingFrames, type HandPosition, type SowingFrame } from '../model/mangola/replay';
import { prefersReducedMotion } from './usePileAnimation';

/** More states than this waiting behind an animation: the older ones are dropped and the newest is shown at once. */
export const MAX_QUEUED_STATES = 2;

/** The move to read out and the version of the state that carried it. */
export interface AnnouncedMove {
  version: number;
  move: MangolaLastMove;
}

export interface SowingAnimationOptions {
  /** Defaults to the `prefers-reduced-motion` media query. */
  reducedMotion?: () => boolean;
}

const sameMove = (a: MangolaLastMove | undefined, b: MangolaLastMove | undefined): boolean =>
  JSON.stringify(a ?? null) === JSON.stringify(b ?? null);

const sameBoard = (a: readonly (number | null)[], b: readonly (number | null)[]): boolean =>
  a.length === b.length && a.every((count, i) => count === b[i]);

/**
 * Plays the sowing of each move on the board, for the screen to draw (the DTO board is already final).
 *
 * The screen calls `push(state)` for each state the store receives and draws `displayBoard` (the counts it shows
 * right now), `hand`, `activePit` and `capturing` while `animating`. The sequencing:
 *
 * - a state whose last move replays exactly from the board on screen to the board of the state is animated with
 *   the travelling hand (`sowingFrames`); any other state (first one, no move, a missed state, another game,
 *   reduced motion) is shown at once: the board on screen always ends equal to the server's;
 * - a state that arrives meanwhile is queued and played after; a state not newer than the newest one is ignored;
 *   with more than `MAX_QUEUED_STATES` waiting, only the newest is kept (and shown without a sowing);
 * - `skip()` (the Skip button, a tap on the board) ends the animation on the newest board known;
 * - `announced` is set once per move, at its start (or at once when it is not animated), for the live region.
 */
export function useSowingAnimation(options: SowingAnimationOptions = {}) {
  const reducedMotion = options.reducedMotion ?? prefersReducedMotion;

  /** The last state whose animation is over (or that was shown at once). */
  const shownState = shallowRef<MangolaState | null>(null);
  const frame = shallowRef<SowingFrame | null>(null);
  const animating = computed(() => frame.value !== null);
  const announced = shallowRef<AnnouncedMove | null>(null);
  /** The board before the last move, to say what each captured pit lost. */
  const previousBoard = shallowRef<readonly (number | null)[] | null>(null);
  /** The move the marks describe. */
  const markedMove = shallowRef<MangolaLastMove | undefined>(undefined);

  let queue: MangolaState[] = [];
  let newestVersion = -1;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let frames: SowingFrame[] = [];
  let target: MangolaState | null = null;

  const displayBoard = computed<readonly (number | null)[] | null>(() => frame.value?.board ?? shownState.value?.board ?? null);
  const hand = computed<HandPosition | null>(() => frame.value?.hand ?? null);
  const activePit = computed<number | null>(() => frame.value?.hand.pit ?? null);
  const capturing = computed<number[]>(() => frame.value?.captured ?? []);
  // The marks of the last move appear once it is over, so they do not give the sowing away.
  const marks = computed<BoardMarks>(() =>
    animating.value ? NO_BOARD_MARKS : boardMarks(markedMove.value, previousBoard.value ?? undefined));

  function clearTimer(): void {
    if (timer !== null) clearTimeout(timer);
    timer = null;
  }

  function announce(state: MangolaState): void {
    if (state.lastMove) announced.value = { version: state.version, move: state.lastMove };
  }

  /** Shows `state` as it is. `moved`: its last move is a new one (announced, and the marks follow it). */
  function settle(state: MangolaState, moved: boolean, before: readonly (number | null)[] | null): void {
    clearTimer();
    frame.value = null;
    frames = [];
    target = null;
    if (moved) {
      announce(state);
      previousBoard.value = before;
      markedMove.value = state.lastMove;
    } else if (!sameMove(markedMove.value, state.lastMove)) {
      markedMove.value = state.lastMove;
      previousBoard.value = null;
    }
    shownState.value = state;
  }

  function reset(): void {
    clearTimer();
    queue = [];
    newestVersion = -1;
    frame.value = null;
    frames = [];
    target = null;
    shownState.value = null;
    announced.value = null;
    previousBoard.value = null;
    markedMove.value = undefined;
  }

  function run(index: number): void {
    frame.value = frames[index];
    timer = setTimeout(() => {
      timer = null;
      if (index + 1 < frames.length) run(index + 1);
      else finish();
    }, frames[index].durationMs);
  }

  function finish(): void {
    // The marks and the board before the move were set when the animation began; only the screen state moves on.
    const done = target!;
    clearTimer();
    frame.value = null;
    frames = [];
    target = null;
    shownState.value = done;
    drain();
  }

  /** Starts the oldest queued state, or the only one. */
  function drain(): void {
    if (animating.value || queue.length === 0) return;
    if (queue.length > MAX_QUEUED_STATES) queue = [queue[queue.length - 1]];
    begin(queue.shift()!);
  }

  function begin(next: MangolaState): void {
    const current = shownState.value;
    if (current === null || current.id !== next.id) {
      settle(next, false, null);
      return;
    }
    const moved = next.lastMove !== undefined && !sameMove(current.lastMove, next.lastMove);
    if (!moved) {
      settle(next, false, null);
      return;
    }
    const before = current.board;
    const planned = reducedMotion() ? null : sowingFrames(before, next.lastMove!.trace);
    const lastFrame = planned?.frames[planned.frames.length - 1];
    if (!planned || !lastFrame || !sameBoard(lastFrame.board, next.board)) {
      settle(next, true, before);
      drain();
      return;
    }
    frames = planned.frames;
    target = next;
    previousBoard.value = before;
    markedMove.value = next.lastMove;
    announce(next);
    run(0);
  }

  /** The screen gives each state the store receives (null when there is no game). */
  function push(state: MangolaState | null): void {
    if (state === null) {
      reset();
      return;
    }
    const current = shownState.value;
    if (current !== null && current.id !== state.id) {
      // Another game (a rematch): nothing of the previous one carries over.
      reset();
    }
    if (state.version <= newestVersion) return;
    newestVersion = state.version;
    if (shownState.value === null) {
      settle(state, false, null);
      return;
    }
    queue.push(state);
    drain();
  }

  /** Ends the animation on the newest board known, with no sowing for the states that were waiting. */
  function skip(): void {
    if (!animating.value) return;
    const reached = target!;
    const newest = queue.length > 0 ? queue[queue.length - 1] : reached;
    queue = [];
    if (newest === reached) {
      finish();
      return;
    }
    const before = reached.board;
    settle(newest, newest.lastMove !== undefined && !sameMove(reached.lastMove, newest.lastMove), before);
  }

  if (getCurrentScope()) onScopeDispose(clearTimer);

  return {
    shownState, displayBoard, hand, activePit, capturing, animating, announced, marks,
    previousBoard, push, skip,
  };
}
