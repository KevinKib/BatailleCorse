import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick, ref } from 'vue';
import { useTableMotion, type MotionPlan, type Played } from './useTableMotion';
import type { AnimationSpeed } from '../core/speed';
import type { TableMovement } from '../core/movement';

type Entry = { id: number };
type Move = TableMovement<string>;
const move = (over: Partial<Move> = {}): Move => ({
  kind: 'travel', from: 'seat', to: 'pile', card: null, flip: false, turnDown: false, actor: 1, byOpponent: false,
  delayMs: 100, durationMs: 400, holdAfterMs: 100, marks: [], ...over,
});
const bounds = { maxQueued: 4, maxLagMs: 3500 };

const entry = (id: number): Entry => ({ id });

/** A table on fakes: a log that a test pushes to (newest first), a layer that records its calls, a clock it moves. */
function table(over: Partial<Parameters<typeof useTableMotion<Entry, string>>[0]> = {}) {
  const log = ref<Entry[]>([]);
  const clock = { now: 10_000 };
  const play = vi.fn();
  const layer = ref<{ play: typeof play } | null>({ play });
  const speed = ref<AnimationSpeed>('normal');
  const motion = useTableMotion<Entry, string>({
    layer, bounds, speed, now: () => clock.now, log: () => log.value,
    plan: () => ({ movements: [move()] }), ...over,
  });
  const push = async (...entries: Entry[]) => {
    log.value = [...entries.reverse(), ...log.value];
    await nextTick();
    await Promise.resolve();
  };
  return { log, clock, play, layer, speed, motion, push };
}

