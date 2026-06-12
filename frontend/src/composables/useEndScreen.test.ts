import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { ref, nextTick } from 'vue';
import { useEndScreen, END_SCREEN_DELAY_MS } from './useEndScreen';

describe('useEndScreen', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('givenGameNotOver_thenOverlayStaysHidden', async () => {
    const over = ref(false);
    const animating = ref(false);
    const { showEndOverlay } = useEndScreen(() => over.value, () => animating.value);

    await nextTick();
    vi.advanceTimersByTime(END_SCREEN_DELAY_MS + 100);

    expect(showEndOverlay.value).toBe(false);
  });

  it('givenGameEndsWithNoAnimation_thenOverlayShowsAfterDelay', async () => {
    // A SEND that empties the sender's hand ends the game with no pile animation.
    const over = ref(false);
    const animating = ref(false);
    const { showEndOverlay } = useEndScreen(() => over.value, () => animating.value);

    over.value = true;
    await nextTick();
    expect(showEndOverlay.value).toBe(false);

    vi.advanceTimersByTime(END_SCREEN_DELAY_MS - 1);
    expect(showEndOverlay.value).toBe(false);

    vi.advanceTimersByTime(1);
    expect(showEndOverlay.value).toBe(true);
  });

  it('givenGameEndsMidAnimation_thenWaitsUntilAnimationSettles', async () => {
    // A grab/slap that wins the game flips the winner while the pile is still flying.
    const over = ref(false);
    const animating = ref(true);
    const { showEndOverlay } = useEndScreen(() => over.value, () => animating.value);

    over.value = true;
    await nextTick();
    vi.advanceTimersByTime(END_SCREEN_DELAY_MS + 100);
    expect(showEndOverlay.value).toBe(false); // still animating → not revealed yet

    animating.value = false; // pile-fly finishes
    await nextTick();
    expect(showEndOverlay.value).toBe(false); // delay not elapsed yet

    vi.advanceTimersByTime(END_SCREEN_DELAY_MS);
    expect(showEndOverlay.value).toBe(true);
  });

  it('givenReloadIntoFinishedGame_whenRevealImmediatelyIfOver_thenShowsSynchronously', () => {
    const over = ref(true);
    const animating = ref(false);
    const { showEndOverlay, revealImmediatelyIfOver } = useEndScreen(() => over.value, () => animating.value);

    revealImmediatelyIfOver();

    expect(showEndOverlay.value).toBe(true);
  });

  it('givenNotOver_whenRevealImmediatelyIfOver_thenStaysHidden', () => {
    const over = ref(false);
    const animating = ref(false);
    const { showEndOverlay, revealImmediatelyIfOver } = useEndScreen(() => over.value, () => animating.value);

    revealImmediatelyIfOver();

    expect(showEndOverlay.value).toBe(false);
  });

  it('givenOverlayShown_whenStateBecomesNotOver_thenOverlayHides', async () => {
    const over = ref(true);
    const animating = ref(false);
    const { showEndOverlay } = useEndScreen(() => over.value, () => animating.value);

    over.value = true;
    await nextTick();
    vi.advanceTimersByTime(END_SCREEN_DELAY_MS);
    expect(showEndOverlay.value).toBe(true);

    // A rematch deals a fresh, not-over game.
    over.value = false;
    await nextTick();
    expect(showEndOverlay.value).toBe(false);
  });

  it('givenPendingReveal_whenCancel_thenNeverShowsAndStopsWatching', async () => {
    const over = ref(false);
    const animating = ref(false);
    const { showEndOverlay, cancel } = useEndScreen(() => over.value, () => animating.value);

    over.value = true;
    await nextTick();
    cancel();

    expect(vi.getTimerCount()).toBe(0);
    vi.advanceTimersByTime(END_SCREEN_DELAY_MS + 100);
    expect(showEndOverlay.value).toBe(false);

    // After cancel the watch is stopped, so later state changes do nothing.
    over.value = false;
    animating.value = true;
    await nextTick();
    over.value = true;
    animating.value = false;
    await nextTick();
    vi.advanceTimersByTime(END_SCREEN_DELAY_MS + 100);
    expect(showEndOverlay.value).toBe(false);
  });
});
