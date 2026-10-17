import { computed, getCurrentScope, onScopeDispose, shallowRef, watch } from 'vue';
import type { MangolaLastMove, MangolaState, MangolaStep } from '../model/mangola/MangolaState';
import { boardMarks, NO_BOARD_MARKS, type BoardMarks } from '../model/mangola/marks';
import { sowingFrames, type HandPosition, type SowingFrame } from '../model/mangola/replay';
import { SPEED_PROFILES } from '../model/mangola/trace';
import type { AnimationSpeed } from './useAnimationSpeed';

/** More states than this waiting behind an animation: the older ones are dropped and the newest is shown at once. */
export const MAX_QUEUED_STATES = 2;

/** The move to read out and the version of the state that carried it. */
export interface AnnouncedMove {
  version: number;
  move: MangolaLastMove;
}

export interface SowingAnimationOptions {
  /** The animation speed now (read at each move, and watched: a change applies to the sowing in progress). Defaults to Fast. */
  speed?: () => AnimationSpeed;
  /**
   * My seat. A move by another seat first shows its chosen pit, the hand resting on it, before the seeds leave;
   * my own move does not (I just chose the pit). Without it no move gets that pause.
   */
  mySeat?: () => number | null;
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
 *   the Instant speed) is shown at once: the board on screen always ends equal to the server's;
 * - a state that arrives meanwhile is queued and played after; a state not newer than the newest one is ignored;
 *   with more than `MAX_QUEUED_STATES` waiting, only the newest is kept (and shown without a sowing);
 * - a move by the opponent first shows the pit it chose, the hand resting on it (`chosenPit`), for a short pause
 *   (500 ms in Normal), so the decision can be seen before the seeds leave; Instant shows the result at once;
 * - `skip()` (the Skip button, a tap on the board) ends the animation on the newest board known;
 * - `announced` is set once per move, at its start (or at once when it is not animated), for the live region.
 */
export function useSowingAnimation(options: SowingAnimationOptions = {}) {
  const speedNow = options.speed ?? ((): AnimationSpeed => 'fast');
  const mySeat = options.mySeat ?? ((): number | null => null);

  /** The last state whose animation is over (or that was shown at once). */
  const shownState = shallowRef<MangolaState | null>(null);
  const frame = shallowRef<SowingFrame | null>(null);
  /** The pause before the seeds leave: the pit chosen, and the seeds the hand will pick up there. */
  const leadIn = shallowRef<HandPosition | null>(null);
  const animating = computed(() => frame.value !== null || leadIn.value !== null);
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
  /** The board before the move and its trace, to time them again when the speed changes. */
  let playing: { before: readonly number[]; trace: readonly MangolaStep[] } | null = null;
  /** The frame being shown, -1 during the pause before the first one. */
  let index = -1;

  const displayBoard = computed<readonly (number | null)[] | null>(() => frame.value?.board ?? shownState.value?.board ?? null);
  const hand = computed<HandPosition | null>(() => frame.value?.hand ?? leadIn.value);
  const activePit = computed<number | null>(() => frame.value?.hand.pit ?? null);
  /** The pit the opponent chose, shown before the seeds leave it. */
  const chosenPit = computed<number | null>(() => leadIn.value?.pit ?? null);
  const capturing = computed<number[]>(() => frame.value?.captured ?? []);
  // The marks of the last move appear once it is over, so they do not give the sowing away.
  const marks = computed<BoardMarks>(() =>
    animating.value ? NO_BOARD_MARKS : boardMarks(markedMove.value, previousBoard.value ?? undefined));

  function clearTimer(): void {
    if (timer !== null) clearTimeout(timer);
    timer = null;
  }

  /** Ends whatever is playing, with no state change. */
  function stopPlaying(): void {
    clearTimer();
    frame.value = null;
    leadIn.value = null;
    frames = [];
    target = null;
    playing = null;
    index = -1;
  }

  function announce(state: MangolaState): void {
    if (state.lastMove) announced.value = { version: state.version, move: state.lastMove };
  }

  /** Shows `state` as it is. `moved`: its last move is a new one (announced, and the marks follow it). */
  function settle(state: MangolaState, moved: boolean, before: readonly (number | null)[] | null): void {
    stopPlaying();
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
    stopPlaying();
    queue = [];
    newestVersion = -1;
    shownState.value = null;
    announced.value = null;
    previousBoard.value = null;
    markedMove.value = undefined;
  }

  /** Shows step `i` of the sowing (-1: the pause before it) and schedules the next one. */
  function enter(i: number): void {
    clearTimer();
    index = i;
    if (i < 0) {
      frame.value = null;
      leadIn.value = leadInHand();
      timer = setTimeout(() => { timer = null; enter(0); }, SPEED_PROFILES[speedNow()].leadInMs);
      return;
    }
    leadIn.value = null;
    frame.value = frames[i];
    timer = setTimeout(() => {
      timer = null;
      if (i + 1 < frames.length) enter(i + 1);
      else finish();
    }, frames[i].durationMs);
  }

  function leadInHand(): HandPosition {
    const pit = target!.lastMove!.pit;
    return { pit, seeds: playing!.before[pit] };
  }

  function finish(): void {
    // The marks and the board before the move were set when the animation began; only the screen state moves on.
    const done = target!;
    stopPlaying();
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
    const speed = speedNow();
    const planned = speed === 'instant' ? null : sowingFrames(before, next.lastMove!.trace, speed);
    const lastFrame = planned?.frames[planned.frames.length - 1];
    if (!planned || !lastFrame || !sameBoard(lastFrame.board, next.board)) {
      settle(next, true, before);
      drain();
      return;
    }
    frames = planned.frames;
    target = next;
    playing = { before: before as number[], trace: next.lastMove!.trace };
    previousBoard.value = before;
    markedMove.value = next.lastMove;
    announce(next);
    const me = mySeat();
    const opponentMove = me !== null && next.lastMove!.seat !== me;
    enter(opponentMove && SPEED_PROFILES[speed].leadInMs > 0 ? -1 : 0);
  }

  // A change of speed applies to the sowing in progress: Instant ends it on its final board, another speed
  // times the rest of it again.
  watch(speedNow, (speed) => {
    if (!animating.value) return;
    if (speed === 'instant') {
      finish();
      return;
    }
    const replanned = sowingFrames(playing!.before, playing!.trace, speed);
    if (!replanned) {
      finish();
      return;
    }
    frames = replanned.frames;
    enter(index);
  }, { flush: 'sync' });

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
    shownState, displayBoard, hand, activePit, chosenPit, capturing, animating, announced, marks,
    previousBoard, push, skip,
  };
}