describe('useTableMotion', () => {
  beforeEach(() => {
    window.matchMedia = ((q: string) => ({ matches: false, media: q, addEventListener() {}, removeEventListener() {} })) as typeof window.matchMedia;
  });
  afterEach(() => { vi.restoreAllMocks(); });

  it('givenTheLogAtMount_thenItIsNeverReplayed', async () => {
    const log = ref<Entry[]>([entry(1)]);
    const plan = vi.fn(() => ({ movements: [move()] }));
    const play = vi.fn();
    useTableMotion<Entry, string>({ layer: ref({ play }), bounds, log: () => log.value, plan });
    await nextTick();
    expect(plan).not.toHaveBeenCalled();
    log.value = [entry(2), ...log.value];
    await nextTick();
    expect(plan).toHaveBeenCalledTimes(1);
  });

  it('givenAFreshBurst_thenEachEntryIsPlannedOldestFirstAndPlayedOnTheLayer', async () => {
    const planned: number[] = [];
    const t = table({ plan: e => { planned.push(e.id); return { movements: [move()] }; } });
    await t.push(entry(1), entry(2), entry(3));
    expect(planned).toEqual([1, 2, 3]);
    expect(t.play).toHaveBeenCalledTimes(3);
  });

  it('givenABurst_thenEachEventStartsWhenThePreviousFlightIsOver', async () => {
    const t = table();
    await t.push(entry(1), entry(2), entry(3));
    // The flight of an event lasts delay + duration = 500 ms (the hold after landing excluded).
    expect(t.play.mock.calls.map(c => c[1])).toEqual([
      { marksOnly: false, startInMs: 0, still: false },
      { marksOnly: false, startInMs: 500, still: false },
      { marksOnly: false, startInMs: 1000, still: false },
    ]);
    expect(t.motion.backlogMs()).toBe(1500);
    t.clock.now += 1600;
    expect(t.motion.backlogMs()).toBe(0);
  });

  it('givenAFullBacklog_thenOnlyTheRingsAreShownAtOnce', async () => {
    const t = table({ bounds: { maxQueued: 1, maxLagMs: 3500 } });
    await t.push(entry(1), entry(2), entry(3));
    expect(t.play.mock.calls.map(c => [c[1].marksOnly, c[1].startInMs])).toEqual([[false, 0], [false, 500], [true, 0]]);
  });

  it('givenInstant_thenNothingFliesAndTheQueueIsNotUsed', async () => {
    const t = table();
    t.speed.value = 'instant';
    expect(t.motion.still.value).toBe(true);
    await t.push(entry(1), entry(2));
    expect(t.play.mock.calls.map(c => c[1])).toEqual([
      { marksOnly: true, startInMs: 0, still: true },
      { marksOnly: true, startInMs: 0, still: true },
    ]);
    expect(t.motion.backlogMs()).toBe(0);
  });

  it('givenReducedMotion_thenTheTableIsStill', async () => {
    window.matchMedia = ((q: string) => ({ matches: q.includes('reduce'), media: q })) as typeof window.matchMedia;
    const t = table();
    expect(t.motion.still.value).toBe(true);
    await t.push(entry(1));
    expect(t.play.mock.calls[0][1]).toEqual({ marksOnly: true, startInMs: 0, still: true });
  });

  it('givenTheSpeed_thenThePlanIsAskedForItsFactorAndInstantPlansAtNormalLength', async () => {
    const factors: number[] = [];
    const t = table({ plan: (_e, ctx) => { factors.push(ctx.factor); return { movements: [move()] }; } });
    await t.push(entry(1));
    t.speed.value = 'fast';
    await t.push(entry(2));
    t.speed.value = 'instant';
    await t.push(entry(3));
    expect(factors).toEqual([1, 0.5, 1]);
    expect(t.motion.factor.value).toBe(1);
  });

  it('givenNoSpeed_thenTheTableRunsAtNormalSpeed', async () => {
    const play = vi.fn();
    const log = ref<Entry[]>([]);
    const motion = useTableMotion<Entry, string>({ layer: ref({ play }), bounds, log: () => log.value, plan: () => ({ movements: [move()] }) });
    expect(motion.factor.value).toBe(1);
    expect(motion.still.value).toBe(false);
  });

  it('givenAPlanWithASpan_thenTheQueueIsHeldForThatLongInsteadOfTheFlights', async () => {
    const t = table({ plan: () => ({ movements: [move()], spanMs: 2000 }) });
    await t.push(entry(1), entry(2));
    expect(t.play.mock.calls[1][1].startInMs).toBe(2000);
  });

  it('givenFollowUps_thenTheyPlayAtTheStartOfTheEventPlusTheirOffsetNeverStill', async () => {
    const sweep = [move({ kind: 'travel', from: 'pile', to: 'winner' })];
    const t = table({ plan: () => ({ movements: [move()], followUps: [{ movements: sweep, atMs: 900 }] }) });
    await t.push(entry(1), entry(2));
    expect(t.play.mock.calls.map(c => [c[0] === sweep, c[1].startInMs, c[1].still])).toEqual([
      [false, 0, false], [true, 900, false], [false, 500, false], [true, 1400, false],
    ]);
  });

  it('givenAFullBacklogOrAStillTable_thenNoFollowUpPlays', async () => {
    const sweep = [move({ from: 'pile', to: 'winner' })];
    const t = table({ plan: () => ({ movements: [move()], followUps: [{ movements: sweep, atMs: 900 }] }) });
    t.speed.value = 'instant';
    await t.push(entry(1));
    expect(t.play).toHaveBeenCalledTimes(1);
  });

  it('givenAnEvent_thenOnPlayedGetsTheEndOfItsFlightsAsATimestamp', async () => {
    const played: [Played<string>, Entry | null][] = [];
    const t = table({ onPlayed: (p, e) => { played.push([p, e]); } });
    await t.push(entry(1), entry(2));
    expect(played.map(([p, e]) => [e?.id, p.motion, p.still, p.startInMs, p.endsAt])).toEqual([
      [1, true, false, 0, 10_500],
      [2, true, false, 500, 11_000],
    ]);
  });

  it('givenARingsOnlyEvent_thenItEndsNow', async () => {
    const played: Played<string>[] = [];
    const t = table({ bounds: { maxQueued: 0, maxLagMs: 3500 }, onPlayed: p => { played.push(p); } });
    await t.push(entry(1));
    expect(played[0]).toMatchObject({ motion: false, still: false, startInMs: 0, endsAt: 10_000 });
  });

  it('givenAPlanThatIsNull_thenTheEntryIsSkippedAndNotReported', async () => {
    const onPlayed = vi.fn();
    const t = table({ plan: () => null, onPlayed });
    await t.push(entry(1));
    expect(t.play).not.toHaveBeenCalled();
    expect(onPlayed).not.toHaveBeenCalled();
  });

  it('givenAPlanWithNothingToMove_thenNothingIsPlayedButTheGameIsTold', async () => {
    const onPlayed = vi.fn();
    const t = table({ plan: () => ({ movements: [] }), onPlayed });
    await t.push(entry(1));
    expect(t.play).not.toHaveBeenCalled();
    expect(onPlayed.mock.calls[0][0]).toMatchObject({ motion: false, endsAt: 10_000 });
    expect(t.motion.backlogMs()).toBe(0);
  });

  it('givenAPrepare_thenItIsAwaitedBeforeAdmissionAndMayChangeThePlan', async () => {
    const order: string[] = [];
    const t = table({
      prepare: async (_e, plan) => {
        order.push('prepare');
        await Promise.resolve();
        await Promise.resolve();
        return { ...plan, movements: [move({ durationMs: 900 })] };
      },
      onPlayed: () => { order.push('played'); },
    });
    await t.push(entry(1), entry(2));
    await vi.waitFor(() => expect(t.play).toHaveBeenCalledTimes(2));
    expect(order).toEqual(['prepare', 'prepare', 'played', 'played']);
    expect(t.play.mock.calls[0][0][0].durationMs).toBe(900);
    // 100 + 900 = 1000 ms of flight for the first event.
    expect(t.play.mock.calls[1][1].startInMs).toBe(1000);
  });

  it('givenAPlanThatIsNotFromTheLog_thenPlayShareTheQueueAndReportsWithNoEntry', async () => {
    const played: [Played<string>, Entry | null][] = [];
    const t = table({ onPlayed: (p, e) => { played.push([p, e]); } });
    await t.push(entry(1));
    const result = await t.motion.play({ movements: [move({ durationMs: 200 })] });
    expect(result).toMatchObject({ motion: true, startInMs: 500, endsAt: 10_800 });
    expect(played[1][1]).toBeNull();
    expect(t.play.mock.calls[1][1]).toEqual({ marksOnly: false, startInMs: 500, still: false });
  });

  it('givenALayerThatThrows_thenNothingIsRaisedAndTheNextEventStillPlays', async () => {
    const t = table();
    t.play.mockImplementationOnce(() => { throw new Error('boom'); });
    await t.push(entry(1), entry(2));
    expect(t.play).toHaveBeenCalledTimes(2);
  });

  it('givenNoLayerYet_thenNothingIsRaisedAndTheGameIsStillTold', async () => {
    const onPlayed = vi.fn();
    const t = table({ onPlayed });
    t.layer.value = null;
    await t.push(entry(1));
    expect(onPlayed).toHaveBeenCalledTimes(1);
  });

  it('givenAnEntryWhosePlanThrows_thenTheOthersAreStillPlayed', async () => {
    const t = table({ plan: e => { if (e.id === 1) throw new Error('boom'); return { movements: [move()] }; } });
    await t.push(entry(1), entry(2));
    expect(t.play).toHaveBeenCalledTimes(1);
  });

  it('givenATableWithNoLog_thenOnlyPlayWorks', async () => {
    const play = vi.fn();
    const motion = useTableMotion<Entry, string>({ layer: ref({ play }), bounds, now: () => 5 });
    const plan: MotionPlan<string> = { movements: [move()] };
    await motion.play(plan);
    expect(play).toHaveBeenCalledTimes(1);
  });
});
