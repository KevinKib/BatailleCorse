import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useEndScreen, END_SCREEN_DELAY_MS } from './useEndScreen';

describe('useEndScreen', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('givenGameNotOver_whenRevealAfterAnimation_thenOverlayStaysHidden', () => {
    const { showEndOverlay, revealAfterAnimation } = useEndScreen(() => false);
    revealAfterAnimation();
    vi.advanceTimersByTime(END_SCREEN_DELAY_MS + 100);
    expect(showEndOverlay.value).toBe(false);
  });

  it('givenGameOver_whenRevealAfterAnimation_thenOverlayShowsOnlyAfterDelay', () => {
    const { showEndOverlay, revealAfterAnimation } = useEndScreen(() => true);
    revealAfterAnimation();
    expect(showEndOverlay.value).toBe(false);
    vi.advanceTimersByTime(END_SCREEN_DELAY_MS - 1);
    expect(showEndOverlay.value).toBe(false);
    vi.advanceTimersByTime(1);
    expect(showEndOverlay.value).toBe(true);
  });

  it('givenGameOver_whenRevealImmediatelyIfOver_thenOverlayShowsSynchronously', () => {
    const { showEndOverlay, revealImmediatelyIfOver } = useEndScreen(() => true);
    revealImmediatelyIfOver();
    expect(showEndOverlay.value).toBe(true);
  });

  it('givenGameNotOver_whenRevealImmediatelyIfOver_thenOverlayStaysHidden', () => {
    const { showEndOverlay, revealImmediatelyIfOver } = useEndScreen(() => false);
    revealImmediatelyIfOver();
    expect(showEndOverlay.value).toBe(false);
  });

  it('givenPendingReveal_whenCancel_thenOverlayNeverShowsAndNoTimerLeaks', () => {
    const { showEndOverlay, revealAfterAnimation, cancel } = useEndScreen(() => true);
    revealAfterAnimation();
    cancel();
    expect(vi.getTimerCount()).toBe(0);
    vi.advanceTimersByTime(END_SCREEN_DELAY_MS + 100);
    expect(showEndOverlay.value).toBe(false);
  });
});
