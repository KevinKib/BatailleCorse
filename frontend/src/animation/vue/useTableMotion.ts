import { computed, ref, watch, type ComputedRef, type Ref } from 'vue';
import { eventSpanMs, type MotionPlan, type TableMovement } from '../core/movement';
import { freshEntries } from '../core/freshEntries';
import { prefersReducedMotion } from '../core/reducedMotion';
import { speedFactor, type AnimationSpeed } from '../core/speed';
import type { QueueBounds } from '../core/tempo';
import { createTempoQueue } from '../core/tempoQueue';

export type { MotionPlan } from '../core/movement';

/** What the orchestrator needs from the layer (`CardMotionLayer`): to play the movements of one event. */
export type CardMotionLayerPlay<A> = (
  movements: TableMovement<A>[],
  options?: { marksOnly?: boolean; startInMs?: number; still?: boolean },
) => unknown;

/** What happened to an event, for the game's side effects (holds, counts, end gating). */
export interface Played<A> {
  plan: MotionPlan<A>;
  /** False: rings only (the backlog is full, Instant, reduced motion) or nothing to move. */
  motion: boolean;
  /** Instant or reduced motion. */
  still: boolean;
  startInMs: number;
  /** When its flights are over, as a timestamp of `now()` (equal to now when nothing flies). */
  endsAt: number;
}

export interface TableMotionOptions<E, A> {
  layer: Ref<{ play: CardMotionLayerPlay<A> } | null>;
  bounds: QueueBounds;
  /** Defaults to a fixed 'normal' (a table without a speed button). */
  speed?: Ref<AnimationSpeed>;
  /** Defaults to `Date.now`; tests pass a fake clock. */
  now?: () => number;
  /** The log, newest first (each entry a new object). Omit for a table that only calls `play`. */
  log?: () => readonly E[];
  /**
   * The event -> motion mapping of the game. Called once per fresh entry, oldest first, as it arrives. `null`: this
   * entry has no picture (nothing is played and `onPlayed` is not called).
   */
  plan?: (entry: E, context: { factor: number; still: boolean }) => MotionPlan<A> | null;
  /** Awaited before admission (Kobo: one frame for my drawn face; President: the held pile); may return another plan. */
  prepare?: (entry: E, plan: MotionPlan<A>) => Promise<MotionPlan<A>> | MotionPlan<A>;
  /**
   * Awaited for a log entry once it is admitted (its place in the queue is kept from its arrival), before the layer
   * plays it (President: the held pile of the trick before clears, then the DOM shows the new state). The time it takes
   * comes off the event's start. Not asked for a plan given to `play`, nor for one with nothing to move. If it throws,
   * the entry's picture is skipped.
   */
  beforePlay?: (admitted: Played<A>, entry: E) => Promise<void> | void;
  /** Told once an event is admitted and handed to the layer (`entry` is null for a plan given to `play`). */
  onPlayed?: (played: Played<A>, entry: E | null) => void;
}

export interface TableMotion<A> {
  /**
   * Plays a plan that does not come from the log (President's exchange from two states), in the same queue.
   * `maxLagMs` replaces the queue's own bound for this plan (President's exchange queues its returns behind a long deal).
   */
  play(plan: MotionPlan<A>, options?: { maxLagMs?: number }): Promise<Played<A>>;
  /** How long until everything admitted is over (0 when idle). */
  backlogMs(): number;
  /** `speedFactor(speed)`, never 0 (a table at Instant plans at Normal length: nothing plays anyway). */
  factor: ComputedRef<number>;
  /** Instant or reduced motion: nothing flies, the rings are still outlines. */
  still: ComputedRef<boolean>;
}

/**
 * The orchestrator of a table's pictures: finds the new entries of the game's log, asks the game for their motion,
 * admits them through the tempo queue (a burst plays one event after the other; past the bounds only the rings show),
 * hands them to the layer and tells the game when each one ends. A game keeps only its event -> motion mapping, its
 * anchors and its tempo numbers.
 *
 * Purely visual: nothing here waits for the picture to change the state, a failure only costs the picture, and the log
 * as it is when the table mounts is already known (a reload or a reconnection never animates).
 */
export function useTableMotion<E, A>(options: TableMotionOptions<E, A>): TableMotion<A> {
  const now = options.now ?? Date.now;
  const speed = options.speed ?? ref<AnimationSpeed>('normal');
  const queue = createTempoQueue(now, options.bounds);
  const factor = computed(() => speedFactor(speed.value) || 1);
  const stillNow = () => speed.value === 'instant' || prefersReducedMotion();
  const still = computed(stillNow);

  /**
   * Admits one plan through the queue, hands it to the layer, and reports. Never throws, except from `beforePlay`
   * (the caller then skips the entry).
   */
  async function admit(plan: MotionPlan<A>, entry: E | null, isStill: boolean, maxLagMs?: number): Promise<Played<A>> {
    const nothing = plan.movements.length === 0 && !(plan.followUps?.length);
    const { motion, startInMs: admittedStartInMs } = isStill || nothing
      ? { startInMs: 0, motion: false }
      : queue.admit(plan.spanMs ?? eventSpanMs(plan.movements), maxLagMs);
    const report = (startInMs: number): Played<A> => ({
      plan, motion, still: isStill, startInMs,
      endsAt: now() + (motion ? startInMs + eventSpanMs(plan.movements) : 0),
    });
    let startInMs = admittedStartInMs;
    if (!nothing && entry !== null && options.beforePlay) {
      const admittedAt = now();
      await options.beforePlay(report(admittedStartInMs), entry);
      // The place in the queue was kept from the arrival: the time the wait took comes off the start.
      startInMs = Math.max(0, admittedStartInMs - (now() - admittedAt));
    }
    if (!nothing) {
      try {
        options.layer.value?.play(plan.movements, { marksOnly: !motion, startInMs, still: isStill });
        if (motion) {
          for (const followUp of plan.followUps ?? []) {
            options.layer.value?.play(followUp.movements, { startInMs: startInMs + followUp.atMs, still: false });
          }
        }
      } catch {
        /* a picture is never worth an error */
      }
    }
    const played = report(startInMs);
    try {
      options.onPlayed?.(played, entry);
    } catch {
      /* the game's side effect is its own business; the next event still plays */
    }
    return played;
  }

  async function handle(entry: E): Promise<void> {
    try {
      const isStill = stillNow();
      let plan = options.plan?.(entry, { factor: factor.value, still: isStill }) ?? null;
      if (!plan) return;
      if (options.prepare) plan = await options.prepare(entry, plan);
      await admit(plan, entry, isStill);
    } catch {
      /* a picture is never worth an error */
    }
  }

  if (options.log) {
    const log = options.log;
    watch(log, (events, previous) => {
      for (const entry of freshEntries(events, previous?.[0])) void handle(entry);
    });
  }

  return {
    play: (plan, playOptions) => admit(plan, null, stillNow(), playOptions?.maxLagMs),
    backlogMs: () => queue.backlogMs(),
    factor,
    still,
  };
}
