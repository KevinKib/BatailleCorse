import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { effectScope, nextTick, ref } from 'vue';
import type { EndKind } from '../model/president/endSequence';
import { usePresidentEndSequence } from './usePresidentEndSequence';

describe('usePresidentEndSequence', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });

  function setup(initial: EndKind = 'none') {
    const kind = ref<EndKind>(initial);
    const backlog = { ms: 0 };
    const busy = ref(false);
    const timings = ref({ graceMs: 80, settleMs: 1000 });
    const scope = effectScope();
    const sequence = scope.run(() => usePresidentEndSequence({
      kind: () => kind.value, backlogMs: () => backlog.ms, busy: () => busy.value, timings: () => timings.value,
    }))!;
    return { kind, backlog, busy, timings, sequence, scope };
  }

  it('givenNothingEnded_thenNothingShows', () => {
    const { sequence } = setup();
    expect(sequence.sheetVisible.value).toBe(false);
    expect(sequence.finalVisible.value).toBe(false);
  });

  it('givenAPageOpenedOnAnEndedRound_thenTheSheetShowsAtOnce', () => {
    expect(setup('round').sequence.sheetVisible.value).toBe(true);
  });

  it('givenAPageOpenedOnAFinishedMatch_thenTheFinalScoresShowAtOnce', () => {
    const { sequence } = setup('final');
    expect(sequence.finalVisible.value).toBe(true);
    expect(sequence.sheetVisible.value).toBe(false);
  });

  it('givenTheRoundEndsOnAnIdleTable_thenTheSheetWaitsForTheTableToSettle', async () => {
    const { kind, sequence } = setup();
    kind.value = 'round';
    await nextTick();
    vi.advanceTimersByTime(1000);
    expect(sequence.sheetVisible.value).toBe(false);
    vi.advanceTimersByTime(200);
    expect(sequence.sheetVisible.value).toBe(true);
  });

  it('givenTheLastMovesAreStillPlaying_thenTheSettleOnlyStartsOnceTheyAreOver', async () => {
    const { kind, backlog, sequence } = setup();
    kind.value = 'round';
    await nextTick();
    // the last move reaches the queue just after the state: 1500 ms of flights and sweep
    backlog.ms = 1500;
    vi.advanceTimersByTime(80);
    backlog.ms = 1420;
    vi.advanceTimersByTime(1420);
    backlog.ms = 0;
    vi.advanceTimersByTime(900);
    expect(sequence.sheetVisible.value).toBe(false);
    vi.advanceTimersByTime(200);
    expect(sequence.sheetVisible.value).toBe(true);
  });

  it('givenAMoveArrivesDuringTheSettle_thenTheSheetWaitsForItToo', async () => {
    const { kind, backlog, sequence } = setup();
    kind.value = 'round';
    await nextTick();
    vi.advanceTimersByTime(80);
    vi.advanceTimersByTime(600);
    backlog.ms = 700;
    vi.advanceTimersByTime(500);
    expect(sequence.sheetVisible.value).toBe(false);
    backlog.ms = 0;
    vi.advanceTimersByTime(300);
    expect(sequence.sheetVisible.value).toBe(false);
    vi.advanceTimersByTime(1500);
    expect(sequence.sheetVisible.value).toBe(true);
  });

  it('givenAHeldPicture_thenTheSheetWaitsForIt', async () => {
    const { kind, busy, sequence } = setup();
    busy.value = true;
    kind.value = 'round';
    await nextTick();
    vi.advanceTimersByTime(5000);
    expect(sequence.sheetVisible.value).toBe(false);
    busy.value = false;
    vi.advanceTimersByTime(1200);
    expect(sequence.sheetVisible.value).toBe(true);
  });

  it('givenAStillTable_thenTheSheetComesAfterOneTurnOfTheEventLoop', async () => {
    const { kind, timings, sequence } = setup();
    timings.value = { graceMs: 0, settleMs: 0 };
    kind.value = 'round';
    await nextTick();
    expect(sequence.sheetVisible.value).toBe(false);
    vi.advanceTimersByTime(0);
    expect(sequence.sheetVisible.value).toBe(true);
  });

  it('givenTheLastRound_thenTheSheetComesFirstAndTheFinalScoresOnlyWhenContinued', async () => {
    const { kind, sequence } = setup();
    kind.value = 'final';
    await nextTick();
    vi.advanceTimersByTime(1200);
    expect(sequence.sheetVisible.value).toBe(true);
    expect(sequence.finalVisible.value).toBe(false);
    vi.advanceTimersByTime(60_000);
    expect(sequence.finalVisible.value).toBe(false);
    sequence.continueToFinal();
    expect(sequence.finalVisible.value).toBe(true);
  });

  it('givenAMatchEndedWithoutARoundToShow_thenTheFinalScoresComeAfterTheSettle', async () => {
    const { kind, sequence } = setup();
    kind.value = 'abandoned';
    await nextTick();
    vi.advanceTimersByTime(1200);
    expect(sequence.sheetVisible.value).toBe(false);
    expect(sequence.finalVisible.value).toBe(true);
  });

  it('givenTheNextRoundStarts_thenTheSheetGoesAway', async () => {
    const { kind, sequence } = setup('round');
    kind.value = 'none';
    await nextTick();
    expect(sequence.sheetVisible.value).toBe(false);
  });

  it('givenItIsCancelled_thenNothingShowsLater', async () => {
    const { kind, sequence, scope } = setup();
    kind.value = 'round';
    await nextTick();
    scope.stop();
    vi.advanceTimersByTime(5000);
    expect(sequence.sheetVisible.value).toBe(false);
  });
});
